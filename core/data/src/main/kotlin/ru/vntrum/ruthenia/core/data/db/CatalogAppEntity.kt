/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Catalog cache: what the user has already found stays available offline. */
@Entity(tableName = "catalog_apps")
data class CatalogAppEntity(
    @PrimaryKey val packageName: String,
    val appId: Long,
    val name: String,
    val shortDescription: String?,
    val iconUrl: String?,
    val versionCode: Long?,
    val averageUserRating: Double? = null,
    val totalRatings: Long? = null,
    val downloadsText: String? = null,
    val cachedAt: Long,
)
