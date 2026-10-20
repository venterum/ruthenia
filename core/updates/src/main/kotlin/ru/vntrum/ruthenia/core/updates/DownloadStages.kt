/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

/** Persisted download stages. Kept as strings so the Room schema stays readable. */
object DownloadStage {
    const val QUEUED = "QUEUED"
    const val DOWNLOADING = "DOWNLOADING"
    const val VERIFYING = "VERIFYING"
    const val READY = "READY"
    const val PAUSED = "PAUSED"
    const val FAILED = "FAILED"
    const val COMPLETED = "COMPLETED"

    /** Internal: work is being cancelled and files must be deleted. */
    const val REMOVING = "REMOVING"
}

/** Persisted install stages. */
object InstallStage {
    const val IDLE = "IDLE"
    const val INSTALLING = "INSTALLING"
    const val AWAITING_USER = "AWAITING_USER"
    const val FAILED = "FAILED"
}
