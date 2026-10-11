/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface HomeFeedDao {

    @Query("SELECT * FROM home_entries WHERE cachedAt >= :threshold ORDER BY sectionPosition, itemPosition")
    suspend fun entriesSince(threshold: Long): List<HomeEntryEntity>

    /**
     * Clears the app feed while keeping the games feed, and vice versa.
     * The prefixes match [com.vntrum.ruthenia.core.data.repository.HomeConfig.GAMES_SECTION_PREFIX]:
     * `game_` is matched literally (ESCAPE), so the `games` category of the app
     * feed is not touched.
     */
    @Query("DELETE FROM home_entries WHERE sectionId NOT LIKE 'game\\_%' ESCAPE '\\'")
    suspend fun clearAppSections()

    @Query("DELETE FROM home_entries WHERE sectionId LIKE 'game\\_%' ESCAPE '\\'")
    suspend fun clearGameSections()

    @Upsert
    suspend fun upsertAll(entries: List<HomeEntryEntity>)
}
