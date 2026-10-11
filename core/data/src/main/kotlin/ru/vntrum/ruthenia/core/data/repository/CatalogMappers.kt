/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.repository

import com.vntrum.ruthenia.core.data.db.CatalogAppEntity
import com.vntrum.ruthenia.core.network.CatalogApp

internal fun CatalogApp.toEntity(cachedAt: Long): CatalogAppEntity = CatalogAppEntity(
    packageName = packageName,
    appId = appId,
    name = name,
    shortDescription = shortDescription,
    iconUrl = iconUrl,
    versionCode = versionCode,
    averageUserRating = averageUserRating,
    totalRatings = totalRatings,
    downloadsText = downloadsText,
    cachedAt = cachedAt,
)

internal fun CatalogAppEntity.toModel(): CatalogApp = CatalogApp(
    appId = appId,
    packageName = packageName,
    name = name,
    shortDescription = shortDescription,
    iconUrl = iconUrl,
    versionCode = versionCode,
    minSdkVersion = null,
    averageUserRating = averageUserRating,
    totalRatings = totalRatings,
    downloadsText = downloadsText,
)
