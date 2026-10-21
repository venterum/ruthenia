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
import com.vntrum.ruthenia.core.data.db.DownloadTaskDao
import com.vntrum.ruthenia.core.data.db.GithubAppDao
import com.vntrum.ruthenia.core.data.db.HomeFeedDao
import com.vntrum.ruthenia.core.data.db.InstalledAppDao
import com.vntrum.ruthenia.core.data.db.RutheniaDatabase
import com.vntrum.ruthenia.core.data.db.UnresolvedAppDao
import com.vntrum.ruthenia.core.data.model.AppSource

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {

    private const val SETTINGS_DATASTORE = "settings"

    @Provides
    @Singleton
    internal fun provideDatabase(@ApplicationContext context: Context): RutheniaDatabase =
        Room.databaseBuilder(context, RutheniaDatabase::class.java, RutheniaDatabase.NAME)
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            )
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
    internal fun provideUnresolvedAppDao(database: RutheniaDatabase): UnresolvedAppDao =
        database.unresolvedAppDao()

    @Provides
    internal fun provideDownloadTaskDao(database: RutheniaDatabase): DownloadTaskDao =
        database.downloadTaskDao()

    @Provides
    internal fun provideGithubAppDao(database: RutheniaDatabase): GithubAppDao =
        database.githubAppDao()

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

    /**
     * Adds the durable download-manager ledger. WorkManager is still the
     * executor, but failures, pause state and completed history must survive
     * process death and WorkManager pruning.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `download_tasks` (
                    `packageName` TEXT NOT NULL,
                    `appId` INTEGER,
                    `name` TEXT,
                    `iconUrl` TEXT,
                    `publisher` TEXT,
                    `versionCode` INTEGER,
                    `versionName` TEXT,
                    `totalBytes` INTEGER,
                    `stage` TEXT NOT NULL,
                    `workId` TEXT,
                    `failureCode` TEXT,
                    `failureDetail` TEXT,
                    `installStage` TEXT NOT NULL,
                    `installFailureCode` TEXT,
                    `installFailureDetail` TEXT,
                    `attemptCount` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    PRIMARY KEY(`packageName`)
                )
                """.trimIndent(),
            )
        }
    }

    /**
     * Adds GitHub release sources and the direct-download metadata needed by
     * the download worker. Existing rows remain RuStore downloads.
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `github_apps` (
                    `packageName` TEXT NOT NULL,
                    `owner` TEXT NOT NULL,
                    `repo` TEXT NOT NULL,
                    `repoId` INTEGER,
                    `displayName` TEXT,
                    `avatarUrl` TEXT,
                    `assetName` TEXT,
                    `includePrereleases` INTEGER NOT NULL,
                    `etag` TEXT,
                    `installedReleaseId` INTEGER,
                    `installedTag` TEXT,
                    `latestReleaseId` INTEGER,
                    `latestTag` TEXT,
                    `latestVersionName` TEXT,
                    `latestPublishedAt` INTEGER,
                    `latestAssetUrl` TEXT,
                    `latestAssetSize` INTEGER,
                    `latestAssetDigest` TEXT,
                    `lastCheckedAt` INTEGER,
                    PRIMARY KEY(`packageName`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "ALTER TABLE download_tasks ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'RUSTORE'",
            )
            db.execSQL("ALTER TABLE download_tasks ADD COLUMN sourceUrl TEXT")
            db.execSQL("ALTER TABLE download_tasks ADD COLUMN sourceSize INTEGER")
            db.execSQL("ALTER TABLE download_tasks ADD COLUMN sourceSha256 TEXT")
        }
    }

    /**
     * Adds the negative cache of the app feed: curated packages the catalog
     * could not resolve. It keeps every feed access from retrying them and
     * blocking collection opens on the network.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `unresolved_apps` (
                    `packageName` TEXT NOT NULL,
                    `attemptedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`packageName`)
                )
                """.trimIndent(),
            )
        }
    }
}
