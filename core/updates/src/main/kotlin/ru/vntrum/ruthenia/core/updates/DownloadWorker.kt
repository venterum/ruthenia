/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vntrum.ruthenia.core.data.model.toUserMessage
import com.vntrum.ruthenia.core.data.repository.LibraryRepository
import com.vntrum.ruthenia.core.data.settings.SettingsRepository
import com.vntrum.ruthenia.core.updates.R
import com.vntrum.ruthenia.core.installer.ApkDownloader
import com.vntrum.ruthenia.core.installer.ApkSignatureVerifier
import com.vntrum.ruthenia.core.installer.ApkSource
import com.vntrum.ruthenia.core.installer.DownloadProgress
import com.vntrum.ruthenia.core.installer.InstallException
import com.vntrum.ruthenia.core.installer.SignatureCheck
import com.vntrum.ruthenia.core.network.DownloadUrl
import com.vntrum.ruthenia.core.network.RuStoreApi
import com.vntrum.ruthenia.core.network.RuStoreException

/**
 * Background APK download — for an update or a first-time install.
 *
 * The worker only **downloads** files and verifies the signature; the install
 * is always started by the user from the download manager (the system dialog
 * cannot and should not be bypassed). The state lives in WorkManager, so
 * progress is not lost when leaving the screen or restarting the process. A
 * foreground service is deliberately not used: the app does not need extra
 * permissions.
 *
 * The app metadata (name, icon, publisher) comes in the input data, and the
 * missing parts are taken from the library. The worker publishes them in the
 * progress and the result so the download manager shows a human-readable row
 * even after a process restart.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val api: RuStoreApi,
    private val libraryRepository: LibraryRepository,
    private val downloader: ApkDownloader,
    private val signatureVerifier: ApkSignatureVerifier,
    private val settingsRepository: SettingsRepository,
    private val notifications: DownloadNotifications,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val targetVersion = inputData.getLong(KEY_VERSION_CODE, UNKNOWN_VERSION)
        val packageName = inputData.getString(KEY_PACKAGE)
            ?: return Result.failure(
                failureData(
                    applicationContext.getString(R.string.download_error_unknown_package),
                    targetVersion,
                ),
            )
        val app = libraryRepository.get(packageName)
        val meta = Meta(
            appId = inputData.getLong(KEY_APP_ID, UNKNOWN_APP_ID).takeIf { it > 0 } ?: app?.appId,
            name = inputData.stringOrNull(KEY_NAME) ?: app?.name ?: packageName,
            iconUrl = inputData.stringOrNull(KEY_ICON) ?: app?.iconUrl,
            publisher = inputData.stringOrNull(KEY_PUBLISHER) ?: app?.publisher,
        )

        return try {
            // The appId from the request saves a request; if it is absent, we ask the catalog.
            val appId = meta.appId ?: api.overallInfo(packageName).appId
            val links = api.downloadLinks(appId)
            if (links.urls.isEmpty()) {
                return failed(
                    packageName,
                    meta,
                    applicationContext.getString(R.string.download_error_no_files),
                    targetVersion,
                )
            }
            val versionCode = links.versionCode ?: targetVersion

            publish(meta, PHASE_DOWNLOADING, fraction = null)
            notifications.showProgress(packageName, meta.name, fraction = null)

            val files = downloadFiles(
                packageName = packageName,
                meta = meta,
                urls = links.urls,
            )
            if (files.isEmpty()) {
                return failed(
                    packageName,
                    meta,
                    applicationContext.getString(R.string.download_error_no_files),
                    versionCode,
                )
            }

            // Signature verification before install: an update can only be
            // installed over the same build. The user can disable the check in
            // the settings — then the files are accepted as is.
            if (settingsRepository.settings.first().verifySignatures) {
                publish(meta, PHASE_VERIFYING, fraction = null)
                notifications.showProgress(packageName, meta.name, fraction = null)

                if (signatureVerifier.verify(packageName, files) is SignatureCheck.Mismatch) {
                    return failed(
                        packageName,
                        meta,
                        applicationContext.getString(R.string.download_error_signature_mismatch),
                        versionCode,
                    )
                }
            }

            notifications.showReady(packageName, meta.name)
            Result.success(successData(meta, versionCode))
        } catch (cancellation: CancellationException) {
            // The coroutine is already cancelled — cleanup must survive the cancellation.
            withContext(NonCancellable) {
                downloader.clear(packageName)
            }
            notifications.clear(packageName)
            throw cancellation
        } catch (error: Throwable) {
            if (runAttemptCount < MAX_ATTEMPTS && error.isTransient()) {
                Result.retry()
            } else {
                failed(packageName, meta, error.toDownloadMessage(), targetVersion)
            }
        }
    }

    /** Downloader errors with a clear message; the rest go through the general mapping. */
    private fun Throwable.toDownloadMessage(): String = when (this) {
        is InstallException.IntegrityMismatch ->
            applicationContext.getString(R.string.download_error_integrity)

        is InstallException.ContainerHasNoApk ->
            applicationContext.getString(R.string.download_error_container)

        else -> toUserMessage(applicationContext)
    }

    /** Downloading with progress publishing: the channel conflates frequent updates. */
    private suspend fun downloadFiles(
        packageName: String,
        meta: Meta,
        urls: List<DownloadUrl>,
    ): List<File> = coroutineScope {
        val progress = Channel<Float?>(Channel.CONFLATED)
        val publisher = launch {
            for (fraction in progress) {
                publish(meta = meta, phase = PHASE_DOWNLOADING, fraction = fraction)
                notifications.showProgress(packageName, meta.name, fraction)
            }
        }

        try {
            downloader.download(
                packageName = packageName,
                sources = urls.map { ApkSource(url = it.url, size = it.size) },
                onProgress = { report: DownloadProgress -> progress.trySend(report.fraction) },
            )
        } finally {
            progress.close()
            publisher.join()
        }
    }

    private suspend fun publish(meta: Meta, phase: String, fraction: Float?) {
        setProgress(
            workDataOf(
                KEY_PHASE to phase,
                KEY_FRACTION to (fraction ?: UNKNOWN_FRACTION),
                KEY_APP_ID to (meta.appId ?: UNKNOWN_APP_ID),
                KEY_NAME to meta.name,
                KEY_ICON to meta.iconUrl,
                KEY_PUBLISHER to meta.publisher,
            ),
        )
    }

    private suspend fun failed(
        packageName: String,
        meta: Meta,
        message: String,
        versionCode: Long,
    ): Result {
        downloader.clear(packageName)
        notifications.clear(packageName)
        return Result.failure(
            workDataOf(
                KEY_MESSAGE to message,
                KEY_VERSION_CODE to versionCode,
                KEY_APP_ID to (meta.appId ?: UNKNOWN_APP_ID),
                KEY_NAME to meta.name,
                KEY_ICON to meta.iconUrl,
                KEY_PUBLISHER to meta.publisher,
            ),
        )
    }

    private fun successData(meta: Meta, versionCode: Long): Data = workDataOf(
        KEY_VERSION_CODE to versionCode,
        KEY_APP_ID to (meta.appId ?: UNKNOWN_APP_ID),
        KEY_NAME to meta.name,
        KEY_ICON to meta.iconUrl,
        KEY_PUBLISHER to meta.publisher,
    )

    private fun failureData(message: String, versionCode: Long): Data =
        workDataOf(KEY_MESSAGE to message, KEY_VERSION_CODE to versionCode)

    private fun Throwable.isTransient(): Boolean = when (this) {
        is RuStoreException.Network -> true
        is RuStoreException.Http -> code == HTTP_TOO_MANY_REQUESTS || code in SERVER_ERROR_RANGE
        is InstallException.DownloadFailed, is InstallException.SessionFailed -> true
        else -> false
    }

    /** Download metadata sufficient for the manager's list row. */
    data class Meta(
        val appId: Long?,
        val name: String,
        val iconUrl: String?,
        val publisher: String?,
    )

    companion object {
        /** Root tag of all download work: the UI uses it to receive progress. */
        const val TAG = "ruthenia-download"

        /** Tag prefix with the package name of a specific download. */
        const val PKG_TAG_PREFIX = "pkg:"

        const val KEY_PACKAGE = "packageName"
        const val KEY_APP_ID = "appId"
        const val KEY_VERSION_CODE = "versionCode"
        const val KEY_NAME = "name"
        const val KEY_ICON = "iconUrl"
        const val KEY_PUBLISHER = "publisher"
        const val KEY_PHASE = "phase"
        const val KEY_FRACTION = "fraction"
        const val KEY_MESSAGE = "message"

        const val PHASE_DOWNLOADING = "downloading"
        const val PHASE_VERIFYING = "verifying"

        const val UNKNOWN_FRACTION = -1f
        const val UNKNOWN_APP_ID = -1L
        const val UNKNOWN_VERSION = -1L

        /** We retry transient network failures; the partial file is resumed in the process. */
        private const val MAX_ATTEMPTS = 3
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val SERVER_ERROR_RANGE = 500..599

        fun uniqueName(packageName: String): String = "ruthenia-download-$packageName"

        fun packageTag(packageName: String): String = "$PKG_TAG_PREFIX$packageName"
    }
}

/** A string from [Data] only if it is non-blank. */
private fun Data.stringOrNull(key: String): String? =
    getString(key)?.takeIf { it.isNotBlank() }
