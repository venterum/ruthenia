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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vntrum.ruthenia.core.data.icons.AppIconStore
import com.vntrum.ruthenia.core.data.repository.GithubRepository
import com.vntrum.ruthenia.core.data.repository.LibraryRepository
import com.vntrum.ruthenia.core.github.GithubAsset
import com.vntrum.ruthenia.core.github.GithubTokenStore
import com.vntrum.ruthenia.core.github.GithubRelease
import com.vntrum.ruthenia.core.github.GithubRepo
import com.vntrum.ruthenia.core.installer.ApkDownloader
import com.vntrum.ruthenia.core.installer.ApkSignatureVerifier
import com.vntrum.ruthenia.core.installer.ApkSource
import com.vntrum.ruthenia.core.installer.ApkVerification
import com.vntrum.ruthenia.core.installer.DownloadProgress

/**
 * First install from a GitHub release.
 *
 * Unlike RuStore downloads, the Android package name is unknown until the APK
 * is inspected. The worker therefore downloads under a temporary key, reads
 * the APK metadata, verifies it, moves the files to the real package directory
 * and creates a READY ledger row. The normal DownloadManager install path takes
 * over from there.
 */
@HiltWorker
class GithubInstallWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloader: ApkDownloader,
    private val signatureVerifier: ApkSignatureVerifier,
    private val repository: DownloadRepository,
    private val notifications: DownloadNotifications,
    private val githubRepository: GithubRepository,
    private val libraryRepository: LibraryRepository,
    private val githubTokenStore: GithubTokenStore,
    private val appIconStore: AppIconStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val request = inputData.toRequest()
            ?: return Result.failure(workDataOf(KEY_ERROR to "Invalid GitHub install request"))
        val tempKey = tempKey(request)
        var label = request.displayName ?: request.fullName

        return try {
            val files = downloadWithProgress(request, tempKey)
            val metadata = signatureVerifier.metadata(files.first())
                ?: return fail(
                    tempKey = tempKey,
                    name = label,
                    failure = TaskFailure(TaskFailureCode.INVALID_APK),
                    clearFiles = true,
                )
            label = metadata.label ?: label

            when (val verification = signatureVerifier.verifyPackage(
                packageName = metadata.packageName,
                expectedVersionCode = null,
                apkFiles = files,
                verifySignature = true,
            )) {
                ApkVerification.Valid -> Unit
                is ApkVerification.Failed -> return fail(
                    tempKey = tempKey,
                    name = label,
                    failure = verification.toTaskFailure(),
                    clearFiles = verification.code.name in REDOWNLOAD_VERIFICATIONS,
                )
            }

            val installed = installedVersionCode(metadata.packageName)
            if (installed != null && metadata.versionCode < installed) {
                return fail(
                    tempKey = tempKey,
                    name = label,
                    failure = TaskFailure(
                        TaskFailureCode.VERSION_MISMATCH,
                        "${metadata.versionCode} < $installed",
                    ),
                    clearFiles = true,
                )
            }

            val packageName = metadata.packageName
            // The real launcher icon from the APK: a GitHub release only has
            // the owner's avatar, which is not the app's icon.
            val iconUrl = appIconStore.fromApk(files, packageName, metadata.versionCode)
            if (installed != null && metadata.versionCode == installed) {
                // The same build is already installed: link the source and
                // start tracking the app — without a library row it would
                // never be checked for updates.
                downloader.clear(tempKey)
                link(request, packageName)
                libraryRepository.trackInstalled(
                    packageName = packageName,
                    appId = null,
                    name = label,
                    iconUrl = iconUrl,
                    publisher = request.owner,
                )
                notifications.clear(tempKey)
                return Result.success(
                    workDataOf(
                        KEY_PACKAGE to packageName,
                        KEY_NAME to label,
                        KEY_VERSION_CODE to metadata.versionCode,
                    ),
                )
            }

            downloader.rekey(tempKey, packageName)

            repository.adoptReady(
                packageName = packageName,
                appId = null,
                name = label,
                iconUrl = iconUrl,
                publisher = request.owner,
                versionCode = metadata.versionCode,
                versionName = metadata.versionName ?: request.releaseVersionName,
                totalBytes = files.sumOf { it.length() },
                source = DownloadSource.GithubAsset(
                    url = request.assetUrl,
                    size = request.assetSize,
                    sha256 = request.assetSha256,
                ),
            )

            link(request, packageName)

            notifications.clear(tempKey)
            notifications.showReady(packageName, label)
            Result.success(
                workDataOf(
                    KEY_PACKAGE to packageName,
                    KEY_NAME to label,
                    KEY_VERSION_CODE to metadata.versionCode,
                ),
            )
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                downloader.clear(tempKey)
                notifications.clear(tempKey)
            }
            throw cancellation
        } catch (error: Throwable) {
            val failure = error.toTaskFailure()
            if (failure.isRetryable && runAttemptCount < MAX_RETRIES) {
                notifications.clear(tempKey)
                Result.retry()
            } else {
                fail(
                    tempKey = tempKey,
                    name = label,
                    failure = failure,
                    clearFiles = failure.needsRedownload,
                )
            }
        }
    }

    private suspend fun downloadWithProgress(
        request: GithubInstallRequest,
        tempKey: String,
    ) = coroutineScope {
        val progress = Channel<DownloadProgress>(Channel.CONFLATED)
        var lastPercent = -1
        val publisher = launch {
            for (report in progress) {
                setProgress(
                    workDataOf(
                        KEY_FRACTION to (report.fraction ?: DownloadWorker.UNKNOWN_FRACTION),
                    ),
                )
                val percent = report.fraction?.let { (it * 100).toInt() } ?: -1
                if (percent != lastPercent) {
                    lastPercent = percent
                    notifications.showProgress(
                        packageName = tempKey,
                        appName = request.displayName ?: request.fullName,
                        fraction = report.fraction,
                    )
                }
            }
        }

        try {
            downloader.download(
                packageName = tempKey,
                sources = listOf(
                    ApkSource(
                        url = request.assetUrl,
                        size = request.assetSize,
                        sha256 = request.assetSha256,
                        headers = githubTokenStore.token()
                            ?.let { mapOf("Authorization" to "Bearer $it") }
                            .orEmpty(),
                    ),
                ),
                onProgress = { report -> progress.trySend(report) },
            )
        } finally {
            progress.close()
            publisher.join()
        }
    }

    private suspend fun link(request: GithubInstallRequest, packageName: String) {
        githubRepository.linkInstalled(
            packageName = packageName,
            repo = GithubRepo(
                id = request.repoId ?: 0L,
                owner = request.owner,
                name = request.repo,
                description = null,
                htmlUrl = "https://github.com/${request.fullName}",
                avatarUrl = request.avatarUrl,
                archived = false,
            ),
            release = GithubRelease(
                id = request.releaseId,
                tagName = request.releaseTag,
                name = null,
                body = null,
                prerelease = false,
                publishedAt = null,
                assets = emptyList(),
            ),
            asset = GithubAsset(
                id = 0L,
                name = request.assetName,
                size = request.assetSize ?: 0L,
                contentType = null,
                downloadUrl = request.assetUrl,
                digest = request.assetSha256?.let { "sha256:$it" },
            ),
            includePrereleases = request.includePrereleases,
        )
    }

    private suspend fun fail(
        tempKey: String,
        name: String,
        failure: TaskFailure,
        clearFiles: Boolean,
    ): Result {
        if (clearFiles) downloader.clear(tempKey)
        notifications.clear(tempKey)
        notifications.showGithubFailed(tempKey, name, failure)
        return Result.failure(
            workDataOf(
                KEY_ERROR_CODE to failure.code.name,
                KEY_ERROR to failure.text(applicationContext).message,
            ),
        )
    }

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

    private fun tempKey(request: GithubInstallRequest): String =
        "github-" + "${request.owner}-${request.repo}".replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun Data.toRequest(): GithubInstallRequest? {
        val owner = getString(KEY_OWNER) ?: return null
        val repo = getString(KEY_REPO) ?: return null
        val tag = getString(KEY_RELEASE_TAG) ?: return null
        val versionName = getString(KEY_RELEASE_VERSION) ?: tag
        val assetName = getString(KEY_ASSET_NAME) ?: return null
        val assetUrl = getString(KEY_ASSET_URL) ?: return null
        return GithubInstallRequest(
            owner = owner,
            repo = repo,
            repoId = getLong(KEY_REPO_ID, -1L).takeIf { it >= 0 },
            displayName = getString(KEY_DISPLAY_NAME),
            avatarUrl = getString(KEY_AVATAR),
            releaseId = getLong(KEY_RELEASE_ID, -1L),
            releaseTag = tag,
            releaseVersionName = versionName,
            assetName = assetName,
            assetUrl = assetUrl,
            assetSize = getLong(KEY_ASSET_SIZE, -1L).takeIf { it >= 0 },
            assetSha256 = getString(KEY_ASSET_SHA256),
            includePrereleases = getBoolean(KEY_INCLUDE_PRERELEASES, false),
        )
    }

    companion object {
        const val TAG = "ruthenia-github-install"
        const val KEY_OWNER = "owner"
        const val KEY_REPO = "repo"
        const val KEY_REPO_ID = "repoId"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_AVATAR = "avatarUrl"
        const val KEY_RELEASE_ID = "releaseId"
        const val KEY_RELEASE_TAG = "releaseTag"
        const val KEY_RELEASE_VERSION = "releaseVersion"
        const val KEY_ASSET_NAME = "assetName"
        const val KEY_ASSET_URL = "assetUrl"
        const val KEY_ASSET_SIZE = "assetSize"
        const val KEY_ASSET_SHA256 = "assetSha256"
        const val KEY_INCLUDE_PRERELEASES = "includePrereleases"
        const val KEY_FRACTION = DownloadWorker.KEY_FRACTION
        const val KEY_PACKAGE = "packageName"
        const val KEY_NAME = "name"
        const val KEY_VERSION_CODE = "versionCode"
        const val KEY_ERROR = "error"
        const val KEY_ERROR_CODE = "errorCode"

        fun uniqueName(repoFullName: String): String = "github-install-$repoFullName"

        fun repoTag(repoFullName: String): String = "github:$repoFullName"

        private const val MAX_RETRIES = 3

        private val REDOWNLOAD_VERIFICATIONS = setOf(
            "INVALID_APK",
            "PACKAGE_MISMATCH",
            "VERSION_MISMATCH",
            "INCOMPATIBLE",
            "SIGNATURE_MISMATCH",
        )
    }
}
