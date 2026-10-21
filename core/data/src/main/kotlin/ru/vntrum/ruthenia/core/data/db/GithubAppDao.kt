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
interface GithubAppDao {

    @Query("SELECT * FROM github_apps ORDER BY packageName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<GithubAppEntity>>

    @Query("SELECT * FROM github_apps")
    suspend fun getAll(): List<GithubAppEntity>

    @Query("SELECT * FROM github_apps WHERE packageName = :packageName")
    suspend fun get(packageName: String): GithubAppEntity?

    @Query("SELECT * FROM github_apps WHERE packageName = :packageName")
    fun observe(packageName: String): Flow<GithubAppEntity?>

    @Upsert
    suspend fun upsert(app: GithubAppEntity)

    @Upsert
    suspend fun upsertAll(apps: List<GithubAppEntity>)

    @Query("DELETE FROM github_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM github_apps")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(apps: List<GithubAppEntity>) {
        clear()
        upsertAll(apps)
    }
}
