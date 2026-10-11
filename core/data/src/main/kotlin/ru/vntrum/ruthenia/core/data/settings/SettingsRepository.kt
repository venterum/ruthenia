/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            themeMode = preferences[Keys.THEME_MODE]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            dynamicColor = preferences[Keys.DYNAMIC_COLOR] ?: true,
            accent = preferences[Keys.ACCENT]
                ?.let { name -> AccentPalette.entries.firstOrNull { it.name == name } }
                ?: AccentPalette.TEAL,
            language = preferences[Keys.LANGUAGE]
                ?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
                ?: AppLanguage.SYSTEM,
            navBarStyle = preferences[Keys.NAV_BAR_STYLE]
                ?.let { name -> NavBarStyle.entries.firstOrNull { it.name == name } }
                ?: NavBarStyle.FLOATING,
            appIconShape = preferences[Keys.APP_ICON_SHAPE]
                ?.let { name -> AppIconShape.entries.firstOrNull { it.name == name } }
                ?: AppIconShape.CIRCLE,
            gamesEnabled = preferences[Keys.GAMES_ENABLED] ?: true,
            backgroundChecksEnabled = preferences[Keys.BACKGROUND_CHECKS] ?: false,
            updateCheckIntervalHours = preferences[Keys.UPDATE_INTERVAL_HOURS]
                ?: AppSettings.DEFAULT_INTERVAL_HOURS,
            unmeteredOnly = preferences[Keys.UNMETERED_ONLY] ?: false,
            diagnosticsEnabled = preferences[Keys.DIAGNOSTICS] ?: false,
            onboardingCompleted = preferences[Keys.ONBOARDING_COMPLETED] ?: false,
            showGooglePlayApps = preferences[Keys.SHOW_GOOGLE_PLAY] ?: false,
            showSideloadedApps = preferences[Keys.SHOW_SIDELOADED] ?: true,
            apkExportTreeUri = preferences[Keys.APK_EXPORT_TREE],
            verifySignatures = preferences[Keys.VERIFY_SIGNATURES] ?: true,
            autoInstall = preferences[Keys.AUTO_INSTALL] ?: false,
            installMethod = preferences[Keys.INSTALL_METHOD]
                ?.let { name -> InstallMethod.entries.firstOrNull { it.name == name } }
                ?: InstallMethod.SYSTEM,
            ignoredUpdates = preferences[Keys.IGNORED_UPDATES].orEmpty(),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    /** Accent palette (used when dynamic colors are disabled). */
    suspend fun setAccent(accent: AccentPalette) {
        dataStore.edit { it[Keys.ACCENT] = accent.name }
    }

    /** UI language. */
    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    /** Bottom navigation bar style. */
    suspend fun setNavBarStyle(style: NavBarStyle) {
        dataStore.edit { it[Keys.NAV_BAR_STYLE] = style.name }
    }

    /** Shape of app icons in the interface. */
    suspend fun setAppIconShape(shape: AppIconShape) {
        dataStore.edit { it[Keys.APP_ICON_SHAPE] = shape.name }
    }

    /** Shows or hides the "Games" tab. */
    suspend fun setGamesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.GAMES_ENABLED] = enabled }
    }

    suspend fun setBackgroundChecksEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKGROUND_CHECKS] = enabled }
    }

    suspend fun setUpdateCheckIntervalHours(hours: Int) {
        dataStore.edit { it[Keys.UPDATE_INTERVAL_HOURS] = hours }
    }

    suspend fun setUnmeteredOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.UNMETERED_ONLY] = enabled }
    }

    suspend fun setDiagnosticsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DIAGNOSTICS] = enabled }
    }

    /** Onboarding completed (or skipped). */
    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setShowGooglePlayApps(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_GOOGLE_PLAY] = enabled }
    }

    suspend fun setShowSideloadedApps(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_SIDELOADED] = enabled }
    }

    /** Folder for saving APKs; `null` clears the selection. */
    suspend fun setApkExportTreeUri(treeUri: String?) {
        dataStore.edit { preferences ->
            if (treeUri == null) {
                preferences.remove(Keys.APK_EXPORT_TREE)
            } else {
                preferences[Keys.APK_EXPORT_TREE] = treeUri
            }
        }
    }

    /** Enables/disables APK signature verification before install. */
    suspend fun setVerifySignatures(enabled: Boolean) {
        dataStore.edit { it[Keys.VERIFY_SIGNATURES] = enabled }
    }

    /** Enables/disables automatic install after download. */
    suspend fun setAutoInstall(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_INSTALL] = enabled }
    }

    /** Selects the install backend: system dialog or silent Shizuku install. */
    suspend fun setInstallMethod(method: InstallMethod) {
        dataStore.edit { it[Keys.INSTALL_METHOD] = method.name }
    }

    /**
     * Hides an app update. With a version — up to and including it, without a
     * version — forever.
     */
    suspend fun ignoreUpdate(packageName: String, versionCode: Long?) {
        dataStore.edit { preferences ->
            preferences[Keys.IGNORED_UPDATES] = preferences[Keys.IGNORED_UPDATES].orEmpty() +
                AppSettings.ignoredUpdateKey(packageName, versionCode)
        }
    }

    /** Shows updates for a hidden app again. */
    suspend fun restoreUpdateVisibility(packageName: String) {
        dataStore.edit { preferences ->
            preferences[Keys.IGNORED_UPDATES] = preferences[Keys.IGNORED_UPDATES].orEmpty()
                .filterNot { it == packageName || it.startsWith("$packageName@") }
                .toSet()
        }
    }

    /** Replaces the set of hidden updates — for example, on data import. */
    suspend fun setIgnoredUpdates(keys: Set<String>) {
        dataStore.edit { preferences ->
            if (keys.isEmpty()) {
                preferences.remove(Keys.IGNORED_UPDATES)
            } else {
                preferences[Keys.IGNORED_UPDATES] = keys
            }
        }
    }

    /**
     * Restores user settings from a backup.
     *
     * Deliberately leaves the APK folder URI and local diagnostics alone: they
     * are tied to a specific device and the current session.
     */
    suspend fun restore(settings: AppSettings) {
        dataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = settings.themeMode.name
            preferences[Keys.DYNAMIC_COLOR] = settings.dynamicColor
            preferences[Keys.ACCENT] = settings.accent.name
            preferences[Keys.NAV_BAR_STYLE] = settings.navBarStyle.name
            preferences[Keys.APP_ICON_SHAPE] = settings.appIconShape.name
            preferences[Keys.GAMES_ENABLED] = settings.gamesEnabled
            preferences[Keys.BACKGROUND_CHECKS] = settings.backgroundChecksEnabled
            preferences[Keys.UPDATE_INTERVAL_HOURS] = settings.updateCheckIntervalHours
            preferences[Keys.UNMETERED_ONLY] = settings.unmeteredOnly
            preferences[Keys.SHOW_GOOGLE_PLAY] = settings.showGooglePlayApps
            preferences[Keys.SHOW_SIDELOADED] = settings.showSideloadedApps
            preferences[Keys.VERIFY_SIGNATURES] = settings.verifySignatures
            preferences[Keys.AUTO_INSTALL] = settings.autoInstall
            preferences[Keys.INSTALL_METHOD] = settings.installMethod.name
            if (settings.ignoredUpdates.isEmpty()) {
                preferences.remove(Keys.IGNORED_UPDATES)
            } else {
                preferences[Keys.IGNORED_UPDATES] = settings.ignoredUpdates
            }
        }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT = stringPreferencesKey("accent_palette")
        val LANGUAGE = stringPreferencesKey("language")
        val NAV_BAR_STYLE = stringPreferencesKey("nav_bar_style")
        val APP_ICON_SHAPE = stringPreferencesKey("app_icon_shape")
        val GAMES_ENABLED = booleanPreferencesKey("games_enabled")
        val BACKGROUND_CHECKS = booleanPreferencesKey("background_checks")
        val UPDATE_INTERVAL_HOURS = intPreferencesKey("update_interval_hours")
        val UNMETERED_ONLY = booleanPreferencesKey("unmetered_only")
        val DIAGNOSTICS = booleanPreferencesKey("diagnostics_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SHOW_GOOGLE_PLAY = booleanPreferencesKey("show_google_play_apps")
        val SHOW_SIDELOADED = booleanPreferencesKey("show_sideloaded_apps")
        val APK_EXPORT_TREE = stringPreferencesKey("apk_export_tree_uri")
        val VERIFY_SIGNATURES = booleanPreferencesKey("verify_signatures")
        val AUTO_INSTALL = booleanPreferencesKey("auto_install")
        val INSTALL_METHOD = stringPreferencesKey("install_method")
        val IGNORED_UPDATES = stringSetPreferencesKey("ignored_updates")
    }
}
