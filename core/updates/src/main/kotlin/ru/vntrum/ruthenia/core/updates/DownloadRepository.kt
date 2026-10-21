/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import com.vntrum.ruthenia.core.data.db.DownloadTaskDao
import com.vntrum.ruthenia.core.data.db.DownloadTaskEntity
import com.vntrum.ruthenia.core.data.settings.SettingsRepository
import com.vntrum.ruthenia.core.installer.ApkDownloader

/**
 * Durable download ledger plus WorkManager orchestration.
 *
 * WorkManager executes the download and owns live progress, while the Room
 * ledger keeps metadata, terminal failures, pause state and completed history
 * alive across process death and WorkManager pruning.
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadTaskDao,
    private val settingsRepository: SettingsRepository,
    private val downloader: ApkDownloader,
) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    /** All manager rows: durable ledger merged with live WorkManager progress. */
    val downloads: Flow<List<DownloadTask>> =
        combine(
            dao.observeAll(),
            workManager.getWorkInfosByTagFlow(DownloadWorker.TAG),
            settingsRepository.settings,
        ) { rows, infos, settings ->
            val byId = infos.associateBy { it.id.toString() }
            rows.mapNotNull { row ->
                row.toTask(
                    info = row.workId?.let(byId::get),
                    unmeteredOnly = settings.unmeteredOnly,
                )
            }.sortedWith(
                compareBy({ it.rank() }, { it.name ?: it.packageName }),
            )
        }

    /** Queues a download, replacing the previous work for the same package. */
    suspend fun enqueue(request: DownloadRequest) {
        val now = System.currentTimeMillis()
        val existing = dao.get(request.packageName)
        val unmetered = settingsRepository.settings.first().unmeteredOnly

        // Cancel the previous unique work before writing the new ledger row:
        // its cancellation handler would otherwise see the new QUEUED row and
        // race with the replacement worker.
        workManager.cancelUniqueWork(DownloadWorker.uniqueName(request.packageName)).await()

        val work = buildWork(request, unmetered)
        dao.upsert(
            DownloadTaskEntity(
                packageName = request.packageName,
                appId = request.appId ?: existing?.appId,
                name = request.name ?: existing?.name,
                iconUrl = request.iconUrl ?: existing?.iconUrl,
                publisher = request.publisher ?: existing?.publisher,
                versionCode = request.versionCode ?: existing?.versionCode,
                versionName = request.versionName ?: existing?.versionName,
                sourceType = request.source.type,
                sourceUrl = request.source.url,
                sourceSize = request.source.size,
                sourceSha256 = request.source.sha256,
                totalBytes = null,
                stage = DownloadStage.QUEUED,
                workId = work.id.toString(),
                failureCode = null,
                failureDetail = null,
                installStage = InstallStage.IDLE,
                installFailureCode = null,
                installFailureDetail = null,
                attemptCount = 0,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                completedAt = null,
            ),
        )

        workManager.enqueueUniqueWork(
            DownloadWorker.uniqueName(request.packageName),
            ExistingWorkPolicy.REPLACE,
            work,
        )
    }

    /**
     * Creates a READY ledger row for files downloaded outside [DownloadWorker]
     * (the first install from GitHub, where the package name is unknown until
     * the APK has been inspected). The normal install path takes it from here.
     */
    suspend fun adoptReady(
        packageName: String,
        appId: Long?,
        name: String?,
        iconUrl: String?,
        publisher: String?,
        versionCode: Long?,
        versionName: String?,
        totalBytes: Long?,
        source: DownloadSource,
    ) {
        val now = System.currentTimeMillis()
        val existing = dao.get(packageName)
        workManager.cancelUniqueWork(DownloadWorker.uniqueName(packageName)).await()
        dao.upsert(
            DownloadTaskEntity(
                packageName = packageName,
                appId = appId,
                name = name,
                iconUrl = iconUrl,
                publisher = publisher,
                versionCode = versionCode,
                versionName = versionName,
                sourceType = source.type,
                sourceUrl = source.url,
                sourceSize = source.size,
                sourceSha256 = source.sha256,
                totalBytes = totalBytes,
                stage = DownloadStage.READY,
                workId = null,
                failureCode = null,
                failureDetail = null,
                installStage = InstallStage.IDLE,
                installFailureCode = null,
                installFailureDetail = null,
                attemptCount = 0,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                completedAt = null,
            ),
        )
    }

    /** Resumes a paused task without deleting partial files. */
    suspend fun resume(packageName: String) {
        val existing = dao.get(packageName) ?: return
        if (existing.stage != DownloadStage.PAUSED && existing.stage != DownloadStage.QUEUED) return

        workManager.cancelUniqueWork(DownloadWorker.uniqueName(packageName)).await()
        val request = existing.toRequest()
        val now = System.currentTimeMillis()
        val work = buildWork(request, settingsRepository.settings.first().unmeteredOnly)
        dao.upsert(
            existing.copy(
                stage = DownloadStage.QUEUED,
                workId = work.id.toString(),
                failureCode = null,
                failureDetail = null,
                attemptCount = 0,
                updatedAt = now,
                completedAt = null,
            ),
        )
        workManager.enqueueUniqueWork(
            DownloadWorker.uniqueName(packageName),
            ExistingWorkPolicy.REPLACE,
            work,
        )
    }

    /** Stops the download and keeps partial files for a later resume. */
    suspend fun pause(packageName: String) {
        val existing = dao.get(packageName) ?: return
        if (existing.stage !in ACTIVE_STAGES) return

        dao.upsert(
            existing.copy(
                stage = DownloadStage.PAUSED,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        workManager.cancelUniqueWork(DownloadWorker.uniqueName(packageName)).await()
    }

    /** Cancels work, deletes files and removes the manager row. */
    suspend fun remove(packageName: String) {
        val existing = dao.get(packageName) ?: return
        if (existing.stage in ACTIVE_STAGES) {
            // The worker checks this stage in its cancellation handler and
            // deletes the temporary files itself. Awaiting the cancellation
            // avoids deleting a directory while the worker is still writing.
            dao.upsert(
                existing.copy(
                    stage = DownloadStage.REMOVING,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            workManager.cancelUniqueWork(DownloadWorker.uniqueName(packageName)).await()
        }
        downloader.clear(packageName)
        dao.delete(packageName)
    }

    suspend fun get(packageName: String): DownloadTaskEntity? = dao.get(packageName)

    suspend fun getAll(): List<DownloadTaskEntity> = dao.getAll()

    suspend fun upsert(task: DownloadTaskEntity) = dao.upsert(task)

    suspend fun markQueued(packageName: String, attemptCount: Int) {
        update(packageName) {
            if (it.stage == DownloadStage.PAUSED || it.stage == DownloadStage.REMOVING) {
                it
            } else {
                it.copy(
                    stage = DownloadStage.QUEUED,
                    failureCode = null,
                    failureDetail = null,
                    installStage = InstallStage.IDLE,
                    installFailureCode = null,
                    installFailureDetail = null,
                    attemptCount = attemptCount,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    suspend fun markDownloading(packageName: String) {
        update(packageName) {
            if (it.stage != DownloadStage.QUEUED && it.stage != DownloadStage.DOWNLOADING) {
                it
            } else {
                it.copy(stage = DownloadStage.DOWNLOADING, updatedAt = System.currentTimeMillis())
            }
        }
    }

    suspend fun markVerifying(packageName: String) {
        update(packageName) {
            if (it.stage != DownloadStage.DOWNLOADING && it.stage != DownloadStage.VERIFYING) {
                it
            } else {
                it.copy(stage = DownloadStage.VERIFYING, updatedAt = System.currentTimeMillis())
            }
        }
    }

    suspend fun markReady(packageName: String, versionCode: Long?, totalBytes: Long?) {
        update(packageName) {
            if (it.stage == DownloadStage.PAUSED || it.stage == DownloadStage.REMOVING) {
                it
            } else {
                it.copy(
                    stage = DownloadStage.READY,
                    versionCode = versionCode ?: it.versionCode,
                    totalBytes = totalBytes,
                    failureCode = null,
                    failureDetail = null,
                    installStage = InstallStage.IDLE,
                    installFailureCode = null,
                    installFailureDetail = null,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    suspend fun markFailed(packageName: String, failure: TaskFailure, attemptCount: Int) {
        update(packageName) {
            if (it.stage == DownloadStage.PAUSED || it.stage == DownloadStage.REMOVING) {
                it
            } else {
                it.copy(
                    stage = DownloadStage.FAILED,
                    failureCode = failure.code.name,
                    failureDetail = failure.detail,
                    attemptCount = attemptCount,
                    installStage = InstallStage.IDLE,
                    installFailureCode = null,
                    installFailureDetail = null,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    suspend fun markPaused(packageName: String) {
        update(packageName) {
            it.copy(stage = DownloadStage.PAUSED, updatedAt = System.currentTimeMillis())
        }
    }

    /** Explicit file loss (cache eviction, Clear cache) bypasses the pause guard. */
    suspend fun markFilesLost(packageName: String) {
        update(packageName) {
            if (it.stage == DownloadStage.REMOVING) {
                it
            } else {
                it.copy(
                    stage = DownloadStage.FAILED,
                    failureCode = TaskFailureCode.FILES_LOST.name,
                    failureDetail = null,
                    installStage = InstallStage.IDLE,
                    installFailureCode = null,
                    installFailureDetail = null,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    suspend fun markInterrupted(packageName: String) {
        update(packageName) {
            it.copy(
                stage = DownloadStage.FAILED,
                failureCode = TaskFailureCode.INTERRUPTED.name,
                failureDetail = null,
                installStage = InstallStage.IDLE,
                installFailureCode = null,
                installFailureDetail = null,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun setInstallState(
        packageName: String,
        stage: String,
        failure: TaskFailure? = null,
    ) {
        update(packageName) {
            it.copy(
                installStage = stage,
                installFailureCode = failure?.code?.name,
                installFailureDetail = failure?.detail,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun markCompleted(packageName: String, versionCode: Long?, completedAt: Long) {
        update(packageName) {
            it.copy(
                stage = DownloadStage.COMPLETED,
                versionCode = versionCode ?: it.versionCode,
                failureCode = null,
                failureDetail = null,
                installStage = InstallStage.IDLE,
                installFailureCode = null,
                installFailureDetail = null,
                updatedAt = completedAt,
                completedAt = completedAt,
            )
        }
    }

    suspend fun clearCompleted() = dao.clearCompleted()

    suspend fun trimHistory(max: Int, olderThan: Long) {
        dao.trimCompleted(max)
        dao.deleteCompletedOlderThan(olderThan)
    }

    /** Active package names, used by the reconciler to find orphan cache folders. */
    suspend fun knownPackages(): Set<String> = dao.getAll().mapTo(mutableSetOf()) { it.packageName }

    private suspend fun update(
        packageName: String,
        transform: (DownloadTaskEntity) -> DownloadTaskEntity,
    ) {
        val current = dao.get(packageName) ?: return
        dao.upsert(transform(current))
    }

    private fun buildWork(request: DownloadRequest, unmeteredOnly: Boolean) =
        OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
                    )
                    .build(),
            )
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_PACKAGE to request.packageName,
                    DownloadWorker.KEY_APP_ID to (request.appId ?: DownloadWorker.UNKNOWN_APP_ID),
                    DownloadWorker.KEY_VERSION_CODE to
                        (request.versionCode ?: DownloadWorker.UNKNOWN_VERSION),
                    DownloadWorker.KEY_NAME to request.name,
                    DownloadWorker.KEY_ICON to request.iconUrl,
                    DownloadWorker.KEY_PUBLISHER to request.publisher,
                    DownloadWorker.KEY_VERSION_NAME to request.versionName,
                    DownloadWorker.KEY_SOURCE_TYPE to request.source.type,
                    DownloadWorker.KEY_SOURCE_URL to request.source.url,
                    DownloadWorker.KEY_SOURCE_SIZE to request.source.size,
                    DownloadWorker.KEY_SOURCE_SHA256 to request.source.sha256,
                ),
            )
            // Exponential backoff before retrying a network failure.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(DownloadWorker.TAG)
            .addTag(DownloadWorker.packageTag(request.packageName))
            .build()

    private fun DownloadTaskEntity.toTask(info: WorkInfo?, unmeteredOnly: Boolean): DownloadTask? {
        if (stage == DownloadStage.REMOVING) return null
        val download = downloadState(info, unmeteredOnly)
        return DownloadTask(
            packageName = packageName,
            appId = appId,
            name = name,
            iconUrl = iconUrl,
            publisher = publisher,
            versionCode = versionCode,
            versionName = versionName,
            totalBytes = totalBytes,
            source = source(),
            download = download,
            install = installState(),
            completedAt = completedAt,
        )
    }

    private fun DownloadTaskEntity.downloadState(
        info: WorkInfo?,
        unmeteredOnly: Boolean,
    ): DownloadState = when (stage) {
        DownloadStage.READY -> DownloadState.Ready(versionCode)
        DownloadStage.PAUSED -> DownloadState.Paused
        DownloadStage.COMPLETED -> DownloadState.Completed(versionCode, completedAt ?: updatedAt)
        DownloadStage.FAILED -> DownloadState.Failed(failure())
        DownloadStage.QUEUED,
        DownloadStage.DOWNLOADING,
        DownloadStage.VERIFYING,
        -> fromLiveWork(info, unmeteredOnly)

        else -> DownloadState.Queued(QueueReason.QUEUED)
    }

    private fun DownloadTaskEntity.fromLiveWork(
        info: WorkInfo?,
        unmeteredOnly: Boolean,
    ): DownloadState {
        if (info == null) {
            return if (stage == DownloadStage.QUEUED) {
                queueState(unmeteredOnly)
            } else {
                DownloadState.Failed(TaskFailure(TaskFailureCode.INTERRUPTED))
            }
        }
        return when (info.state) {
            WorkInfo.State.RUNNING -> {
                val phase = info.progress.getString(DownloadWorker.KEY_PHASE)
                if (phase == DownloadWorker.PHASE_VERIFYING) {
                    DownloadState.Verifying
                } else {
                    val fraction = info.progress
                        .getFloat(DownloadWorker.KEY_FRACTION, DownloadWorker.UNKNOWN_FRACTION)
                        .takeIf { it >= 0f }
                    DownloadState.Downloading(
                        fraction = fraction,
                        downloadedBytes = info.progress.getLong(
                            DownloadWorker.KEY_DOWNLOADED_BYTES,
                            0L,
                        ),
                        totalBytes = info.progress
                            .getLong(DownloadWorker.KEY_TOTAL_BYTES, DownloadWorker.UNKNOWN_VERSION)
                            .takeIf { it > 0L }
                            ?: totalBytes,
                    )
                }
            }

            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED,
            -> queueState(unmeteredOnly)

            WorkInfo.State.SUCCEEDED -> DownloadState.Ready(versionCode)

            WorkInfo.State.FAILED -> DownloadState.Failed(
                failure().takeUnless { it.code == TaskFailureCode.UNKNOWN }
                    ?: TaskFailure(
                        code = TaskFailureCode.UNKNOWN,
                        detail = info.outputData.getString(DownloadWorker.KEY_MESSAGE),
                    ),
            )

            WorkInfo.State.CANCELLED -> if (stage == DownloadStage.PAUSED) {
                DownloadState.Paused
            } else {
                DownloadState.Failed(TaskFailure(TaskFailureCode.INTERRUPTED))
            }
        }
    }

    private fun DownloadTaskEntity.failure(): TaskFailure {
        val code = failureCode
            ?.let { name -> TaskFailureCode.entries.firstOrNull { it.name == name } }
            ?: TaskFailureCode.UNKNOWN
        return TaskFailure(code, failureDetail)
    }

    private fun DownloadTaskEntity.installState(): InstallState = when (installStage) {
        InstallStage.INSTALLING -> InstallState.Installing
        InstallStage.AWAITING_USER -> InstallState.AwaitingUser
        InstallStage.FAILED -> InstallState.Failed(
            TaskFailure(
                code = installFailureCode
                    ?.let { name -> TaskFailureCode.entries.firstOrNull { it.name == name } }
                    ?: TaskFailureCode.UNKNOWN,
                detail = installFailureDetail,
            ),
        )

        else -> InstallState.Idle
    }

    private fun DownloadTaskEntity.queueState(unmeteredOnly: Boolean): DownloadState.Queued {
        val reason = queueReason(unmeteredOnly)
        return DownloadState.Queued(
            if (attemptCount > 0 && reason == QueueReason.QUEUED) {
                QueueReason.RETRYING
            } else {
                reason
            },
        )
    }

    private fun queueReason(unmeteredOnly: Boolean): QueueReason {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return QueueReason.QUEUED
        return try {
            val network = manager.activeNetwork ?: return QueueReason.WAITING_NETWORK
            val capabilities = manager.getNetworkCapabilities(network)
                ?: return QueueReason.WAITING_NETWORK
            when {
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ->
                    QueueReason.WAITING_NETWORK

                unmeteredOnly &&
                    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ->
                    QueueReason.WAITING_WIFI

                else -> QueueReason.QUEUED
            }
        } catch (_: SecurityException) {
            QueueReason.QUEUED
        }
    }

    private fun DownloadTask.rank(): Int = when {
        isActive -> 0
        isPaused -> 1
        isReady -> 2
        isFailed -> 3
        isCompleted -> 4
        else -> 5
    }

    private fun DownloadTaskEntity.toRequest(): DownloadRequest = DownloadRequest(
        packageName = packageName,
        appId = appId,
        versionCode = versionCode,
        name = name,
        iconUrl = iconUrl,
        publisher = publisher,
        versionName = versionName,
        source = source(),
    )

    private fun DownloadTaskEntity.source(): DownloadSource = when (sourceType) {
        "GITHUB" -> DownloadSource.GithubAsset(
            url = sourceUrl.orEmpty(),
            size = sourceSize,
            sha256 = sourceSha256,
        )

        else -> DownloadSource.RuStore
    }

    private suspend fun Operation.await() = suspendCancellableCoroutine { continuation ->
        result.addListener(
            { if (continuation.isActive) continuation.resume(Unit) },
            ContextCompat.getMainExecutor(context),
        )
    }

    private companion object {
        val ACTIVE_STAGES = setOf(
            DownloadStage.QUEUED,
            DownloadStage.DOWNLOADING,
            DownloadStage.VERIFYING,
        )
    }
}
