/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A snapshot of the home feed: one row per app in a section.
 *
 * The app data is duplicated here so the feed can be read with a single query
 * without a join and survives an app restart offline.
 */
@Entity(tableName = "home_entries", primaryKeys = ["sectionId", "packageName"])
data class HomeEntryEntity(
    val sectionId: String,
    val sectionTitle: String,
    val sectionPosition: Int,
    val itemPosition: Int,
    val packageName: String,
    val appId: Long,
    val name: String,
    val shortDescription: String?,
    val iconUrl: String?,
    val versionCode: Long?,
    val minSdkVersion: Int?,
    val averageUserRating: Double?,
    val totalRatings: Long?,
    val downloadsText: String?,
    val cachedAt: Long,
)
