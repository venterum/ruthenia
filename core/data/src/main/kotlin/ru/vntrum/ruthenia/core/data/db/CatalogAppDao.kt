/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogAppDao {

    @Query("SELECT * FROM catalog_apps ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CatalogAppEntity>>

    @Query("SELECT * FROM catalog_apps WHERE packageName = :packageName")
    suspend fun get(packageName: String): CatalogAppEntity?

    @Query("SELECT * FROM catalog_apps WHERE packageName IN (:packageNames)")
    suspend fun getByPackages(packageNames: List<String>): List<CatalogAppEntity>

    @Upsert
    suspend fun upsertAll(apps: List<CatalogAppEntity>)

    @Query("DELETE FROM catalog_apps WHERE cachedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
