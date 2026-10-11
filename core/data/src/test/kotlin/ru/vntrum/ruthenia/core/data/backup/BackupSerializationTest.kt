/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.serialization.json.Json

class BackupSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `backup round trip preserves apps and settings`() {
        val backup = RutheniaBackup(
            exportedAt = 123L,
            apps = listOf(
                BackupApp(
                    packageName = "com.example",
                    appId = 42L,
                    name = "Example",
                    installedVersionCode = 1L,
                    latestVersionCode = 2L,
                    source = "GOOGLE_PLAY",
                    inCatalog = true,
                ),
            ),
            settings = BackupSettings(
                themeMode = "DARK",
                navBarStyle = "NORMAL",
                appIconShape = "SQUARE",
                gamesEnabled = false,
                updateCheckIntervalHours = 12,
                ignoredUpdates = setOf("com.example@2", "com.other"),
            ),
        )

        val decoded = json.decodeFromString<RutheniaBackup>(json.encodeToString(backup))

        assertEquals(backup, decoded)
    }

    @Test
    fun `unknown fields do not break decoding of a newer backup`() {
        val payload = """
            {"version":1,"exportedAt":0,"apps":[],"settings":null,"futureField":42}
        """.trimIndent()

        val decoded = json.decodeFromString<RutheniaBackup>(payload)

        assertEquals(0, decoded.apps.size)
        assertEquals(1, decoded.version)
    }
}