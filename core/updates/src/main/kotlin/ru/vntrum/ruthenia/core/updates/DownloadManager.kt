/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import com.vntrum.ruthenia.core.data.repository.LibraryRepository
import com.vntrum.ruthenia.core.data.settings.InstallMethod
import com.vntrum.ruthenia.core.data.settings.SettingsRepository
import com.vntrum.ruthenia.core.updates.R
import com.vntrum.ruthenia.core.installer.ApkDownloader
import com.vntrum.ruthenia.core.installer.ApkInstaller
import com.vntrum.ruthenia.core.installer.InstallResult
import com.vntrum.ruthenia.core.installer.shizuku.ShizukuInstaller

/**
 * Download manager — a single entry point for downloading and installing.
 *
 * Both library updates and first-time installs from the app details screen go
 * through it: the download always runs as background work, and the UI just
 * observes [downloads]. The install is started by the user — with the system
 * backend the confirmation dialog is passed up through [onUserActionRequired];
 * with the optional Shizuku backend it happens silently.
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
) {

    /**
     * All downloads for the manager and app details screens.
     *
     * Ready-to-install entries whose version is already on the device are
     * hidden: WorkManager keeps finished work, and without this reconciliation
     * the "ready to install" row would remain in the manager after install.
     */
    val downloads: Flow<List<DownloadTask>> =
        repository.downloads.combine(libraryRepository.observeLibrary()) { tasks, apps ->
            val installed = apps.associate { it.packageName to it.installedVersionCode }
            tasks.filterNot { task -> task.isAlreadyInstalled(installed[task.packageName]) }
        }

    /** Queues a download (an update or a first-time install). */
    suspend fun request(request: DownloadRequest) = repository.enqueue(request)

    /** Cancels the active download. */
    fun cancel(packageName: String) = repository.cancel(packageName)

    /** Removes a finished/cancelled entry from the manager. */
    fun forget(packageName: String) = repository.clear(packageName)

    /**
     * Installs the already downloaded package. After success the library entry
     * is updated and the temporary files are deleted.
     */
    suspend fun install(
        packageName: String,
        appId: Long?,
        name: String?,
        iconUrl: String?,
        publisher: String?,
        onUserActionRequired: (Intent) -> Unit = {},
    ): InstallResult {
        // We run an install of a single package only once at a time: auto-install
        // and a manual tap can arrive at the same moment.
        if (!installingPackages.add(packageName)) {
            return InstallResult.Failure(
                context.getString(R.string.download_error_install_in_progress),
            )
        }
        try {
            return installOnce(packageName, appId, name, iconUrl, publisher, onUserActionRequired)
        } finally {
            installingPackages.remove(packageName)
        }
    }

    private suspend fun installOnce(
        packageName: String,
        appId: Long?,
        name: String?,
        iconUrl: String?,
        publisher: String?,
        onUserActionRequired: (Intent) -> Unit,
    ): InstallResult {
        val files = downloader.downloadedFiles(packageName)
        if (files.isEmpty()) {
            repository.clear(packageName)
            return InstallResult.Failure(
                context.getString(R.string.download_error_files_missing),
            )
        }

        // Silent install is opt-in. If Shizuku fails for any reason (stopped
        // service, denied permission, a `pm` error), fall back to the system
        // installer so the user is never stuck without a dialog.
        val silent = settingsRepository.settings.first().installMethod == InstallMethod.SHIZUKU
        val result = if (silent) {
            when (val attempt = shizukuInstaller.install(packageName, files)) {
                is InstallResult.Success -> attempt
                is InstallResult.Failure ->
                    installer.install(packageName, files, onUserActionRequired)
            }
        } else {
            installer.install(packageName, files, onUserActionRequired)
        }

        if (result is InstallResult.Success) {
            libraryRepository.trackInstalled(
                packageName = packageName,
                appId = appId,
                name = name,
                iconUrl = iconUrl,
                publisher = publisher,
            )
            downloader.clear(packageName)
            repository.clear(packageName)
            notifications.clear(packageName)
        }
        return result
    }

    /** Packages whose install is already running — protection against parallel starts. */
    private val installingPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
}

/**
 * A "ready to install" entry is no longer relevant if the same or a newer
 * version is on the device: the downloaded file is either already installed or
 * outdated.
 */
private fun DownloadTask.isAlreadyInstalled(installedVersionCode: Long?): Boolean {
    if (!isReady) return false
    val downloaded = versionCode ?: return false
    return installedVersionCode != null && installedVersionCode >= downloaded
}
