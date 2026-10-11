/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants of the curated feed content. The test does not go online — it just
 * prevents accidentally duplicating sections or slipping in an empty query.
 */
class HomeConfigTest {

    @Test
    fun `category ids are unique and queries are non-blank`() {
        val ids = HomeConfig.categories.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        HomeConfig.categories.forEach { category ->
            assertTrue(category.title.isNotBlank())
            assertTrue(category.query.isNotBlank())
        }
    }

    @Test
    fun `recommended entries are unique and look like package names`() {
        val packages = HomeConfig.recommended.map { it.packageName }
        assertEquals(packages.size, packages.toSet().size)
        HomeConfig.recommended.forEach { app ->
            assertTrue(app.query.isNotBlank())
            assertTrue(app.packageName.contains('.'))
        }
    }

    @Test
    fun `section ids do not collide`() {
        val ids = HomeConfig.categories.map { it.id } + HomeConfig.RECOMMENDED_SECTION_ID
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `game genre ids are unique, prefixed, and non-blank`() {
        val ids = HomeConfig.gameGenres.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        HomeConfig.gameGenres.forEach { genre ->
            assertTrue(genre.id.startsWith(HomeConfig.GAMES_SECTION_PREFIX))
            assertTrue(genre.title.isNotBlank())
            assertTrue(genre.query.isNotBlank())
            assertTrue(HomeConfig.isGameSection(genre.id))
        }
    }

    @Test
    fun `game sections do not collide with app sections`() {
        val appIds = HomeConfig.categories.map { it.id } + HomeConfig.RECOMMENDED_SECTION_ID
        val gameIds = HomeConfig.gameGenres.map { it.id } + HomeConfig.GAMES_POPULAR_SECTION_ID

        // "games" is an app feed category and must not match the game prefix.
        assertTrue(appIds.none(HomeConfig::isGameSection))
        assertTrue(HomeConfig.isGameSection(HomeConfig.GAMES_POPULAR_SECTION_ID))
        assertEquals((appIds + gameIds).size, (appIds + gameIds).toSet().size)
    }
}
