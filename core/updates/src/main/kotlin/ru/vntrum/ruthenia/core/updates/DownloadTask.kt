/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

/** Where the APK files for a download come from. */
sealed interface DownloadSource {

    /** Resolve links through the RuStore API using [appId]. */
    data object RuStore : DownloadSource

    /** A direct GitHub release asset. */
    data class GithubAsset(
        val url: String,
        val size: Long?,
        val sha256: String?,
    ) : DownloadSource
}

internal val DownloadSource.type: String
    get() = when (this) {
        DownloadSource.RuStore -> "RUSTORE"
        is DownloadSource.GithubAsset -> "GITHUB"
    }

internal val DownloadSource.url: String?
    get() = (this as? DownloadSource.GithubAsset)?.url

internal val DownloadSource.size: Long?
    get() = (this as? DownloadSource.GithubAsset)?.size

internal val DownloadSource.sha256: String?
    get() = (this as? DownloadSource.GithubAsset)?.sha256

/**
 * Request for a background APK download.
 *
 * Describes an update of an already installed app and a first-time install from
 * the catalog in the same way: the metadata is needed by the download manager
 * to show a row with an icon and name, even if the app is not yet in the
 * library.
 */
data class DownloadRequest(
    val packageName: String,
    val appId: Long? = null,
    val versionCode: Long? = null,
    val name: String? = null,
    val iconUrl: String? = null,
    val publisher: String? = null,
    /** Target version name for the manager row and completed history. */
    val versionName: String? = null,
    val source: DownloadSource = DownloadSource.RuStore,
)

/**
 * A download manager row: the app metadata along with the state of its
 * background download and install. The durable source is the Room ledger;
 * live progress is merged in from WorkManager.
 */
data class DownloadTask(
    val packageName: String,
    val appId: Long?,
    val name: String?,
    val iconUrl: String?,
    val publisher: String?,
    val versionCode: Long?,
    val versionName: String? = null,
    val totalBytes: Long? = null,
    val source: DownloadSource = DownloadSource.RuStore,
    val download: DownloadState,
    val install: InstallState = InstallState.Idle,
    val completedAt: Long? = null,
) {

    /** The download is active: queued, downloading, verifying, or installing. */
    val isActive: Boolean
        get() = download.isBusy ||
            install is InstallState.Installing ||
            install is InstallState.AwaitingUser

    /** Files are downloaded and waiting for the user to start the install. */
    val isReady: Boolean
        get() = download is DownloadState.Ready

    /** The download or the install needs the user's attention. */
    val isFailed: Boolean
        get() = download is DownloadState.Failed || install is InstallState.Failed

    /** The app was installed and the record is kept as history. */
    val isCompleted: Boolean
        get() = download is DownloadState.Completed

    /** Paused with partial files on disk. */
    val isPaused: Boolean
        get() = download is DownloadState.Paused

    /** The primary failure, if any. */
    val failure: TaskFailure?
        get() = (download as? DownloadState.Failed)?.failure
            ?: (install as? InstallState.Failed)?.failure

    val canPause: Boolean
        get() = download is DownloadState.Queued ||
            download is DownloadState.Downloading ||
            download is DownloadState.Verifying

    val canResume: Boolean
        get() = download is DownloadState.Paused

    val canInstall: Boolean
        get() = download is DownloadState.Ready && install !is InstallState.Installing

    val canRedownload: Boolean
        get() = failure?.needsRedownload == true || download is DownloadState.Failed
}
