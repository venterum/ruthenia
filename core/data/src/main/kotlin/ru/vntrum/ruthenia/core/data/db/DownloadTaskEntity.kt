/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Durable download-manager ledger row.
 *
 * WorkManager remains the executor and owns live progress, but the ledger keeps
 * metadata, terminal failures, pause state and completed history alive across
 * navigation, process death and WorkManager pruning. One row per package:
 * starting a new download replaces the previous record.
 */
@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val packageName: String,
    val appId: Long?,
    val name: String?,
    val iconUrl: String?,
    val publisher: String?,
    val versionCode: Long?,
    val versionName: String?,
    /** RUSTORE or GITHUB. */
    @ColumnInfo(defaultValue = "RUSTORE")
    val sourceType: String = "RUSTORE",
    val sourceUrl: String? = null,
    val sourceSize: Long? = null,
    val sourceSha256: String? = null,
    val totalBytes: Long?,
    /** QUEUED, DOWNLOADING, VERIFYING, READY, PAUSED, FAILED, COMPLETED. */
    val stage: String,
    /** WorkManager request id; null for completed legacy rows. */
    val workId: String?,
    val failureCode: String?,
    val failureDetail: String?,
    /** IDLE, INSTALLING, AWAITING_USER, FAILED. */
    val installStage: String,
    val installFailureCode: String?,
    val installFailureDetail: String?,
    val attemptCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
)
