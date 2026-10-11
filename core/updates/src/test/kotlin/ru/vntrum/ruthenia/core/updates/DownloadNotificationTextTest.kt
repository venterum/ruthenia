/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNotificationTextTest {

    private val downloading = "Скачивание…"
    private val downloaded = "Скачано %d%%"

    @Test
    fun `progress without fraction is indeterminate text`() {
        assertEquals(downloading, DownloadNotificationText.progress(null, downloading, downloaded))
    }

    @Test
    fun `progress rounds fraction to percent`() {
        assertEquals("Скачано 0%", DownloadNotificationText.progress(0f, downloading, downloaded))
        assertEquals("Скачано 42%", DownloadNotificationText.progress(0.42f, downloading, downloaded))
        assertEquals("Скачано 100%", DownloadNotificationText.progress(1f, downloading, downloaded))
    }
}
