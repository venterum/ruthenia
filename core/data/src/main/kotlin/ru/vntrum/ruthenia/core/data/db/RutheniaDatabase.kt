/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.vntrum.ruthenia.core.data.model.AppSource

@Database(
    entities = [
        InstalledAppEntity::class,
        CatalogAppEntity::class,
        HomeEntryEntity::class,
        UnresolvedAppEntity::class,
        DownloadTaskEntity::class,
        GithubAppEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(AppSourceConverters::class)
abstract class RutheniaDatabase : RoomDatabase() {

    abstract fun installedAppDao(): InstalledAppDao

    abstract fun catalogAppDao(): CatalogAppDao

    abstract fun homeFeedDao(): HomeFeedDao

    abstract fun unresolvedAppDao(): UnresolvedAppDao

    abstract fun downloadTaskDao(): DownloadTaskDao

    abstract fun githubAppDao(): GithubAppDao

    companion object {
        const val NAME = "ruthenia.db"
    }
}

/** Room stores the source as a string — that way the schema can be read and migrated by hand. */
class AppSourceConverters {

    @TypeConverter
    fun fromAppSource(value: AppSource): String = value.name

    @TypeConverter
    fun toAppSource(value: String?): AppSource = value
        ?.let { name -> AppSource.entries.firstOrNull { it.name == name } }
        ?: AppSource.UNKNOWN
}
