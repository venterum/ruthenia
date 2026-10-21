/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
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
import com.vntrum.ruthenia.core.data.db.DownloadTaskEntity
import com.vntrum.ruthenia.core.data.repository.LibraryRepository
import com.vntrum.ruthenia.core.data.settings.SettingsRepository
import com.vntrum.ruthenia.core.github.GithubTokenStore
import com.vntrum.ruthenia.core.installer.ApkDownloader
import com.vntrum.ruthenia.core.installer.ApkSignatureVerifier
import com.vntrum.ruthenia.core.installer.ApkSource
import com.vntrum.ruthenia.core.installer.ApkVerification
import com.vntrum.ruthenia.core.installer.DownloadProgress
import com.vntrum.ruthenia.core.network.DownloadLinks
import com.vntrum.ruthenia.core.network.DownloadUrl
import com.vntrum.ruthenia.core.network.RuStoreApi

/**
 * Background APK download — for an update or a first-time install.
 *
 * The worker only **downloads** files and verifies them; the install is always
 * started by the user from the download manager (the system dialog cannot and
 * should not be bypassed). Durable state lives in [DownloadRepository]; live
 * progress is published to WorkManager for the UI and notifications.
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
    private val repository: DownloadRepository,
    private val notifications: DownloadNotifications,
    private val githubTokenStore: GithubTokenStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val targetVersion = inputData.getLong(KEY_VERSION_CODE, UNKNOWN_VERSION)
        val sourceType = inputData.getString(KEY_SOURCE_TYPE) ?: SOURCE_RUSTORE
        val directUrl = inputData.getString(KEY_SOURCE_URL)
            ?.takeIf { sourceType == SOURCE_GITHUB && it.isNotBlank() }
        val directSize = inputData.getLong(KEY_SOURCE_SIZE, UNKNOWN_SIZE)
        val directSha256 = inputData.getString(KEY_SOURCE_SHA256)
        val packageName = inputData.getString(KEY_PACKAGE)
            ?: return Result.failure(
                failureData(
                    TaskFailure(TaskFailureCode.UNKNOWN, "missing package name"),
                    UNKNOWN_VERSION,
                ),
            )

        val record = repository.get(packageName)
        // A replacement worker may have already taken over this package. The
        // old worker must not touch the new ledger row.
        if (record?.workId != null && record.workId != id.toString()) {
            return Result.failure()
        }
        val installed = libraryRepository.get(packageName)
        val meta = Meta(
            appId = inputData.getLong(KEY_APP_ID, UNKNOWN_APP_ID).takeIf { it > 0 }
                ?: record?.appId
                ?: installed?.appId,
            name = inputData.stringOrNull(KEY_NAME) ?: record?.name ?: installed?.name ?: packageName,
            iconUrl = inputData.stringOrNull(KEY_ICON) ?: record?.iconUrl ?: installed?.iconUrl,
            publisher = inputData.stringOrNull(KEY_PUBLISHER)
                ?: record?.publisher
                ?: installed?.publisher,
            versionName = inputData.stringOrNull(KEY_VERSION_NAME)
                ?: record?.versionName
                ?: installed?.latestVersionName,
        )

        // A legacy WorkManager task may have no ledger row (for example, after
        // an app update). Seed one so the manager has a durable task.
        if (record == null) {
            repository.upsert(
                DownloadTaskEntity(
                    packageName = packageName,
                    appId = meta.appId,
                    name = meta.name,
                    iconUrl = meta.iconUrl,
                    publisher = meta.publisher,
                    versionCode = targetVersion.takeIf { it >= 0 },
                    versionName = meta.versionName,
                    totalBytes = null,
                    stage = DownloadStage.QUEUED,
                    workId = id.toString(),
                    failureCode = null,
                    failureDetail = null,
                    installStage = InstallStage.IDLE,
                    installFailureCode = null,
                    installFailureDetail = null,
                    attemptCount = runAttemptCount,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    completedAt = null,
                ),
            )
        }

        return try {
            if (runAttemptCount > 0) {
                repository.markQueued(packageName, runAttemptCount)
            }
            repository.markDownloading(packageName)

            // The appId from the request saves a request; if it is absent, we ask the catalog.
            // A direct GitHub asset already has its URL and does not touch RuStore.
            val links = if (directUrl != null) {
                DownloadLinks(
                    appId = meta.appId,
                    versionCode = targetVersion.takeIf { it >= 0 },
                    urls = listOf(
                        DownloadUrl(
                            url = directUrl,
                            size = directSize.takeIf { it >= 0 },
                            hash = directSha256,
                        ),
                    ),
                )
            } else {
                val appId = meta.appId ?: api.overallInfo(packageName).appId
                api.downloadLinks(appId)
            }
            if (links.urls.isEmpty()) {
                return failed(
                    packageName = packageName,
                    meta = meta,
                    failure = TaskFailure(TaskFailureCode.NO_FILES),
                    versionCode = targetVersion,
                )
            }
            val versionCode = links.versionCode ?: targetVersion

            // Another install may have completed while we were queued.
            val installedNow = installedVersionCode(packageName)
            if (versionCode >= 0 && installedNow != null && installedNow >= versionCode) {
                downloader.clear(packageName)
                repository.markCompleted(packageName, installedNow, System.currentTimeMillis())
                notifications.clear(packageName)
                return Result.success(successData(meta, installedNow))
            }

            publish(
                meta = meta,
                phase = PHASE_DOWNLOADING,
                progress = DownloadProgress(0L, links.urls.sumOf { it.size ?: 0L }),
            )
            notifications.showProgress(packageName, meta.name, fraction = null)

            val files = downloadFiles(
                packageName = packageName,
                meta = meta,
                urls = links.urls,
                headers = if (directUrl != null) githubHeaders() else emptyMap(),
                verifyDigest = directUrl != null,
            )
            if (files.isEmpty()) {
                return failed(
                    packageName = packageName,
                    meta = meta,
                    failure = TaskFailure(TaskFailureCode.NO_FILES),
                    versionCode = versionCode,
                )
            }

            // Verification before install: structure, package name, version,
            // minSdk and — when enabled — the signature of the installed app.
            val verifySignatures = settingsRepository.settings.first().verifySignatures
            if (verifySignatures) {
                publish(
                    meta = meta,
                    phase = PHASE_VERIFYING,
                    progress = DownloadProgress(0L, 0L),
                )
                notifications.showProgress(packageName, meta.name, fraction = null)
            }
            when (val verification = signatureVerifier.verifyPackage(
                packageName = packageName,
                expectedVersionCode = versionCode.takeIf { it >= 0 },
                apkFiles = files,
                verifySignature = verifySignatures,
            )) {
                ApkVerification.Valid -> Unit
                is ApkVerification.Failed -> {
                    return failed(
                        packageName = packageName,
                        meta = meta,
                        failure = verification.toTaskFailure(),
                        versionCode = versionCode,
                    )
                }
            }

            val totalBytes = files.sumOf { it.length() }
            val readyVersionCode = if (directUrl != null) {
                signatureVerifier.downloadedVersionCode(files.first()) ?: versionCode
            } else {
                versionCode
            }
            if (directUrl != null && readyVersionCode != null) {
                val installedNow = installedVersionCode(packageName)
                if (installedNow != null && readyVersionCode < installedNow) {
                    return failed(
                        packageName = packageName,
                        meta = meta,
                        failure = TaskFailure(
                            TaskFailureCode.VERSION_MISMATCH,
                            "$readyVersionCode < $installedNow",
                        ),
                        versionCode = readyVersionCode,
                    )
                }
            }
            repository.markReady(packageName, readyVersionCode, totalBytes)
            notifications.showReady(packageName, meta.name)
            Result.success(successData(meta, readyVersionCode))
        } catch (cancellation: CancellationException) {
            // The coroutine is already cancelled — cleanup must survive the cancellation.
            withContext(NonCancellable) {
                handleCancellation(packageName)
            }
            throw cancellation
        } catch (error: Throwable) {
            val failure = error.toTaskFailure()
            if (failure.isRetryable && runAttemptCount < MAX_ATTEMPTS) {
                repository.markQueued(packageName, runAttemptCount + 1)
                notifications.clear(packageName)
                Result.retry()
            } else {
                failed(packageName, meta, failure, targetVersion)
            }
        }
    }

    private suspend fun handleCancellation(packageName: String) {
        val record = repository.get(packageName)
        if (record?.workId != null && record.workId != id.toString()) return
        when (record?.stage) {
            DownloadStage.REMOVING -> {
                downloader.clear(packageName)
                notifications.clear(packageName)
            }

            DownloadStage.PAUSED -> {
                notifications.clear(packageName)
            }

            else -> {
                // A work replacement or system cancellation must not throw
                // away a resumable partial file. Only an explicit Remove does.
                notifications.clear(packageName)
                repository.markInterrupted(packageName)
            }
        }
    }

    /** Downloading with progress publishing: the channel conflates frequent updates. */
    private suspend fun downloadFiles(
        packageName: String,
        meta: Meta,
        urls: List<DownloadUrl>,
        headers: Map<String, String>,
        verifyDigest: Boolean,
    ): List<File> = coroutineScope {
        val progress = Channel<DownloadProgress>(Channel.CONFLATED)
        val publisher = launch {
            for (report in progress) {
                publish(meta = meta, phase = PHASE_DOWNLOADING, progress = report)
                notifications.showProgress(packageName, meta.name, report.fraction)
            }
        }

        try {
            downloader.download(
                packageName = packageName,
                sources = urls.map {
                    ApkSource(
                        url = it.url,
                        size = it.size,
                        sha256 = if (verifyDigest) it.hash else null,
                        headers = headers,
                    )
                },
                onProgress = { report: DownloadProgress -> progress.trySend(report) },
            )
        } finally {
            progress.close()
            publisher.join()
        }
    }

    private fun githubHeaders(): Map<String, String> =
        githubTokenStore.token()?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()

    private suspend fun publish(meta: Meta, phase: String, progress: DownloadProgress) {
        setProgress(
            workDataOf(
                KEY_PHASE to phase,
                KEY_FRACTION to (progress.fraction ?: UNKNOWN_FRACTION),
                KEY_DOWNLOADED_BYTES to progress.downloadedBytes,
                KEY_TOTAL_BYTES to progress.totalBytes,
                KEY_APP_ID to (meta.appId ?: UNKNOWN_APP_ID),
                KEY_NAME to meta.name,
                KEY_ICON to meta.iconUrl,
                KEY_PUBLISHER to meta.publisher,
                KEY_VERSION_NAME to meta.versionName,
            ),
        )
    }

    private suspend fun failed(
        packageName: String,
        meta: Meta,
        failure: TaskFailure,
        versionCode: Long,
    ): Result {
        // The user may have paused or removed the task while the error was
        // being handled; do not resurrect a paused row with a failure notice.
        val current = repository.get(packageName)
        if (current?.workId != null && current.workId != id.toString()) {
            return Result.failure(failureData(failure, versionCode))
        }
        if (current?.stage == DownloadStage.PAUSED || current?.stage == DownloadStage.REMOVING) {
            notifications.clear(packageName)
            return Result.failure(failureData(failure, versionCode))
        }

        // Corrupt/invalid files are useless for a retry. Network/storage
        // failures keep the partial file so "Retry" can resume it.
        if (failure.shouldClearFiles) {
            downloader.clear(packageName)
        }
        repository.markFailed(packageName, failure, runAttemptCount)
        notifications.showFailed(packageName, meta.name, failure)
        return Result.failure(failureData(failure, versionCode))
    }

    private fun failureData(failure: TaskFailure, versionCode: Long): Data = workDataOf(
        KEY_FAILURE_CODE to failure.code.name,
        KEY_FAILURE_DETAIL to failure.detail,
        KEY_MESSAGE to failure.detail.orEmpty(),
        KEY_VERSION_CODE to versionCode,
    )

    private fun successData(meta: Meta, versionCode: Long): Data = workDataOf(
        KEY_VERSION_CODE to versionCode,
        KEY_APP_ID to (meta.appId ?: UNKNOWN_APP_ID),
        KEY_NAME to meta.name,
        KEY_ICON to meta.iconUrl,
        KEY_PUBLISHER to meta.publisher,
        KEY_VERSION_NAME to meta.versionName,
    )

    @Suppress("DEPRECATION")
    private fun installedVersionCode(packageName: String): Long? = try {
        PackageInfoCompat.getLongVersionCode(
            applicationContext.packageManager.getPackageInfo(packageName, 0),
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: Exception) {
        null
    }

    /** Download metadata sufficient for the manager's list row. */
    data class Meta(
        val appId: Long?,
        val name: String,
        val iconUrl: String?,
        val publisher: String?,
        val versionName: String?,
    )

    companion object {
        /** Root tag of all download work: the UI uses it to receive progress. */
        const val TAG = "ruthenia-download"

        /** Tag prefix with the package name of a specific download. */
        const val PKG_TAG_PREFIX = "pkg:"

        const val KEY_PACKAGE = "packageName"
        const val KEY_APP_ID = "appId"
        const val KEY_VERSION_CODE = "versionCode"
        const val KEY_VERSION_NAME = "versionName"
        const val KEY_SOURCE_TYPE = "sourceType"
        const val KEY_SOURCE_URL = "sourceUrl"
        const val KEY_SOURCE_SIZE = "sourceSize"
        const val KEY_SOURCE_SHA256 = "sourceSha256"
        const val KEY_NAME = "name"
        const val KEY_ICON = "iconUrl"
        const val KEY_PUBLISHER = "publisher"
        const val KEY_PHASE = "phase"
        const val KEY_FRACTION = "fraction"
        const val KEY_DOWNLOADED_BYTES = "downloadedBytes"
        const val KEY_TOTAL_BYTES = "totalBytes"
        const val KEY_MESSAGE = "message"
        const val KEY_FAILURE_CODE = "failureCode"
        const val KEY_FAILURE_DETAIL = "failureDetail"

        const val PHASE_DOWNLOADING = "downloading"
        const val PHASE_VERIFYING = "verifying"

        const val UNKNOWN_FRACTION = -1f
        const val UNKNOWN_APP_ID = -1L
        const val UNKNOWN_VERSION = -1L
        const val UNKNOWN_SIZE = -1L
        const val SOURCE_RUSTORE = "RUSTORE"
        const val SOURCE_GITHUB = "GITHUB"

        /** We retry transient failures; the partial file is resumed in the process. */
        private const val MAX_ATTEMPTS = 4

        fun uniqueName(packageName: String): String = "ruthenia-download-$packageName"

        fun packageTag(packageName: String): String = "$PKG_TAG_PREFIX$packageName"
    }
}

private val TaskFailure.shouldClearFiles: Boolean
    get() = code in setOf(
        TaskFailureCode.APP_UNAVAILABLE,
        TaskFailureCode.NO_FILES,
        TaskFailureCode.INVALID_RESPONSE,
        TaskFailureCode.INVALID_APK,
        TaskFailureCode.CORRUPTED,
        TaskFailureCode.SIZE_MISMATCH,
        TaskFailureCode.SIGNATURE_MISMATCH,
        TaskFailureCode.PACKAGE_MISMATCH,
        TaskFailureCode.VERSION_MISMATCH,
        TaskFailureCode.INCOMPATIBLE,
        TaskFailureCode.INSTALL_INVALID,
    )

/** A string from [Data] only if it is non-blank. */
private fun Data.stringOrNull(key: String): String? =
    getString(key)?.takeIf { it.isNotBlank() }
