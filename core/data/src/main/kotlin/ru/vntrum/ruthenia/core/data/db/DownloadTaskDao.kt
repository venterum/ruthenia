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
interface DownloadTaskDao {

    @Query("SELECT * FROM download_tasks")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks")
    suspend fun getAll(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE packageName = :packageName")
    suspend fun get(packageName: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE packageName = :packageName")
    fun observe(packageName: String): Flow<DownloadTaskEntity?>

    @Upsert
    suspend fun upsert(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM download_tasks WHERE stage = 'COMPLETED'")
    suspend fun clearCompleted()

    /** Keeps the completed history bounded. */
    @Query(
        """
        DELETE FROM download_tasks
        WHERE stage = 'COMPLETED'
          AND packageName NOT IN (
              SELECT packageName FROM download_tasks
              WHERE stage = 'COMPLETED'
              ORDER BY completedAt DESC
              LIMIT :keep
          )
        """,
    )
    suspend fun trimCompleted(keep: Int)

    @Query(
        """
        DELETE FROM download_tasks
        WHERE stage = 'COMPLETED' AND completedAt < :cutoff
        """,
    )
    suspend fun deleteCompletedOlderThan(cutoff: Long)

    @Query("DELETE FROM download_tasks")
    suspend fun clear()
}
