/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.backup

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.vntrum.ruthenia.core.data.db.InstalledAppDao
import com.vntrum.ruthenia.core.data.db.InstalledAppEntity
import com.vntrum.ruthenia.core.data.model.AppSource
import com.vntrum.ruthenia.core.data.settings.AppIconShape
import com.vntrum.ruthenia.core.data.settings.AppSettings
import com.vntrum.ruthenia.core.data.settings.InstallMethod
import com.vntrum.ruthenia.core.data.settings.NavBarStyle
import com.vntrum.ruthenia.core.data.settings.SettingsRepository
import com.vntrum.ruthenia.core.data.settings.ThemeMode

/**
 * App data backup: the library and user settings.
 *
 * The format is versioned: on an incompatible schema change we bump
 * [RutheniaBackup.CURRENT_VERSION] and handle old versions separately.
 * The file is plain JSON, so it can be moved to another device.
 */
@Serializable
data class RutheniaBackup(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = 0L,
    val apps: List<BackupApp> = emptyList(),
    val settings: BackupSettings? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/** A single library app in the backup. */
@Serializable
data class BackupApp(
    val packageName: String,
    val appId: Long? = null,
    val name: String? = null,
    val iconUrl: String? = null,
    val publisher: String? = null,
    val installedVersionCode: Long? = null,
    val installedVersionName: String? = null,
    val latestVersionCode: Long? = null,
    val latestVersionName: String? = null,
    val whatsNew: String? = null,
    val source: String = AppSource.UNKNOWN.name,
    val inCatalog: Boolean = false,
)

/** Settings that make sense when moving between devices. */
@Serializable
data class BackupSettings(
    val themeMode: String = ThemeMode.SYSTEM.name,
    val dynamicColor: Boolean = true,
    val navBarStyle: String = NavBarStyle.FLOATING.name,
    val appIconShape: String = AppIconShape.CIRCLE.name,
    val gamesEnabled: Boolean = true,
    val backgroundChecksEnabled: Boolean = false,
    val updateCheckIntervalHours: Int = AppSettings.DEFAULT_INTERVAL_HOURS,
    val unmeteredOnly: Boolean = false,
    val showGooglePlayApps: Boolean = false,
    val showSideloadedApps: Boolean = true,
    val verifySignatures: Boolean = true,
    val autoInstall: Boolean = false,
    val installMethod: String = InstallMethod.SYSTEM.name,
    val ignoredUpdates: Set<String> = emptySet(),
)

/** Creates and applies backups of the library and settings. */
@Singleton
class BackupManager @Inject constructor(
    private val installedAppDao: InstalledAppDao,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) {

    /** Serializes the current state into a JSON string. */
    suspend fun export(): String {
        val settings = settingsRepository.settings.first()
        val backup = RutheniaBackup(
            exportedAt = System.currentTimeMillis(),
            apps = installedAppDao.getAll().map { it.toBackupApp() },
            settings = settings.toBackupSettings(),
        )
        return json.encodeToString(backup)
    }

    /**
     * Replaces the library and settings with the data from the backup.
     *
     * @throws IllegalArgumentException if the backup version is newer than the
     * app understands.
     */
    suspend fun import(payload: String) {
        val backup = json.decodeFromString<RutheniaBackup>(payload)
        require(backup.version <= RutheniaBackup.CURRENT_VERSION) {
            "Unsupported backup version: ${backup.version}"
        }

        installedAppDao.replaceAll(backup.apps.map { it.toEntity() })
        backup.settings?.let { settingsRepository.restore(it.toAppSettings()) }
    }
}

private fun InstalledAppEntity.toBackupApp(): BackupApp = BackupApp(
    packageName = packageName,
    appId = appId,
    name = name,
    iconUrl = iconUrl,
    publisher = publisher,
    installedVersionCode = installedVersionCode,
    installedVersionName = installedVersionName,
    latestVersionCode = latestVersionCode,
    latestVersionName = latestVersionName,
    whatsNew = whatsNew,
    source = source.name,
    inCatalog = inCatalog,
)

private fun BackupApp.toEntity(): InstalledAppEntity = InstalledAppEntity(
    packageName = packageName,
    appId = appId,
    name = name,
    iconUrl = iconUrl,
    publisher = publisher,
    installedVersionCode = installedVersionCode,
    installedVersionName = installedVersionName,
    latestVersionCode = latestVersionCode,
    latestVersionName = latestVersionName,
    whatsNew = whatsNew,
    lastCheckedAt = null,
    lastInstalledAt = null,
    source = AppSource.entries.firstOrNull { it.name == source } ?: AppSource.UNKNOWN,
    inCatalog = inCatalog,
)

private fun AppSettings.toBackupSettings(): BackupSettings = BackupSettings(
    themeMode = themeMode.name,
    dynamicColor = dynamicColor,
    navBarStyle = navBarStyle.name,
    appIconShape = appIconShape.name,
    gamesEnabled = gamesEnabled,
    backgroundChecksEnabled = backgroundChecksEnabled,
    updateCheckIntervalHours = updateCheckIntervalHours,
    unmeteredOnly = unmeteredOnly,
    showGooglePlayApps = showGooglePlayApps,
    showSideloadedApps = showSideloadedApps,
    verifySignatures = verifySignatures,
    autoInstall = autoInstall,
    installMethod = installMethod.name,
    ignoredUpdates = ignoredUpdates,
)

private fun BackupSettings.toAppSettings(): AppSettings = AppSettings(
    themeMode = ThemeMode.entries.firstOrNull { it.name == themeMode } ?: ThemeMode.SYSTEM,
    dynamicColor = dynamicColor,
    navBarStyle = NavBarStyle.entries.firstOrNull { it.name == navBarStyle } ?: NavBarStyle.FLOATING,
    appIconShape = AppIconShape.entries.firstOrNull { it.name == appIconShape } ?: AppIconShape.CIRCLE,
    gamesEnabled = gamesEnabled,
    backgroundChecksEnabled = backgroundChecksEnabled,
    updateCheckIntervalHours = updateCheckIntervalHours,
    unmeteredOnly = unmeteredOnly,
    showGooglePlayApps = showGooglePlayApps,
    showSideloadedApps = showSideloadedApps,
    verifySignatures = verifySignatures,
    autoInstall = autoInstall,
    installMethod = InstallMethod.entries.firstOrNull { it.name == installMethod }
        ?: InstallMethod.SYSTEM,
    ignoredUpdates = ignoredUpdates,
)