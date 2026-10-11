/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Launcher-icon variants map to manifest `<activity-alias>` names, so their
 * suffixes must be unique and valid Java class-name segments.
 */
class AppIconVariantTest {

    @Test
    fun `alias suffixes are unique`() {
        val suffixes = AppIconVariant.entries.map { it.aliasSuffix }
        assertEquals(suffixes.size, suffixes.toSet().size)
    }

    @Test
    fun `alias suffixes are valid class-name segments`() {
        AppIconVariant.entries.forEach { variant ->
            assertTrue(variant.name, variant.aliasSuffix.isNotBlank())
            assertTrue(variant.name, variant.aliasSuffix.first().isUpperCase())
            assertTrue(variant.name, variant.aliasSuffix.all { it.isLetterOrDigit() })
        }
    }

    @Test
    fun `default variant is the original icon`() {
        assertEquals(AppIconVariant.ORIGINAL, AppSettings().appIconVariant)
    }
}
