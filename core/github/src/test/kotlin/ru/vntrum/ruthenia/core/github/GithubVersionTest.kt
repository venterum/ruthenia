/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubVersionTest {

    @Test
    fun `normalizes common tag forms`() {
        assertEquals("1.2.3", GithubVersion.normalize("v1.2.3"))
        assertEquals("1.2.3", GithubVersion.normalize("1.2.3"))
        assertEquals("1.2.3", GithubVersion.normalize("release-1.2.3"))
        assertEquals("1.2.3", GithubVersion.normalize("app-v1.2.3"))
        assertEquals("2026.10.21", GithubVersion.normalize("2026.10.21"))
    }

    @Test
    fun `keeps unparseable tags`() {
        assertEquals("nightly", GithubVersion.normalize("nightly"))
        assertTrue(!GithubVersion.looksComparable("nightly"))
        assertTrue(GithubVersion.looksComparable("v1.2.3"))
    }
}
