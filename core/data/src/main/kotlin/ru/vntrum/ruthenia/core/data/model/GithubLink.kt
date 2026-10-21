/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.model

import com.vntrum.ruthenia.core.data.db.GithubAppEntity
import com.vntrum.ruthenia.core.github.GithubVersion

/** GitHub repository linked to an installed package. */
data class GithubLink(
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

    /** Update status for an app whose installed version name is [installedVersionName]. */
    fun status(installedVersionName: String?): UpdateStatus {
        if (latestAssetUrl.isNullOrBlank()) return UpdateStatus.UNKNOWN

        // Release ids are monotonic; never offer a downgrade after a manual install.
        if (installedReleaseId != null && latestReleaseId != null &&
            latestReleaseId < installedReleaseId
        ) {
            return UpdateStatus.UP_TO_DATE
        }

        val installedNormalized = installedVersionName?.let(GithubVersion::normalize)
        if (installedNormalized != null && latestVersionName != null &&
            GithubVersion.looksComparable(latestVersionName) &&
            GithubVersion.looksComparable(installedNormalized)
        ) {
            val comparison = VersionComparator.compareNames(latestVersionName, installedNormalized)
            return if (comparison > 0) UpdateStatus.UPDATE_AVAILABLE else UpdateStatus.UP_TO_DATE
        }

        if (installedReleaseId != null && latestReleaseId != null) {
            return if (latestReleaseId > installedReleaseId) {
                UpdateStatus.UPDATE_AVAILABLE
            } else {
                UpdateStatus.UP_TO_DATE
            }
        }

        return UpdateStatus.UNKNOWN
    }

    /** Key used by the hidden-updates setting. */
    val updateKey: String?
        get() = latestReleaseId?.toString() ?: latestVersionName

    /** SHA-256 without GitHub's `sha256:` prefix, when present. */
    val latestAssetSha256: String?
        get() = latestAssetDigest
            ?.removePrefix("sha256:")
            ?.takeIf { it.length == 64 }
}

internal fun GithubAppEntity.toLink(): GithubLink = GithubLink(
    owner = owner,
    repo = repo,
    repoId = repoId,
    displayName = displayName,
    avatarUrl = avatarUrl,
    assetName = assetName,
    includePrereleases = includePrereleases,
    etag = etag,
    installedReleaseId = installedReleaseId,
    installedTag = installedTag,
    latestReleaseId = latestReleaseId,
    latestTag = latestTag,
    latestVersionName = latestVersionName,
    latestPublishedAt = latestPublishedAt,
    latestAssetUrl = latestAssetUrl,
    latestAssetSize = latestAssetSize,
    latestAssetDigest = latestAssetDigest,
    lastCheckedAt = lastCheckedAt,
)
