/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import com.vntrum.ruthenia.core.data.model.UpdateStatus
import com.vntrum.ruthenia.core.data.model.isUpdateIgnored
import com.vntrum.ruthenia.core.data.repository.LibraryRepository
import com.vntrum.ruthenia.core.data.settings.SettingsRepository

/**
 * Background update check.
 *
 * The worker syncs installed versions, queries the catalog and shows a
 * notification. No installs in the background: they are always initiated by the
 * user. Updates hidden by the user do not make it into the notification.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val notifications: UpdateNotifications,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        libraryRepository.syncInstalledVersions()

        // Without a network query the worker would not find new versions: the
        // catalog must be asked again, otherwise the notification lives on data
        // from the previous check.
        val summary = libraryRepository.checkUpdates()
        if (summary.isTotalFailure) return Result.retry()

        val ignored = settingsRepository.settings.first().ignoredUpdates
        val library = libraryRepository.currentLibrary()
        val outdated = library.filter {
            it.status == UpdateStatus.UPDATE_AVAILABLE && !it.isUpdateIgnored(ignored)
        }

        if (outdated.isEmpty()) {
            notifications.clear()
        } else {
            notifications.showUpdatesAvailable(outdated)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "ruthenia-update-check"
    }
}