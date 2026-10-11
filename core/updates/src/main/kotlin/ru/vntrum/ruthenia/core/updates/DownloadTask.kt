/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

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
)

/**
 * A download manager row: the app metadata along with the state of its
 * background download. Assembled from WorkManager and the request that queued
 * the download.
 */
data class DownloadTask(
    val packageName: String,
    val appId: Long?,
    val name: String?,
    val iconUrl: String?,
    val publisher: String?,
    val versionCode: Long?,
    val state: DownloadState,
) {

    /** The download is active: queued, downloading, or verifying. */
    val isActive: Boolean
        get() = state.isBusy

    /** Files are downloaded and waiting for the user to start the install. */
    val isReady: Boolean
        get() = state is DownloadState.Ready

    /** The download failed — a retry can be offered. */
    val isFailed: Boolean
        get() = state is DownloadState.Failed
}
