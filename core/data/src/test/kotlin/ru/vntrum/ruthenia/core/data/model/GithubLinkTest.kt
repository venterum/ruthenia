/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GithubLinkTest {

    private fun link(
        installedReleaseId: Long? = 10,
        installedTag: String? = "v1.0.0",
        latestReleaseId: Long? = 11,
        latestTag: String? = "v1.1.0",
        latestVersionName: String? = "1.1.0",
        latestAssetUrl: String? = "https://github.com/o/r/releases/download/v1.1.0/app.apk",
    ) = GithubLink(
        owner = "o",
        repo = "r",
        repoId = 1,
        displayName = "r",
        avatarUrl = null,
        assetName = "app.apk",
        includePrereleases = false,
        etag = null,
        installedReleaseId = installedReleaseId,
        installedTag = installedTag,
        latestReleaseId = latestReleaseId,
        latestTag = latestTag,
        latestVersionName = latestVersionName,
        latestPublishedAt = null,
        latestAssetUrl = latestAssetUrl,
        latestAssetSize = 1,
        latestAssetDigest = null,
        lastCheckedAt = null,
    )

    @Test
    fun `newer normalized tag is an update`() {
        assertEquals(UpdateStatus.UPDATE_AVAILABLE, link().status("1.0.0"))
    }

    @Test
    fun `same tag is up to date`() {
        assertEquals(UpdateStatus.UP_TO_DATE, link().status("1.1.0"))
    }

    @Test
    fun `older release id is never offered as an update`() {
        assertEquals(
            UpdateStatus.UP_TO_DATE,
            link(installedReleaseId = 12, latestReleaseId = 11).status("1.0.0"),
        )
    }

    @Test
    fun `missing asset means unknown status`() {
        assertEquals(UpdateStatus.UNKNOWN, link(latestAssetUrl = null).status("1.0.0"))
    }
}
