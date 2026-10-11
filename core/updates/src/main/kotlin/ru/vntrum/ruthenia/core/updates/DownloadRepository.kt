/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import com.vntrum.ruthenia.core.data.settings.SettingsRepository

/**
 * Queues and observes background downloads.
 *
 * Each app gets a unique work item, so progress is tracked separately, and
 * tapping again restarts the download rather than spawning duplicates. The
 * state is kept by WorkManager — it survives a process restart and does not
 * need a separate Room table.
 *
 * The UI gets the metadata (name, icon, publisher) from the work progress and
 * result; if the work is still queued — from the last [enqueue] request.
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    /** Recent requests by package name — a fallback metadata source. */
    private val requests = MutableStateFlow<Map<String, DownloadRequest>>(emptyMap())

    /** All downloads: updates and first-time installs, in order of importance. */
    val downloads: Flow<List<DownloadTask>> =
        workManager.getWorkInfosByTagFlow(DownloadWorker.TAG)
            .combine(requests) { infos, pending -> toTasks(infos, pending) }

    /** Queues a download, replacing the previous work for the same package. */
    suspend fun enqueue(request: DownloadRequest) {
        requests.update { it + (request.packageName to request) }

        // "Wi-Fi only" applies to both checks and the downloads themselves.
        val unmeteredOnly = settingsRepository.settings.first().unmeteredOnly
        val networkType = if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        val work = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
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
                ),
            )
            // Exponential backoff before retrying a network failure.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(DownloadWorker.TAG)
            .addTag(DownloadWorker.packageTag(request.packageName))
            .build()

        workManager.enqueueUniqueWork(
            DownloadWorker.uniqueName(request.packageName),
            ExistingWorkPolicy.REPLACE,
            work,
        )
    }

    /** Cancels the download; the worker itself deletes the temporary files. */
    fun cancel(packageName: String) {
        workManager.cancelUniqueWork(DownloadWorker.uniqueName(packageName))
    }

    /** Clears the entry after a successful install or a manual "forget". */
    fun clear(packageName: String) {
        requests.update { it - packageName }
        workManager.cancelUniqueWork(DownloadWorker.uniqueName(packageName))
    }

    private fun toTasks(
        infos: List<WorkInfo>,
        pending: Map<String, DownloadRequest>,
    ): List<DownloadTask> =
        infos
            .mapNotNull { info ->
                val packageName = info.tags
                    .firstOrNull { it.startsWith(DownloadWorker.PKG_TAG_PREFIX) }
                    ?.removePrefix(DownloadWorker.PKG_TAG_PREFIX)
                    ?: return@mapNotNull null
                packageName to info
            }
            .groupBy({ it.first }, { it.second })
            .map { (packageName, workInfoList) ->
                val request = pending[packageName]
                val state = workInfoList.reduceToState()
                val meta = workInfoList.metadata(request)
                DownloadTask(
                    packageName = packageName,
                    appId = meta.appId ?: request?.appId,
                    name = meta.name ?: request?.name,
                    iconUrl = meta.iconUrl ?: request?.iconUrl,
                    publisher = meta.publisher ?: request?.publisher,
                    versionCode = meta.versionCode ?: request?.versionCode,
                    state = state,
                )
            }
            .sortedWith(
                compareBy({ it.rank() }, { it.name ?: it.packageName }),
            )

    private fun List<WorkInfo>.reduceToState(): DownloadState {
        firstOrNull { it.state == WorkInfo.State.RUNNING }?.let { running ->
            val fraction = running.progress
                .getFloat(
                    DownloadWorker.KEY_FRACTION,
                    DownloadWorker.UNKNOWN_FRACTION,
                )
                .takeIf { it >= 0f }
            val verifying = running.progress.getString(DownloadWorker.KEY_PHASE) ==
                DownloadWorker.PHASE_VERIFYING
            return if (verifying) {
                DownloadState.Verifying
            } else {
                DownloadState.Downloading(fraction)
            }
        }

        if (any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }) {
            return DownloadState.Queued
        }

        // There can be several finished records for one package name (retries).
        // The freshest version wins; when versions are equal a failure beats an
        // old success — the files are deleted after a failure.
        val success = filter { it.state == WorkInfo.State.SUCCEEDED }
            .maxByOrNull { it.outputVersionCode() }
        val failure = filter { it.state == WorkInfo.State.FAILED }
            .maxByOrNull { it.outputVersionCode() }
        val successVersion = success?.outputVersionCode() ?: Long.MIN_VALUE
        val failureVersion = failure?.outputVersionCode() ?: Long.MIN_VALUE

        return when {
            failure != null && failureVersion >= successVersion -> DownloadState.Failed(
                failure.outputData.getString(DownloadWorker.KEY_MESSAGE).orEmpty(),
            )

            success != null -> DownloadState.Ready(
                successVersion.takeIf { it >= 0L },
            )

            any { it.state == WorkInfo.State.CANCELLED } -> DownloadState.Cancelled

            else -> DownloadState.Idle
        }
    }

    /**
     * We take the metadata from the active progress, otherwise from the result,
     * otherwise from the request. That way the manager row stays meaningful at
     * every stage.
     */
    private fun List<WorkInfo>.metadata(request: DownloadRequest?): Meta {
        val source = firstOrNull { it.state == WorkInfo.State.RUNNING }
            ?: maxByOrNull { it.outputVersionCode() }

        val data: Data = when (source?.state) {
            WorkInfo.State.RUNNING -> source.progress
            null -> Data.EMPTY
            else -> source.outputData
        }

        val appId = data.getLong(DownloadWorker.KEY_APP_ID, DownloadWorker.UNKNOWN_APP_ID)
            .takeIf { it > 0 }
        val versionCode = data.getLong(DownloadWorker.KEY_VERSION_CODE, DownloadWorker.UNKNOWN_VERSION)
            .takeIf { it >= 0 }

        return Meta(
            appId = appId,
            name = data.stringOrNull(DownloadWorker.KEY_NAME),
            iconUrl = data.stringOrNull(DownloadWorker.KEY_ICON),
            publisher = data.stringOrNull(DownloadWorker.KEY_PUBLISHER),
            versionCode = versionCode,
        )
    }

    private fun WorkInfo.outputVersionCode(): Long =
        outputData.getLong(DownloadWorker.KEY_VERSION_CODE, -1L)

    /** Row order: active, ready to install, failed, the rest. */
    private fun DownloadTask.rank(): Int = when {
        isActive -> 0
        isReady -> 1
        isFailed -> 2
        else -> 3
    }

    private data class Meta(
        val appId: Long?,
        val name: String?,
        val iconUrl: String?,
        val publisher: String?,
        val versionCode: Long?,
    )
}

private fun Data.stringOrNull(key: String): String? =
    getString(key)?.takeIf { it.isNotBlank() }
