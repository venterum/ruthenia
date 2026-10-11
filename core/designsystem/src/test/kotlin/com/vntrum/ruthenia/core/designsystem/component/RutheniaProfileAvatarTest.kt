// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The device seal must be stable per seed and stay inside its ranges. */
class RutheniaProfileAvatarTest {

    @Test
    fun `seal index is stable for the same seed`() {
        assertEquals(profileSealIndex("abc"), profileSealIndex("abc"))
        assertEquals(profileSealRotation("abc"), profileSealRotation("abc"), 0f)
    }

    @Test
    fun `seal index stays within the variant range`() {
        // Includes negative hash codes (e.g. short/edge strings) to prove `.mod`
        // never returns a negative index.
        listOf("", "a", "device-1", "device-2", "\uFFFF\u0000", "install").forEach { seed ->
            assertTrue(seed, profileSealIndex(seed) in 0 until PROFILE_SEAL_VARIANTS)
        }
    }

    @Test
    fun `seal rotation stays within a full turn`() {
        listOf("", "a", "device-1", "\uFFFF\u0000").forEach { seed ->
            val rotation = profileSealRotation(seed)
            assertTrue(seed, rotation in 0f..359f)
        }
    }
}
