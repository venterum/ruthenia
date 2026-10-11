/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The device token must be stable per seed and stay inside its ranges. */
class ProfileEmblemTest {

    @Test
    fun `motif index and rotation are stable for the same seed`() {
        assertEquals(tokenMotifIndex("abc"), tokenMotifIndex("abc"))
        assertEquals(tokenRotation("abc"), tokenRotation("abc"), 0f)
    }

    @Test
    fun `motif index stays within the variant range`() {
        // Includes negative hash codes (e.g. short/edge strings) to prove `.mod`
        // never returns a negative index.
        listOf("", "a", "device-1", "device-2", "\uFFFF\u0000", "install").forEach { seed ->
            assertTrue(seed, tokenMotifIndex(seed) in 0 until TOKEN_MOTIF_VARIANTS)
        }
    }

    @Test
    fun `rotation stays within a full turn`() {
        listOf("", "a", "device-1", "\uFFFF\u0000").forEach { seed ->
            assertTrue(seed, tokenRotation(seed) in 0f..359f)
        }
    }

    @Test
    fun `seeded emblem is deterministic and in range`() {
        val first = ProfileEmblem.fromSeed("device-1")
        val second = ProfileEmblem.fromSeed("device-1")

        assertEquals(first, second)
        assertTrue(first.motif in 0 until TOKEN_MOTIF_VARIANTS)
        assertEquals(TokenRing.THIN, first.ring)
    }
}
