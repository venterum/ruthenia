/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.vntrum.ruthenia.core.data.settings.AppSettings

class IgnoredUpdatesTest {

    @Test
    fun `package wide ignore hides any version`() {
        val app = app(latestVersionCode = 10L)

        assertTrue(app.isUpdateIgnored(setOf("com.example")))
    }

    @Test
    fun `version specific ignore hides only that version`() {
        val app = app(latestVersionCode = 10L)

        assertTrue(app.isUpdateIgnored(setOf("com.example@10")))
        assertFalse(app.isUpdateIgnored(setOf("com.example@9")))
    }

    @Test
    fun `a newer catalog version becomes visible again`() {
        val app = app(latestVersionCode = 11L)

        assertFalse(app.isUpdateIgnored(setOf("com.example@10")))
    }

    @Test
    fun `unrelated package is never ignored`() {
        val app = app(latestVersionCode = 10L)

        assertFalse(app.isUpdateIgnored(setOf("com.other@10")))
        assertFalse(app.isUpdateIgnored(emptySet()))
    }

    @Test
    fun `ignored update key encodes version when known`() {
        assertEquals("com.example", AppSettings.ignoredUpdateKey("com.example", null))
        assertEquals("com.example@10", AppSettings.ignoredUpdateKey("com.example", 10L))
    }

    private fun app(latestVersionCode: Long): InstalledApp = InstalledApp(
        packageName = "com.example",
        appId = 1L,
        name = "Example",
        iconUrl = null,
        publisher = null,
        installedVersionCode = 1L,
        installedVersionName = "1.0",
        latestVersionCode = latestVersionCode,
        latestVersionName = "2.0",
        whatsNew = null,
        lastCheckedAt = null,
        lastInstalledAt = null,
        source = AppSource.RUSTORE,
        inCatalog = true,
    )
}