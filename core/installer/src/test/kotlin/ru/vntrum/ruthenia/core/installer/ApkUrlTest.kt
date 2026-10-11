/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer

import org.junit.Assert.assertEquals
import org.junit.Test

class ApkUrlTest {

    @Test
    fun `zip container url is rewritten to the apk beside it`() {
        assertEquals(
            "https://static-m.rustore.ru/2026/9/10/77/91203661-f108-45f3-942c-0fe7a77a659b.apk",
            "https://static-m.rustore.ru/2026/9/10/77/91203661-f108-45f3-942c-0fe7a77a659b.zip"
                .toApkUrl(),
        )
    }

    @Test
    fun `non zip url is left untouched`() {
        val url = "https://static-m.rustore.ru/path/base.apk"
        assertEquals(url, url.toApkUrl())
    }

    @Test
    fun `fraction is null when total size is unknown`() {
        assertEquals(null, DownloadProgress(downloadedBytes = 100, totalBytes = 0).fraction)
    }

    @Test
    fun `fraction is clamped between zero and one`() {
        assertEquals(
            0.5f,
            DownloadProgress(downloadedBytes = 50, totalBytes = 100).fraction!!,
            0.0001f,
        )
        assertEquals(
            1f,
            DownloadProgress(downloadedBytes = 500, totalBytes = 100).fraction!!,
            0.0001f,
        )
    }
}
