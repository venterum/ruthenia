/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.vntrum.ruthenia.core.data.settings.AppSettings

/** Schedules the periodic update check. */
@Singleton
class UpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun reschedule(settings: AppSettings) {
        val workManager = WorkManager.getInstance(context)

        if (!settings.backgroundChecksEnabled) {
            workManager.cancelUniqueWork(UpdateCheckWorker.UNIQUE_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (settings.unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            settings.updateCheckIntervalHours.toLong(),
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UpdateCheckWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
