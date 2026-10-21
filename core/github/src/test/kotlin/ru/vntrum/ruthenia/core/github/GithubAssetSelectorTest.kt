/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubAssetSelectorTest {

    private fun asset(name: String, size: Long = 100) = GithubAsset(
        id = name.hashCode().toLong(),
        name = name,
        size = size,
        contentType = "application/vnd.android.package-archive",
        downloadUrl = "https://github.com/o/r/releases/download/v1/$name",
        digest = null,
    )

    private fun release(vararg assets: GithubAsset) = GithubRelease(
        id = 1,
        tagName = "v1.0.0",
        name = "1.0.0",
        body = null,
        prerelease = false,
        publishedAt = null,
        assets = assets.toList(),
    )

    @Test
    fun `prefers the remembered asset name`() {
        val result = GithubAssetSelector.select(
            release(asset("app-arm64.apk", 300), asset("app-universal.apk", 500)),
            "app-arm64.apk",
        )
        assertEquals("app-arm64.apk", result?.name)
    }

    @Test
    fun `falls back to universal then largest apk`() {
        assertEquals(
            "app-universal.apk",
            GithubAssetSelector.select(
                release(asset("app-arm64.apk", 800), asset("app-universal.apk", 500)),
                null,
            )?.name,
        )
        assertEquals(
            "app-arm64.apk",
            GithubAssetSelector.select(
                release(asset("app-arm64.apk", 800), asset("app-x86.apk", 500)),
                null,
            )?.name,
        )
    }

    @Test
    fun `ignores non apk assets`() {
        assertNull(GithubAssetSelector.select(release(asset("app.aab")), null))
        assertNull(GithubAssetSelector.select(release(asset("notes.txt")), null))
    }
}
