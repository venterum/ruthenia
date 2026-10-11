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
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.vntrum.ruthenia.core.installer.ApkDownloader
import com.vntrum.ruthenia.core.installer.ApkExporter
import com.vntrum.ruthenia.core.installer.ApkSource
import com.vntrum.ruthenia.core.installer.ExportEntry
import com.vntrum.ruthenia.core.network.RuStoreApi

/**
 * Saves an app's APK into the user-selected folder.
 *
 * Downloads the files (preferably a single universal APK without splits) and
 * copies them into the SAF tree. Like [DownloadWorker], it runs in the
 * background and shows progress via a notification; it does not touch installs.
 */
@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val api: RuStoreApi,
    private val downloader: ApkDownloader,
    private val exporter: ApkExporter,
    private val notifications: DownloadNotifications,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val packageName = inputData.getString(KEY_PACKAGE) ?: return failure()
        val appId = inputData.getLong(KEY_APP_ID, UNKNOWN_APP_ID)
        val treeUri = inputData.getString(KEY_TREE_URI) ?: return failure()
        if (appId <= 0) return failure()

        val name = inputData.getString(KEY_NAME) ?: packageName
        val versionName = inputData.getString(KEY_VERSION_NAME).orEmpty()

        return try {
            val links = api.downloadLinks(appId, firstInstall = false, withoutSplits = true)
            if (links.urls.isEmpty()) return failure()

            notifications.showProgress(packageName, name, fraction = null)
            val files = downloader.download(
                packageName = packageName,
                sources = links.urls.map { ApkSource(url = it.url, size = it.size) },
                onProgress = { progress ->
                    notifications.showProgress(packageName, name, progress.fraction)
                },
            )
            if (files.isEmpty()) return failure()

            val folderName = safeName("$name $versionName".trim())
            val entries = files.mapIndexed { index, file ->
                ExportEntry(
                    file = file,
                    fileName = if (files.size == 1) {
                        "$folderName.apk"
                    } else if (index == 0) {
                        "base.apk"
                    } else {
                        "split_$index.apk"
                    },
                )
            }

            exporter.export(treeUriString = treeUri, folderName = folderName, entries = entries)
            downloader.clear(packageName)
            notifications.showExportDone(packageName, name)
            Result.success()
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { downloader.clear(packageName) }
            notifications.clear(packageName)
            throw cancellation
        } catch (error: Throwable) {
            downloader.clear(packageName)
            notifications.clear(packageName)
            Result.failure(
                workDataOf(KEY_MESSAGE to error.message.orEmpty()),
            )
        }
    }

    private fun failure(): Result = Result.failure(
        workDataOf(KEY_MESSAGE to applicationContext.getString(R.string.export_error)),
    )

    private fun safeName(raw: String): String = raw
        .map { if (it in FORBIDDEN_CHARS || it.isISOControl()) '_' else it }
        .joinToString("")
        .trim()
        .ifEmpty { "app" }

    companion object {
        const val TAG = "ruthenia-export"
        const val KEY_PACKAGE = "packageName"
        const val KEY_APP_ID = "appId"
        const val KEY_NAME = "name"
        const val KEY_VERSION_NAME = "versionName"
        const val KEY_TREE_URI = "treeUri"
        const val KEY_MESSAGE = "message"

        private const val UNKNOWN_APP_ID = -1L
        private val FORBIDDEN_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

        fun uniqueName(packageName: String): String = "ruthenia-export-$packageName"
    }
}
