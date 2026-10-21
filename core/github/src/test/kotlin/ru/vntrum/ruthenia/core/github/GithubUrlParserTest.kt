/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubUrlParserTest {

    @Test
    fun `parses short owner repo form`() {
        val parsed = GithubUrlParser.parse("venterum/ruthenia")
        assertEquals("venterum", parsed?.owner)
        assertEquals("ruthenia", parsed?.repo)
    }

    @Test
    fun `parses https url with trailing path`() {
        val parsed = GithubUrlParser.parse("https://github.com/venterum/ruthenia/releases/latest")
        assertEquals("venterum/ruthenia", parsed?.fullName)
    }

    @Test
    fun `accepts github url without scheme`() {
        val parsed = GithubUrlParser.parse("github.com/venterum/ruthenia")
        assertEquals("venterum/ruthenia", parsed?.fullName)
    }

    @Test
    fun `strips git suffix`() {
        val parsed = GithubUrlParser.parse("https://github.com/owner/repo.git")
        assertEquals("repo", parsed?.repo)
    }

    @Test
    fun `rejects non github hosts and insecure urls`() {
        assertNull(GithubUrlParser.parse("https://gitlab.com/owner/repo"))
        assertNull(GithubUrlParser.parse("http://github.com/owner/repo"))
        assertNull(GithubUrlParser.parse("https://user:pass@github.com/owner/repo"))
        assertNull(GithubUrlParser.parse("owner"))
        assertNull(GithubUrlParser.parse("owner/repo/extra"))
    }
}
