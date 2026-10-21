/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.vntrum.ruthenia.core.data.settings.SettingsRepository

/** Schedules and observes the GitHub first-install worker. */
@Singleton
class GithubInstallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    suspend fun enqueue(request: GithubInstallRequest) {
        val unmetered = settingsRepository.settings.first().unmeteredOnly

        val work = OneTimeWorkRequestBuilder<GithubInstallWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED,
                    )
                    .build(),
            )
            .setInputData(
                workDataOf(
                    GithubInstallWorker.KEY_OWNER to request.owner,
                    GithubInstallWorker.KEY_REPO to request.repo,
                    GithubInstallWorker.KEY_REPO_ID to (request.repoId ?: -1L),
                    GithubInstallWorker.KEY_DISPLAY_NAME to request.displayName,
                    GithubInstallWorker.KEY_AVATAR to request.avatarUrl,
                    GithubInstallWorker.KEY_RELEASE_ID to request.releaseId,
                    GithubInstallWorker.KEY_RELEASE_TAG to request.releaseTag,
                    GithubInstallWorker.KEY_RELEASE_VERSION to request.releaseVersionName,
                    GithubInstallWorker.KEY_ASSET_NAME to request.assetName,
                    GithubInstallWorker.KEY_ASSET_URL to request.assetUrl,
                    GithubInstallWorker.KEY_ASSET_SIZE to (request.assetSize ?: -1L),
                    GithubInstallWorker.KEY_ASSET_SHA256 to request.assetSha256,
                    GithubInstallWorker.KEY_INCLUDE_PRERELEASES to request.includePrereleases,
                ),
            )
            .addTag(GithubInstallWorker.TAG)
            .addTag(GithubInstallWorker.repoTag(request.fullName))
            .build()

        workManager.enqueueUniqueWork(
            GithubInstallWorker.uniqueName(request.fullName),
            ExistingWorkPolicy.REPLACE,
            work,
        )
    }

    fun observe(repoFullName: String): Flow<GithubInstallState> =
        workManager.getWorkInfosForUniqueWorkFlow(GithubInstallWorker.uniqueName(repoFullName))
            .map { infos -> infos.lastOrNull().toState() }

    fun cancel(repoFullName: String) {
        workManager.cancelUniqueWork(GithubInstallWorker.uniqueName(repoFullName))
    }

    private fun WorkInfo?.toState(): GithubInstallState {
        if (this == null) return GithubInstallState.Idle
        return when (state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED,
            -> GithubInstallState.Queued

            WorkInfo.State.RUNNING -> GithubInstallState.Running(
                progress.getFloat(GithubInstallWorker.KEY_FRACTION, -1f)
                    .takeIf { it >= 0f },
            )

            WorkInfo.State.SUCCEEDED -> GithubInstallState.Ready(
                packageName = outputData.getString(GithubInstallWorker.KEY_PACKAGE).orEmpty(),
                name = outputData.getString(GithubInstallWorker.KEY_NAME),
            )

            WorkInfo.State.FAILED -> GithubInstallState.Failed(
                outputData.getString(GithubInstallWorker.KEY_ERROR)
                    ?: "GitHub install failed",
            )

            WorkInfo.State.CANCELLED -> GithubInstallState.Idle
        }
    }
}
