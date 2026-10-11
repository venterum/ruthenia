/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

/**
 * State of an app's background download — shared by updates and first-time
 * installs.
 *
 * The source of truth is WorkManager: the state survives a process restart, so
 * both the library screen and the app details screen can honestly show progress
 * after the user leaves the screen or backgrounds the app.
 */
sealed interface DownloadState {

    /** No download. */
    data object Idle : DownloadState

    /** Work is queued (waiting for the network or a free slot). */
    data object Queued : DownloadState

    /** Downloading; [fraction] is the 0..1 share or `null` if the size is unknown. */
    data class Downloading(val fraction: Float?) : DownloadState

    /** Files downloaded, signature verification in progress. */
    data object Verifying : DownloadState

    /**
     * Files are ready to install. [versionCode] is the version of the downloaded
     * package: if a newer one appears in the catalog, the readiness is
     * considered stale.
     */
    data class Ready(val versionCode: Long?) : DownloadState

    /** The download failed; [message] is already display-ready. */
    data class Failed(val message: String) : DownloadState

    /** The download was cancelled by the user. */
    data object Cancelled : DownloadState
}

/** A download is happening right now: queued, downloading, or verifying the signature. */
val DownloadState.isBusy: Boolean
    get() = this is DownloadState.Queued ||
        this is DownloadState.Downloading ||
        this is DownloadState.Verifying

/** A download can be started or retried. */
val DownloadState.isActionable: Boolean
    get() = this is DownloadState.Idle || this is DownloadState.Failed
