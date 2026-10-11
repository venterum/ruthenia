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
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledAppDao {

    @Query("SELECT * FROM installed_apps ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<InstalledAppEntity>>

    @Query("SELECT * FROM installed_apps")
    suspend fun getAll(): List<InstalledAppEntity>

    @Query("SELECT * FROM installed_apps WHERE packageName = :packageName")
    suspend fun get(packageName: String): InstalledAppEntity?

    @Query("SELECT * FROM installed_apps WHERE packageName = :packageName")
    fun observe(packageName: String): Flow<InstalledAppEntity?>

    @Upsert
    suspend fun upsert(app: InstalledAppEntity)

    @Query("DELETE FROM installed_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM installed_apps")
    suspend fun clear()

    @Upsert
    suspend fun upsertAll(apps: List<InstalledAppEntity>)

    /** Replaces the whole library — used when importing a backup. */
    @Transaction
    suspend fun replaceAll(apps: List<InstalledAppEntity>) {
        clear()
        upsertAll(apps)
    }
}
