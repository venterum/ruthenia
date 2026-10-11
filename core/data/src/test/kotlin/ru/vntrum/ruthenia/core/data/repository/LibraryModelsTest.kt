/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import com.vntrum.ruthenia.core.data.model.AppSource
import com.vntrum.ruthenia.core.data.model.InstalledApp
import com.vntrum.ruthenia.core.data.model.UpdateStatus

class LibraryModelsTest {

    @Test
    fun `import progress fraction is clamped`() {
        assertEquals(0f, ImportProgress(0, 0, null).fraction)
        assertEquals(0.5f, ImportProgress(5, 10, null).fraction)
        assertEquals(1f, ImportProgress(15, 10, null).fraction)
    }

    @Test
    fun `import result counts tracked apps`() {
        val result = ImportResult(
            scanned = 10,
            added = 3,
            updated = 2,
            notInCatalog = 5,
        )
        assertEquals(5, result.totalTracked)
    }

    @Test
    fun `source is derived from installer package`() {
        assertEquals(AppSource.GOOGLE_PLAY, AppSource.fromInstaller("com.android.vending"))
        assertEquals(AppSource.RUSTORE, AppSource.fromInstaller("com.rustore.app"))
        assertEquals(AppSource.RUSTORE, AppSource.fromInstaller("ru.rustore.app"))
        assertEquals(
            AppSource.SIDELOAD,
            AppSource.fromInstaller("com.android.packageinstaller"),
        )
        assertEquals(AppSource.OTHER, AppSource.fromInstaller("com.example.store"))

        // VK Store is not RuStore, important not to confuse them.
        assertEquals(AppSource.OTHER, AppSource.fromInstaller("ru.vostok.vstore"))

        assertEquals(AppSource.UNKNOWN, AppSource.fromInstaller(null))
        assertEquals(AppSource.UNKNOWN, AppSource.fromInstaller("  "))
    }

    @Test
    fun `app outside catalog is never treated as updatable`() {
        val app = InstalledApp(
            packageName = "com.example",
            appId = null,
            name = "Example",
            iconUrl = null,
            publisher = null,
            installedVersionCode = 10,
            installedVersionName = "1.0",
            latestVersionCode = 99,
            latestVersionName = "9.9",
            whatsNew = null,
            lastCheckedAt = null,
            lastInstalledAt = null,
            source = AppSource.GOOGLE_PLAY,
            inCatalog = false,
        )

        assertEquals(UpdateStatus.UNKNOWN, app.status)
    }

    @Test
    fun `app inside catalog compares versions normally`() {
        val app = InstalledApp(
            packageName = "com.example",
            appId = 1,
            name = "Example",
            iconUrl = null,
            publisher = null,
            installedVersionCode = 10,
            installedVersionName = "1.0",
            latestVersionCode = 11,
            latestVersionName = "1.1",
            whatsNew = null,
            lastCheckedAt = null,
            lastInstalledAt = null,
            source = AppSource.GOOGLE_PLAY,
            inCatalog = true,
        )

        assertEquals(UpdateStatus.UPDATE_AVAILABLE, app.status)
    }

    @Test
    fun `installer sources are labelled without guessing`() {
        // VK Store must never be treated as RuStore under any circumstances.
        assertEquals(AppSource.OTHER, AppSource.fromInstaller("ru.vostok.vstore"))
        assertEquals(AppSource.GOOGLE_PLAY, AppSource.fromInstaller("com.android.vending"))
    }
}