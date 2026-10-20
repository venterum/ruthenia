/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import com.vntrum.ruthenia.core.installer.InstallFailureCode

/**
 * Stable, persistable reason why a task needs the user's attention.
 *
 * The UI translates these codes through [DownloadFailureMessages]; raw system
 * messages are kept only in [TaskFailure.detail].
 */
enum class TaskFailureCode {
    NETWORK,
    OFFLINE,
    WAITING_WIFI,
    RATE_LIMITED,
    SERVER,
    SESSION,
    HTTP,
    APP_UNAVAILABLE,
    NO_FILES,
    INVALID_RESPONSE,
    INVALID_APK,
    CORRUPTED,
    SIZE_MISMATCH,
    SIGNATURE_MISMATCH,
    PACKAGE_MISMATCH,
    VERSION_MISMATCH,
    INCOMPATIBLE,
    STORAGE_FULL,
    FILES_LOST,
    PERMISSION_REQUIRED,
    INSTALL_BLOCKED,
    INSTALL_CONFLICT,
    INSTALL_CANCELLED,
    INSTALL_INVALID,
    INSTALL_INCOMPATIBLE,
    INSTALL_TIMEOUT,
    INTERRUPTED,
    UNKNOWN,
}

/** A persisted failure. [detail] must never contain signed download URLs. */
data class TaskFailure(
    val code: TaskFailureCode,
    val detail: String? = null,
)

/** Why a queued task is not running yet. */
enum class QueueReason {
    QUEUED,
    WAITING_NETWORK,
    WAITING_WIFI,
    RETRYING,
}

/**
 * State of an app's background download — shared by updates and first-time
 * installs. Completed records are kept as history.
 */
sealed interface DownloadState {

    /** No download is associated with this app (used by update rows). */
    data object Idle : DownloadState

    /** Work is queued (waiting for the network, Wi-Fi or a retry delay). */
    data class Queued(val reason: QueueReason = QueueReason.QUEUED) : DownloadState

    /** Downloading; [fraction] is the 0..1 share or `null` if the size is unknown. */
    data class Downloading(
        val fraction: Float?,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long? = null,
    ) : DownloadState

    /** Files downloaded, verification in progress. */
    data object Verifying : DownloadState

    /** Files are ready to install. */
    data class Ready(val versionCode: Long?) : DownloadState

    /** The user stopped the download; partial files are kept for resume. */
    data object Paused : DownloadState

    /** The download failed; the user can retry, redownload or remove it. */
    data class Failed(val failure: TaskFailure) : DownloadState

    /** The app was installed; the record is shown in the completed history. */
    data class Completed(val versionCode: Long?, val completedAt: Long) : DownloadState
}

/** State of the installation step, independent of the download state. */
sealed interface InstallState {

    data object Idle : InstallState

    data object Installing : InstallState

    /** The system installer dialog is open and we are waiting for the user. */
    data object AwaitingUser : InstallState

    data class Failed(val failure: TaskFailure) : InstallState
}

/** A download is happening right now: queued, downloading, verifying or installing. */
val DownloadState.isBusy: Boolean
    get() = this is DownloadState.Queued ||
        this is DownloadState.Downloading ||
        this is DownloadState.Verifying

/** A download can be started or retried. */
val DownloadState.isActionable: Boolean
    get() = this is DownloadState.Failed

/** Whether this failure can be retried without downloading the files again. */
val TaskFailure.isRetryable: Boolean
    get() = code in RETRYABLE_CODES

/** Whether the user should ask for a fresh download. */
val TaskFailure.needsRedownload: Boolean
    get() = code in REDOWNLOAD_CODES

/** Maps an installer failure into the shared manager taxonomy. */
fun InstallFailureCode.toTaskFailureCode(): TaskFailureCode = when (this) {
    InstallFailureCode.USER_CANCELLED -> TaskFailureCode.INSTALL_CANCELLED
    InstallFailureCode.PERMISSION_REQUIRED -> TaskFailureCode.PERMISSION_REQUIRED
    InstallFailureCode.BLOCKED -> TaskFailureCode.INSTALL_BLOCKED
    InstallFailureCode.CONFLICT -> TaskFailureCode.INSTALL_CONFLICT
    InstallFailureCode.INVALID_APK -> TaskFailureCode.INSTALL_INVALID
    InstallFailureCode.STORAGE_FULL -> TaskFailureCode.STORAGE_FULL
    InstallFailureCode.INCOMPATIBLE -> TaskFailureCode.INSTALL_INCOMPATIBLE
    InstallFailureCode.TIMEOUT -> TaskFailureCode.INSTALL_TIMEOUT
    InstallFailureCode.SHIZUKU_UNAVAILABLE -> TaskFailureCode.INSTALL_BLOCKED
    InstallFailureCode.INTERRUPTED -> TaskFailureCode.INTERRUPTED
    InstallFailureCode.SESSION_FAILED -> TaskFailureCode.INSTALL_BLOCKED
    InstallFailureCode.UNKNOWN -> TaskFailureCode.UNKNOWN
}

private val RETRYABLE_CODES = setOf(
    TaskFailureCode.NETWORK,
    TaskFailureCode.OFFLINE,
    TaskFailureCode.WAITING_WIFI,
    TaskFailureCode.RATE_LIMITED,
    TaskFailureCode.SERVER,
    TaskFailureCode.SESSION,
    TaskFailureCode.HTTP,
    TaskFailureCode.INSTALL_TIMEOUT,
    TaskFailureCode.INTERRUPTED,
)

private val REDOWNLOAD_CODES = setOf(
    TaskFailureCode.APP_UNAVAILABLE,
    TaskFailureCode.NO_FILES,
    TaskFailureCode.INVALID_RESPONSE,
    TaskFailureCode.INVALID_APK,
    TaskFailureCode.CORRUPTED,
    TaskFailureCode.SIZE_MISMATCH,
    TaskFailureCode.PACKAGE_MISMATCH,
    TaskFailureCode.VERSION_MISMATCH,
    TaskFailureCode.FILES_LOST,
    TaskFailureCode.INSTALL_INVALID,
)
