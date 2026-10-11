/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.vntrum.ruthenia.core.data.db.CatalogAppDao
import com.vntrum.ruthenia.core.data.db.HomeFeedDao
import com.vntrum.ruthenia.core.data.db.InstalledAppDao
import com.vntrum.ruthenia.core.data.db.RutheniaDatabase
import com.vntrum.ruthenia.core.data.model.AppSource

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {

    private const val SETTINGS_DATASTORE = "settings"

    @Provides
    @Singleton
    internal fun provideDatabase(@ApplicationContext context: Context): RutheniaDatabase =
        Room.databaseBuilder(context, RutheniaDatabase::class.java, RutheniaDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    internal fun provideInstalledAppDao(database: RutheniaDatabase): InstalledAppDao =
        database.installedAppDao()

    @Provides
    internal fun provideCatalogAppDao(database: RutheniaDatabase): CatalogAppDao =
        database.catalogAppDao()

    @Provides
    internal fun provideHomeFeedDao(database: RutheniaDatabase): HomeFeedDao =
        database.homeFeedDao()

    @Provides
    @Singleton
    internal fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(SETTINGS_DATASTORE) },
    )

    /**
     * Adds the install source and the "found in the RuStore catalog" flag.
     *
     * Old rows could only have come from Ruthenia Store, so we set the source to
     * [AppSource.RUSTORE] and `inCatalog = 1`.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN source TEXT NOT NULL DEFAULT 'RUSTORE'",
            )
            db.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN inCatalog INTEGER NOT NULL DEFAULT 1",
            )
        }
    }

    /**
     * Adds rating/downloads to the catalog cache and the home feed table.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE catalog_apps ADD COLUMN averageUserRating REAL")
            db.execSQL("ALTER TABLE catalog_apps ADD COLUMN totalRatings INTEGER")
            db.execSQL("ALTER TABLE catalog_apps ADD COLUMN downloadsText TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `home_entries` (
                    `sectionId` TEXT NOT NULL,
                    `sectionTitle` TEXT NOT NULL,
                    `sectionPosition` INTEGER NOT NULL,
                    `itemPosition` INTEGER NOT NULL,
                    `packageName` TEXT NOT NULL,
                    `appId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `shortDescription` TEXT,
                    `iconUrl` TEXT,
                    `versionCode` INTEGER,
                    `minSdkVersion` INTEGER,
                    `averageUserRating` REAL,
                    `totalRatings` INTEGER,
                    `downloadsText` TEXT,
                    `cachedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`sectionId`, `packageName`)
                )
                """.trimIndent(),
            )
        }
    }
}
