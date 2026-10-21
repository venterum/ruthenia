/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A GitHub repository linked to an installed package.
 *
 * The package name is the primary key because Android identifies apps by it,
 * and it is known only after the first APK has been downloaded and inspected.
 * Latest-release fields are a cache: update checks refresh them from the API.
 */
@Entity(tableName = "github_apps")
data class GithubAppEntity(
    @PrimaryKey val packageName: String,
    val owner: String,
    val repo: String,
    val repoId: Long?,
    val displayName: String?,
    val avatarUrl: String?,
    val assetName: String?,
    val includePrereleases: Boolean,
    val etag: String?,
    val installedReleaseId: Long?,
    val installedTag: String?,
    val latestReleaseId: Long?,
    val latestTag: String?,
    val latestVersionName: String?,
    val latestPublishedAt: Long?,
    val latestAssetUrl: String?,
    val latestAssetSize: Long?,
    val latestAssetDigest: String?,
    val lastCheckedAt: Long?,
) {
    val fullName: String get() = "$owner/$repo"
}
