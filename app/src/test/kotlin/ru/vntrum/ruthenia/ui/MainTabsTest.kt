/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tab visibility: the Games tab follows the settings switch. */
class MainTabsTest {

    @Test
    fun `games tab is visible when games are enabled`() {
        assertEquals(
            listOf(MainTab.APPS, MainTab.SEARCH, MainTab.GAMES, MainTab.LIBRARY),
            visibleTabs(gamesEnabled = true),
        )
    }

    @Test
    fun `games tab is hidden when games are disabled`() {
        assertEquals(
            listOf(MainTab.APPS, MainTab.SEARCH, MainTab.LIBRARY),
            visibleTabs(gamesEnabled = false),
        )
    }
}
