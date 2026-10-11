// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenRankTest {

    @Test
    fun `counts map to the expected rank step`() {
        assertEquals(TokenRank.GUEST, TokenRank.forCount(0))
        assertEquals(TokenRank.APPRENTICE, TokenRank.forCount(1))
        assertEquals(TokenRank.APPRENTICE, TokenRank.forCount(2))
        assertEquals(TokenRank.JOURNEYMAN, TokenRank.forCount(3))
        assertEquals(TokenRank.JOURNEYMAN, TokenRank.forCount(5))
        assertEquals(TokenRank.MASTER, TokenRank.forCount(6))
        assertEquals(TokenRank.ARCHITECT, TokenRank.forCount(11))
        assertEquals(TokenRank.KEEPER, TokenRank.forCount(21))
        assertEquals(TokenRank.SORCERER, TokenRank.forCount(41))
        assertEquals(TokenRank.ARCHIVIST, TokenRank.forCount(76))
        assertEquals(TokenRank.ARCHIVIST, TokenRank.forCount(10_000))
    }

    @Test
    fun `thresholds are strictly increasing`() {
        TokenRank.entries.zipWithNext().forEach { (lower, upper) ->
            assertTrue("${lower.name} -> ${upper.name}", upper.minApps > lower.minApps)
        }
    }

    @Test
    fun `top rank has no next and its progress is full`() {
        assertNull(TokenRank.next(TokenRank.ARCHIVIST))
        assertNull(TokenRank.appsToNext(TokenRank.ARCHIVIST, 100))
        assertEquals(1f, TokenRank.progress(TokenRank.ARCHIVIST, 100), 0f)
    }

    @Test
    fun `progress and remaining apps point at the next threshold`() {
        assertEquals(0f, TokenRank.progress(TokenRank.MASTER, 6), 0f)
        assertEquals(0.4f, TokenRank.progress(TokenRank.MASTER, 8), 0.001f)
        assertEquals(0.8f, TokenRank.progress(TokenRank.MASTER, 10), 0.001f)
        assertEquals(5, TokenRank.appsToNext(TokenRank.MASTER, 6))
        assertEquals(1, TokenRank.appsToNext(TokenRank.MASTER, 10))
    }
}
