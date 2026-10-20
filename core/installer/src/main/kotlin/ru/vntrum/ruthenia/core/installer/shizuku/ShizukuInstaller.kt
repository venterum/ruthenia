/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import rikka.sui.Sui
import com.vntrum.ruthenia.core.installer.InstallFailure
import com.vntrum.ruthenia.core.installer.InstallFailureCode
import com.vntrum.ruthenia.core.installer.InstallFailureMapper
import com.vntrum.ruthenia.core.installer.InstallResult
import com.vntrum.ruthenia.core.installer.R

/** Availability of silent install via Shizuku or Sui. */
enum class ShizukuStatus {
    /** Neither Shizuku nor Sui is installed. */
    NOT_INSTALLED,

    /** Installed, but the service is not running (or the version is unsupported). */
    NOT_RUNNING,

    /** The service is running, but Ruthenia Store has no permission yet. */
    PERMISSION_REQUIRED,

    /** Running and permitted: silent install is available. */
    READY,
}

/**
 * Optional silent install through Shizuku or Sui.
 *
 * The actual installation runs in [ShizukuInstallService] with the shell
 * identity; this class only tracks availability, requests the permission, and
 * streams the downloaded files as binder descriptors. It is deliberately
 * separate from [com.vntrum.ruthenia.core.installer.ApkInstaller]: the caller
 * decides which backend to use and can fall back to the system one.
 *
 * The service connection stays bound for the process lifetime: reconnecting on
 * every install would start the service process again and again, while a bound
 * service is what Shizuku expects.
 */
@Singleton
class ShizukuInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // Sui is a Magisk module without a launcher package, so it is detected via
    // its own init (the Shizuku provider does the same automatically). It must
    // be declared before _status: the initial computeStatus() reads it while
    // the constructor is still running, so the lazy delegate has to exist by
    // then (otherwise a missing Shizuku package crashes the app on launch).
    private val suiAvailable: Boolean by lazy {
        runCatching { Sui.init(context.packageName) }.getOrDefault(false)
    }

    private val _status = MutableStateFlow(computeStatus())

    /** Current availability; updates with the Shizuku binder and permission. */
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val connected = MutableStateFlow(false)
    private var service: IInstallService? = null
    private val connectMutex = Mutex()
    private val installMutex = Mutex()

    private val args = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShizukuInstallService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("install")
        .version(SERVICE_VERSION)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IInstallService.Stub.asInterface(binder)
            connected.value = true
            refreshStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            connected.value = false
            refreshStatus()
        }
    }

    init {
        // Sticky: fires immediately if the Shizuku service is already running.
        Shizuku.addBinderReceivedListenerSticky { refreshStatus() }
        Shizuku.addBinderDeadListener {
            service = null
            connected.value = false
            refreshStatus()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refreshStatus() }
    }

    /** Asks Shizuku for the API permission; the result arrives in the listener. */
    fun requestPermission() {
        if (computeStatus() != ShizukuStatus.PERMISSION_REQUIRED) return
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /** True when the user denied the permission and checked "don't ask again". */
    fun permissionDeniedPermanently(): Boolean =
        runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)

    /**
     * Installs the APKs with the shell identity. All files are sent in a single
     * `pm` session, so split apps are installed in one piece.
     *
     * A failure here is not fatal for the caller: it can retry with the system
     * [com.vntrum.ruthenia.core.installer.ApkInstaller].
     */
    suspend fun install(packageName: String, apks: List<File>): InstallResult {
        require(apks.isNotEmpty()) { "No APK files to install" }
        if (computeStatus() != ShizukuStatus.READY) {
            return InstallResult.Failure(
                InstallFailure(
                    InstallFailureCode.SHIZUKU_UNAVAILABLE,
                    context.getString(R.string.installer_shizuku_not_ready),
                ),
            )
        }
        installMutex.withLock {
            val bound = connect()
                ?: return InstallResult.Failure(
                    InstallFailure(
                        InstallFailureCode.SHIZUKU_UNAVAILABLE,
                        context.getString(R.string.installer_shizuku_not_connected),
                    ),
                )
            val descriptors = try {
                apks.map {
                    ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            } catch (e: Exception) {
                return InstallResult.Failure(
                    InstallFailure(
                        InstallFailureCode.SESSION_FAILED,
                        e.message?.take(MAX_DETAIL_LENGTH),
                    ),
                )
            }
            return try {
                val error = withContext(Dispatchers.IO) {
                    bound.install(descriptors.toTypedArray(), packageName)
                }
                if (error.isNullOrEmpty()) {
                    InstallResult.Success
                } else {
                    InstallResult.Failure(
                        InstallFailure(
                            code = InstallFailureMapper.fromMessage(error),
                            detail = error.take(MAX_DETAIL_LENGTH),
                        ),
                    )
                }
            } catch (e: Exception) {
                InstallResult.Failure(
                    InstallFailure(
                        InstallFailureCode.SESSION_FAILED,
                        e.message?.take(MAX_DETAIL_LENGTH),
                    ),
                )
            } finally {
                descriptors.forEach { runCatching { it.close() } }
            }
        }
    }

    private suspend fun connect(): IInstallService? {
        service?.let { return it }
        return connectMutex.withLock {
            service?.let { return@withLock it }
            try {
                Shizuku.bindUserService(args, connection)
            } catch (e: Exception) {
                return@withLock null
            }
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                connected.filter { it }.first()
                service
            }
        }
    }

    private fun refreshStatus() {
        _status.value = computeStatus()
    }

    private fun computeStatus(): ShizukuStatus = when {
        !Shizuku.pingBinder() ->
            if (isInstalled()) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED

        Shizuku.getVersion() < MIN_SHIZUKU_VERSION -> ShizukuStatus.NOT_RUNNING

        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
            ShizukuStatus.PERMISSION_REQUIRED

        else -> ShizukuStatus.READY
    }

    private fun isInstalled(): Boolean {
        val managerInstalled = runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        }.isSuccess
        return managerInstalled || suiAvailable
    }

    companion object {
        /** Package name of the Shizuku manager app. */
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

        private const val SERVICE_VERSION = 1
        private const val REQUEST_CODE = 9001
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val MIN_SHIZUKU_VERSION = 11
        private const val MAX_DETAIL_LENGTH = 300
    }
}
