/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Queues a background APK save into the user-selected folder. */
@Singleton
class ApkExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Starts the APK save; a repeated call replaces the previous work. */
    fun request(
        packageName: String,
        appId: Long,
        name: String?,
        versionName: String?,
        treeUri: String,
    ) {
        val work = OneTimeWorkRequestBuilder<ExportWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                workDataOf(
                    ExportWorker.KEY_PACKAGE to packageName,
                    ExportWorker.KEY_APP_ID to appId,
                    ExportWorker.KEY_NAME to name,
                    ExportWorker.KEY_VERSION_NAME to versionName,
                    ExportWorker.KEY_TREE_URI to treeUri,
                ),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(ExportWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ExportWorker.uniqueName(packageName),
            ExistingWorkPolicy.REPLACE,
            work,
        )
    }
}
