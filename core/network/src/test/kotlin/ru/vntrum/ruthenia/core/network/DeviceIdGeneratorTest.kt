/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdGeneratorTest {

    @Test
    fun `suffix matches the reference implementation`() {
        // Reference: Obtainium lib/app_sources/rustore.dart, deviceIdSuffix().
        // For Google / Pixel 8 Pro / husky it yields -1057450190.
        assertEquals(
            "-1057450190",
            DeviceIdGenerator.suffixFor("Google", "Pixel 8 Pro", "husky"),
        )
    }

    @Test
    fun `generated id has the expected shape`() {
        val id = DeviceIdGenerator.next()
        val parts = id.split("-", limit = 2)

        assertEquals("id must contain a dash", 2, parts.size)
        assertEquals("androidId must be 8 hex bytes", 16, parts[0].length)
        assertTrue(parts[0].all { it in "0123456789abcdef" })
        // The suffix is negative, so the id contains two consecutive dashes.
        assertEquals("-1057450190", parts[1])
    }

    @Test
    fun `generated ids are unique and not persisted`() {
        val ids = List(200) { DeviceIdGenerator.next() }
        assertEquals(200, ids.toSet().size)
    }
}
