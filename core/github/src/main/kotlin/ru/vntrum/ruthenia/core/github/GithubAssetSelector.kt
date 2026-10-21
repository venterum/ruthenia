/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.github

/** Selection rules for APK assets inside a release. */
object GithubAssetSelector {

    /**
     * Picks an asset for an update. The exact name chosen when the source was
     * added wins. If it disappeared, prefer a universal build, then the
     * largest APK. A release with several unrelated APKs still gets a
     * deterministic fallback; the UI can ask the user to choose explicitly.
     */
    fun select(release: GithubRelease, preferredName: String?): GithubAsset? {
        val apks = release.apkAssets
        if (apks.isEmpty()) return null

        preferredName?.let { preferred ->
            apks.firstOrNull { it.name.equals(preferred, ignoreCase = true) }?.let { return it }
        }

        apks.singleOrNull()?.let { return it }

        apks.firstOrNull {
            it.name.contains("universal", ignoreCase = true) ||
                it.name.contains("all", ignoreCase = true)
        }?.let { return it }

        return apks.maxByOrNull { it.size }
    }

    fun containsPreferred(release: GithubRelease, preferredName: String?): Boolean =
        preferredName == null || release.apkAssets.any {
            it.name.equals(preferredName, ignoreCase = true)
        }
}
