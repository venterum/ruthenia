/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/** Storage for the negative cache of the curated app feed. */
@Dao
interface UnresolvedAppDao {

    @Query("SELECT * FROM unresolved_apps")
    suspend fun getAll(): List<UnresolvedAppEntity>

    @Upsert
    suspend fun upsertAll(apps: List<UnresolvedAppEntity>)

    @Query("DELETE FROM unresolved_apps")
    suspend fun clear()

    /** Replaces the whole set — every feed build writes one snapshot. */
    @Transaction
    suspend fun replaceAll(apps: List<UnresolvedAppEntity>) {
        clear()
        upsertAll(apps)
    }
}
