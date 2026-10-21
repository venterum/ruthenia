/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.vntrum.ruthenia.core.data.db.DownloadTaskEntity
import com.vntrum.ruthenia.core.data.icons.AppIconStore
import com.vntrum.ruthenia.core.data.repository.LibraryRepository
import com.vntrum.ruthenia.core.data.settings.InstallMethod
import com.vntrum.ruthenia.core.data.settings.SettingsRepository
import com.vntrum.ruthenia.core.installer.ApkDownloader
import com.vntrum.ruthenia.core.installer.ApkInstaller
import com.vntrum.ruthenia.core.installer.InstallFailure
import com.vntrum.ruthenia.core.installer.InstallFailureCode
import com.vntrum.ruthenia.core.installer.InstallResult
import com.vntrum.ruthenia.core.installer.shizuku.ShizukuInstaller
import com.vntrum.ruthenia.core.installer.shizuku.ShizukuStatus

/**
 * Download manager — a single entry point for downloading and installing.
 *
 * Both library updates and first-time installs from the app details screen go
 * through it. Download state is durable in [DownloadRepository]; the install
 * state and failures are written back to the same ledger so the manager can
 * explain a failed install long after the snackbar is gone.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val shizukuInstaller: ShizukuInstaller,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val notifications: DownloadNotifications,
    private val appIconStore: AppIconStore,
) {

    /**
     * All downloads for the manager and app details screens.
     *
     * Ready-to-install entries whose version is already on the device are
     * hidden; completed history stays visible.
     */
    val downloads: Flow<List<DownloadTask>> =
        repository.downloads.combine(libraryRepository.observeLibrary()) { tasks, apps ->
            val installed = apps.associate { it.packageName to it.installedVersionCode }
            tasks.filterNot { task -> task.isAlreadyInstalled(installed[task.packageName]) }
        }

    private val _userActions = Channel<Intent>(Channel.BUFFERED)

    /** System installer dialogs; launched centrally by MainActivity. */
    val userActions: Flow<Intent> = _userActions.receiveAsFlow()

    /** Queues a download (an update or a first-time install). */
    suspend fun request(request: DownloadRequest) = repository.enqueue(request)

    /** Stops the active download but keeps partial files for resume. */
    suspend fun pause(packageName: String) {
        notifications.clear(packageName)
        repository.pause(packageName)
    }

    suspend fun resume(packageName: String) {
        notifications.clear(packageName)
        repository.resume(packageName)
    }

    /** Cancels work, deletes files and removes the manager row. */
    suspend fun remove(packageName: String) {
        repository.remove(packageName)
        notifications.clear(packageName)
    }

    /** Removes a completed/failed entry from the manager. */
    suspend fun forget(packageName: String) = remove(packageName)

    /** Retries a failed download; corrupt files are downloaded from scratch. */
    suspend fun retry(task: DownloadTask) {
        notifications.clear(task.packageName)
        val mustRedownload = task.failure?.needsRedownload == true
        if (mustRedownload) downloader.clear(task.packageName)
        repository.enqueue(task.toRequest())
    }

    /** Clears cached files and starts a fresh download. */
    suspend fun redownload(task: DownloadTask) {
        notifications.clear(task.packageName)
        downloader.clear(task.packageName)
        repository.enqueue(task.toRequest())
    }

    /** Retries all download failures that have a retry/redownload action. */
    suspend fun retryAllFailed(): Int {
        val tasks = repository.downloads.first()
            .filter { it.download is DownloadState.Failed }
            .filter { it.failure?.let { failure -> failure.isRetryable || failure.needsRedownload } == true }
        tasks.forEach { retry(it) }
        return tasks.size
    }

    /**
     * Installs the already downloaded package. After success the library entry
     * is updated, files are deleted and the task moves to the completed history.
     */
    suspend fun install(
        packageName: String,
        onUserActionRequired: (Intent) -> Unit = {},
    ): InstallResult = withContext(NonCancellable) {
        // We run an install of a single package only once at a time: auto-install
        // and a manual tap can arrive at the same moment.
        if (!installingPackages.add(packageName)) {
            return@withContext InstallResult.Failure(
                InstallFailure(
                    InstallFailureCode.SESSION_FAILED,
                    context.getString(R.string.download_error_install_in_progress),
                ),
            )
        }
        try {
            installOnce(packageName, onUserActionRequired)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            val failure = TaskFailure(
                code = TaskFailureCode.UNKNOWN,
                detail = error.message?.take(300),
            )
            repository.setInstallState(packageName, InstallStage.FAILED, failure)
            notifications.showFailed(
                packageName,
                repository.get(packageName)?.name ?: packageName,
                failure,
            )
            InstallResult.Failure(
                InstallFailure(
                    InstallFailureCode.UNKNOWN,
                    error.message?.take(300),
                ),
            )
        } finally {
            installingPackages.remove(packageName)
        }
    }

    private suspend fun installOnce(
        packageName: String,
        onUserActionRequired: (Intent) -> Unit,
    ): InstallResult {
        val record = repository.get(packageName)
        val installed = libraryRepository.get(packageName)
        val name = record?.name ?: installed?.name
        val versionCode = record?.versionCode ?: installed?.latestVersionCode

        // The app may have been installed manually since the download was
        // prepared (or by a previous install whose result was lost).
        val installedNow = installedVersionCode(packageName)
        if (installedNow != null && versionCode != null && installedNow >= versionCode) {
            // The app got on the device without us: start tracking it anyway,
            // otherwise a GitHub-linked app would never be checked for updates.
            libraryRepository.trackInstalled(
                packageName = packageName,
                appId = record?.appId ?: installed?.appId,
                name = name,
                iconUrl = record?.iconUrl ?: installed?.iconUrl,
                publisher = record?.publisher ?: installed?.publisher,
            )
            downloader.clear(packageName)
            repository.markCompleted(packageName, installedNow, System.currentTimeMillis())
            notifications.clear(packageName)
            return InstallResult.Success
        }

        val files = downloader.downloadedFiles(packageName)
        if (files.isEmpty()) {
            repository.markFailed(
                packageName,
                TaskFailure(TaskFailureCode.FILES_LOST),
                record?.attemptCount ?: 0,
            )
            notifications.showFailed(
                packageName,
                name ?: packageName,
                TaskFailure(TaskFailureCode.FILES_LOST),
            )
            return InstallResult.Failure(
                InstallFailure(InstallFailureCode.INTERRUPTED, "files missing"),
            )
        }

        repository.setInstallState(packageName, InstallStage.INSTALLING)
        notifications.clear(packageName)

        // Silent install is opt-in. If Shizuku is unavailable or its service
        // fails for connectivity reasons, fall back to the system installer so
        // the user is never stuck without a dialog.
        val silent = settingsRepository.settings.first().installMethod == InstallMethod.SHIZUKU
        val result = if (silent && shizukuInstaller.status.value == ShizukuStatus.READY) {
            when (val attempt = shizukuInstaller.install(packageName, files)) {
                is InstallResult.Success -> attempt
                is InstallResult.Failure -> {
                    val fallback = attempt.failure.code in SHIZUKU_FALLBACK_CODES
                    if (fallback) {
                        systemInstall(packageName, files, versionCode, onUserActionRequired)
                    } else {
                        attempt
                    }
                }
            }
        } else {
            systemInstall(packageName, files, versionCode, onUserActionRequired)
        }

        if (result is InstallResult.Success) {
            val newVersionCode = installedVersionCode(packageName) ?: versionCode
            // GitHub releases carry no app icon: read the real one from the
            // installed files instead of keeping the repository avatar.
            val iconUrl = if (record?.sourceType == DownloadWorker.SOURCE_GITHUB) {
                appIconStore.fromApk(files, packageName, newVersionCode ?: 0L)
                    ?: record.iconUrl ?: installed?.iconUrl
            } else {
                record?.iconUrl ?: installed?.iconUrl
            }
            libraryRepository.trackInstalled(
                packageName = packageName,
                appId = record?.appId ?: installed?.appId,
                name = name,
                iconUrl = iconUrl,
                publisher = record?.publisher ?: installed?.publisher,
            )
            downloader.clear(packageName)
            repository.markCompleted(
                packageName = packageName,
                versionCode = newVersionCode,
                completedAt = System.currentTimeMillis(),
            )
            runCatching { repository.trimHistory(MAX_HISTORY, historyCutoff()) }
            notifications.clear(packageName)
        } else if (result is InstallResult.Failure) {
            val failure = result.toTaskFailure()
            if (failure.needsRedownload) {
                downloader.clear(packageName)
                repository.markFailed(packageName, failure, record?.attemptCount ?: 0)
            } else {
                repository.setInstallState(packageName, InstallStage.FAILED, failure)
            }
            // Cancelling the system dialog is a neutral action, not an error.
            if (failure.code != TaskFailureCode.INSTALL_CANCELLED) {
                notifications.showFailed(packageName, name ?: packageName, failure)
            } else {
                notifications.clear(packageName)
            }
        }
        return result
    }

    private suspend fun systemInstall(
        packageName: String,
        files: List<java.io.File>,
        versionCode: Long?,
        onUserActionRequired: (Intent) -> Unit,
    ): InstallResult {
        // The system installer is about to show its confirmation dialog.
        // Set this before starting the session so an async write cannot race
        // with a very fast success/failure result.
        repository.setInstallState(packageName, InstallStage.AWAITING_USER)
        return installer.install(
            packageName = packageName,
            apks = files,
            expectedVersionCode = versionCode,
        ) { intent ->
            _userActions.trySend(intent)
            onUserActionRequired(intent)
        }
    }

    /** Clears all non-active downloaded files. Ready/paused tasks become recoverable failures. */
    suspend fun clearCache(): Int = withContext(Dispatchers.IO) {
        var cleared = 0
        repository.getAll().forEach { record ->
            if (record.stage in CACHEABLE_STAGES) {
                downloader.clear(record.packageName)
                if (record.stage == DownloadStage.READY || record.stage == DownloadStage.PAUSED) {
                    repository.markFilesLost(record.packageName)
                }
                cleared++
            }
        }
        cleared
    }

    suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        downloader.cacheSizeBytes()
    }

    suspend fun clearCompleted() = repository.clearCompleted()

    /**
     * Reconciles the durable ledger with the actual device state after process
     * death, an app update or WorkManager pruning.
     */
    suspend fun reconcile() {
        var records = repository.getAll()
        val allWorkInfos = withContext(Dispatchers.IO) {
            runCatching {
                WorkManager.getInstance(context)
                    .getWorkInfosByTag(DownloadWorker.TAG)
                    .get()
            }.getOrDefault(emptyList())
        }
        importLegacyWork(records.mapTo(mutableSetOf()) { it.packageName }, allWorkInfos)
        records = repository.getAll()
        val workInfos = allWorkInfos.associateBy { it.id.toString() }

        records.forEach { record ->
            val installed = installedVersionCode(record.packageName)
            val info = record.workId?.let(workInfos::get)

            val targetVersion = record.versionCode
            when (record.stage) {
                DownloadStage.READY -> {
                    val installing = record.installStage == InstallStage.INSTALLING ||
                        record.installStage == InstallStage.AWAITING_USER
                    when {
                        installed != null && targetVersion != null && installed >= targetVersion -> {
                            downloader.clear(record.packageName)
                            repository.markCompleted(
                                record.packageName,
                                installed,
                                System.currentTimeMillis(),
                            )
                        }

                        !downloader.hasFiles(record.packageName) ->
                            repository.markFailed(
                                record.packageName,
                                TaskFailure(TaskFailureCode.FILES_LOST),
                                record.attemptCount,
                            )

                        installing -> repository.setInstallState(
                            record.packageName,
                            InstallStage.FAILED,
                            TaskFailure(TaskFailureCode.INTERRUPTED),
                        )
                    }
                }

                DownloadStage.PAUSED -> {
                    if (!downloader.hasFiles(record.packageName)) {
                        repository.markFilesLost(record.packageName)
                    }
                }

                DownloadStage.QUEUED,
                DownloadStage.DOWNLOADING,
                DownloadStage.VERIFYING,
                -> {
                    when {
                        info?.state == WorkInfo.State.SUCCEEDED &&
                            downloader.hasFiles(record.packageName) ->
                            repository.markReady(
                                record.packageName,
                                record.versionCode,
                                downloader.downloadedFiles(record.packageName)
                                    .sumOf { it.length() },
                            )

                        info == null ||
                            info.state == WorkInfo.State.CANCELLED ||
                            info.state == WorkInfo.State.FAILED -> {
                            if (record.installStage == InstallStage.INSTALLING ||
                                record.installStage == InstallStage.AWAITING_USER
                            ) {
                                if (installed != null && targetVersion != null &&
                                    installed >= targetVersion
                                ) {
                                    repository.markCompleted(
                                        record.packageName,
                                        installed,
                                        System.currentTimeMillis(),
                                    )
                                } else {
                                    if (downloader.hasFiles(record.packageName)) {
                                        repository.markReady(
                                            record.packageName,
                                            record.versionCode,
                                            record.totalBytes,
                                        )
                                        repository.setInstallState(
                                            record.packageName,
                                            InstallStage.FAILED,
                                            TaskFailure(TaskFailureCode.INTERRUPTED),
                                        )
                                    } else {
                                        repository.markFailed(
                                            record.packageName,
                                            TaskFailure(TaskFailureCode.FILES_LOST),
                                            record.attemptCount,
                                        )
                                    }
                                }
                            } else {
                                repository.markInterrupted(record.packageName)
                            }
                        }
                    }
                }
            }
        }

        repository.trimHistory(MAX_HISTORY, historyCutoff())
        downloader.cleanupOrphans(repository.knownPackages())
    }

    /**
     * A task queued before this version had no ledger row. Import the still
     * relevant WorkManager records so an app update does not lose active or
     * ready downloads.
     */
    private suspend fun importLegacyWork(
        knownPackages: MutableSet<String>,
        infos: List<WorkInfo>,
    ) {
        val now = System.currentTimeMillis()
        infos.groupBy { info ->
            info.tags
                .firstOrNull { it.startsWith(DownloadWorker.PKG_TAG_PREFIX) }
                ?.removePrefix(DownloadWorker.PKG_TAG_PREFIX)
        }.forEach { (packageName, workInfos) ->
            if (packageName == null || packageName in knownPackages) return@forEach

            // A package can have several records from previous retries. Prefer
            // the currently running one, then a queued one, then the newest
            // success; an old failure must not shadow a newer download.
            val info = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                ?: workInfos.firstOrNull {
                    it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
                }
                ?: workInfos.filter { it.state == WorkInfo.State.SUCCEEDED }
                    .maxByOrNull { it.outputVersionCode() }
                ?: workInfos.firstOrNull { it.state == WorkInfo.State.FAILED }
                ?: return@forEach

            knownPackages += packageName
            val data = when (info.state) {
                WorkInfo.State.RUNNING -> info.progress
                else -> info.outputData
            }
            val app = libraryRepository.get(packageName)
            val versionCode = data.getLong(DownloadWorker.KEY_VERSION_CODE, DownloadWorker.UNKNOWN_VERSION)
                .takeIf { it >= 0 }
            // Do not resurrect an old WorkManager record for an app that is
            // already installed (the files were deleted after installation).
            val installed = installedVersionCode(packageName)
            if (installed != null && versionCode != null && installed >= versionCode) {
                return@forEach
            }
            val failureCode = data.getString(DownloadWorker.KEY_FAILURE_CODE)
                ?.let { name -> TaskFailureCode.entries.firstOrNull { it.name == name } }
            val stage = when (info.state) {
                WorkInfo.State.RUNNING -> DownloadStage.DOWNLOADING
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.BLOCKED,
                -> DownloadStage.QUEUED

                WorkInfo.State.SUCCEEDED -> DownloadStage.READY
                else -> DownloadStage.FAILED
            }
            repository.upsert(
                DownloadTaskEntity(
                    packageName = packageName,
                    appId = data.getLong(DownloadWorker.KEY_APP_ID, DownloadWorker.UNKNOWN_APP_ID)
                        .takeIf { it > 0 } ?: app?.appId,
                    name = data.stringOrNull(DownloadWorker.KEY_NAME) ?: app?.name ?: packageName,
                    iconUrl = data.stringOrNull(DownloadWorker.KEY_ICON) ?: app?.iconUrl,
                    publisher = data.stringOrNull(DownloadWorker.KEY_PUBLISHER) ?: app?.publisher,
                    versionCode = versionCode,
                    versionName = data.stringOrNull(DownloadWorker.KEY_VERSION_NAME)
                        ?: app?.latestVersionName,
                    totalBytes = data.getLong(DownloadWorker.KEY_TOTAL_BYTES, -1L)
                        .takeIf { it > 0 },
                    stage = stage,
                    workId = info.id.toString(),
                    failureCode = if (stage == DownloadStage.FAILED) {
                        (failureCode ?: TaskFailureCode.UNKNOWN).name
                    } else {
                        null
                    },
                    failureDetail = data.getString(DownloadWorker.KEY_FAILURE_DETAIL)
                        ?: data.getString(DownloadWorker.KEY_MESSAGE),
                    installStage = InstallStage.IDLE,
                    installFailureCode = null,
                    installFailureDetail = null,
                    attemptCount = 0,
                    createdAt = now,
                    updatedAt = now,
                    completedAt = null,
                ),
            )
        }
    }

    private fun WorkInfo.outputVersionCode(): Long =
        outputData.getLong(DownloadWorker.KEY_VERSION_CODE, -1L)

    private fun androidx.work.Data.stringOrNull(key: String): String? =
        getString(key)?.takeIf { it.isNotBlank() }

    private suspend fun installedVersionCode(packageName: String): Long? = try {
        PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(packageName, 0),
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: Exception) {
        null
    }

    private fun historyCutoff(): Long =
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(HISTORY_DAYS)

    private fun DownloadTask.toRequest(): DownloadRequest = DownloadRequest(
        packageName = packageName,
        appId = appId,
        versionCode = versionCode,
        name = name,
        iconUrl = iconUrl,
        publisher = publisher,
        versionName = versionName,
    )

    /** Packages whose install is already running — protection against parallel starts. */
    private val installingPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private companion object {
        val SHIZUKU_FALLBACK_CODES = setOf(
            InstallFailureCode.SHIZUKU_UNAVAILABLE,
            InstallFailureCode.SESSION_FAILED,
            InstallFailureCode.INTERRUPTED,
        )

        val CACHEABLE_STAGES = setOf(
            DownloadStage.READY,
            DownloadStage.PAUSED,
            DownloadStage.FAILED,
            DownloadStage.COMPLETED,
        )

        const val MAX_HISTORY = 20
        const val HISTORY_DAYS = 30L
    }
}

/**
 * A "ready to install" entry is no longer relevant if the same or a newer
 * version is on the device: the downloaded file is either already installed or
 * outdated. Completed history is kept.
 */
private fun DownloadTask.isAlreadyInstalled(installedVersionCode: Long?): Boolean {
    if (!isReady) return false
    val downloaded = versionCode ?: return false
    return installedVersionCode != null && installedVersionCode >= downloaded
}
