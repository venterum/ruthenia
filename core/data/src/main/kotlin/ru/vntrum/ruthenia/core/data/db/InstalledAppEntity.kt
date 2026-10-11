/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vntrum.ruthenia.core.data.model.AppSource

/**
 * An app in the Ruthenia Store library.
 *
 * Only what the "up to date / update available" list needs is stored:
 * identifiers, versions, install source and card metadata. Nothing is sent
 * outside.
 *
 * [inCatalog] shows whether the app was found in the RuStore catalog on import:
 * an update cannot be offered for missing ones, but they stay in the list.
 */
@Entity(tableName = "installed_apps")
data class InstalledAppEntity(
    @PrimaryKey val packageName: String,
    val appId: Long?,
    val name: String?,
    val iconUrl: String?,
    val publisher: String?,
    val installedVersionCode: Long?,
    val installedVersionName: String?,
    val latestVersionCode: Long?,
    val latestVersionName: String?,
    val whatsNew: String?,
    val lastCheckedAt: Long?,
    val lastInstalledAt: Long?,
    val source: AppSource = AppSource.UNKNOWN,
    val inCatalog: Boolean = false,
)
