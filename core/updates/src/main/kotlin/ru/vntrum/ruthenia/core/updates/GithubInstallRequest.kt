/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

/** Everything needed to install one APK asset from a GitHub release. */
data class GithubInstallRequest(
    val owner: String,
    val repo: String,
    val repoId: Long?,
    val displayName: String?,
    val avatarUrl: String?,
    val releaseId: Long,
    val releaseTag: String,
    val releaseVersionName: String,
    val assetName: String,
    val assetUrl: String,
    val assetSize: Long?,
    val assetSha256: String?,
    val includePrereleases: Boolean,
) {
    val fullName: String get() = "$owner/$repo"
}

/** UI state of a GitHub first install. */
sealed interface GithubInstallState {
    data object Idle : GithubInstallState

    data object Queued : GithubInstallState

    data class Running(val fraction: Float?) : GithubInstallState

    data class Ready(val packageName: String, val name: String?) : GithubInstallState

    data class Failed(val message: String?) : GithubInstallState
}
