// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodenameTest {

    private val adjectives = (1..12).map { "adj$it" }
    private val nouns = (1..12).map { "noun$it" }

    @Test
    fun `codename is deterministic for the same seed`() {
        assertEquals(
            codenameFor("device-1", adjectives, nouns),
            codenameFor("device-1", adjectives, nouns),
        )
    }

    @Test
    fun `codename is assembled from the provided word lists`() {
        // Includes negative hash codes to prove the index never goes out of range.
        listOf("", "a", "device-1", "device-2", "\uFFFF\u0000", "install").forEach { seed ->
            val parts = codenameFor(seed, adjectives, nouns).split(" ")
            assertEquals(seed, 2, parts.size)
            assertTrue(seed, parts[0] in adjectives)
            assertTrue(seed, parts[1] in nouns)
        }
    }
}
