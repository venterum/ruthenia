/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Installing APKs via the session-based `PackageInstaller`.
 *
 * All files (base APK and config splits) are written into a **single** session, so a
 * split app is installed entirely, without manual additional steps.
 * The system confirmation dialog is not bypassed: if Android requires it,
 * [InstallResult.UserActionRequired] with an intent is returned to the caller.
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun install(
        packageName: String,
        apks: List<File>,
        onUserActionRequired: (Intent) -> Unit = {},
    ): InstallResult {
        require(apks.isNotEmpty()) { "No APK files to install" }

        val packageInstaller = context.packageManager.packageInstaller
        val sessionParams = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(packageName)
        }

        val sessionId = try {
            packageInstaller.createSession(sessionParams)
        } catch (e: Exception) {
            return InstallResult.Failure(e.message ?: "Failed to create install session")
        }

        val pendingIntent = PendingIntentFactory.installResult(context, sessionId)
        val results = CompletableDeferred<InstallResult>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val status = intent.getIntExtra(
                    PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE,
                )
                when (status) {
                    // The system asks for confirmation: we pass the intent up, but
                    // do NOT unregister the receiver — after the user responds,
                    // another broadcast with the final status will arrive.
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirmation = intent.confirmationIntent()
                        if (confirmation != null) {
                            onUserActionRequired(confirmation)
                        } else {
                            results.complete(
                                InstallResult.Failure("System requested confirmation but sent no intent"),
                            )
                        }
                    }

                    PackageInstaller.STATUS_SUCCESS -> results.complete(InstallResult.Success)

                    else -> results.complete(
                        InstallResult.Failure(
                            intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                ?: "Installer status $status",
                        ),
                    )
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_INSTALL_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        try {
            val session = packageInstaller.openSession(sessionId)
            try {
                // Writing the APK into the session is a blocking operation of hundreds of megabytes,
                // it must not be done on the main thread.
                withContext(Dispatchers.IO) {
                    apks.forEach { file ->
                        session.openWrite(file.name, 0, file.length()).use { output ->
                            file.inputStream().use { input -> input.copyTo(output) }
                            session.fsync(output)
                        }
                    }
                }
                session.commit(pendingIntent.intentSender)
            } catch (e: Exception) {
                session.abandon()
                return InstallResult.Failure(e.message ?: "Install session failed")
            } finally {
                session.close()
            }

            return withTimeoutOrNull(INSTALL_TIMEOUT_MS) { results.await() }
                ?: InstallResult.Failure("Install result timed out")
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.confirmationIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_INTENT)
        }

    companion object {
        const val ACTION_INSTALL_RESULT =
            "com.vntrum.ruthenia.core.installer.INSTALL_RESULT"

        private const val INSTALL_TIMEOUT_MS = 10 * 60 * 1000L
    }
}

private object PendingIntentFactory {

    fun installResult(context: Context, sessionId: Int): android.app.PendingIntent =
        android.app.PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(ApkInstaller.ACTION_INSTALL_RESULT).setPackage(context.packageName),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_MUTABLE,
        )
}
