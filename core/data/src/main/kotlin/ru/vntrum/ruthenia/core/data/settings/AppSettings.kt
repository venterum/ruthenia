/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.settings

/** Theme choice. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** UI language; [SYSTEM] follows the system locale. */
enum class AppLanguage {
    SYSTEM,
    RU,
    EN,
}

/** Bottom navigation bar style on compact screens. */
enum class NavBarStyle {
    /** Floating pill above the content. */
    FLOATING,

    /** Standard Material 3 bar docked to the bottom edge. */
    NORMAL,
}

/** Shape of catalog app icons across the interface. */
enum class AppIconShape {
    /** Round icons (launcher default). */
    CIRCLE,

    /** Rounded square. */
    ROUNDED_SQUARE,

    /** Sharp square. */
    SQUARE,
}

/**
 * Colour variant of the app's launcher icon.
 *
 * Each value maps to an `<activity-alias>` in the manifest via [aliasSuffix];
 * exactly one alias is enabled at a time (see `LauncherIconController`). The
 * themed (monochrome) icon layer is shared and never changes.
 */
enum class AppIconVariant(val aliasSuffix: String) {
    /** The default brand-blue icon. */
    ORIGINAL("IconOriginal"),

    /** Deep navy background. */
    DARK("IconDark"),

    /** Green-on-dark. */
    EMERALD("IconEmerald"),

    /** Raspberry on cream. */
    RASPBERRY("IconRaspberry"),

    /** Violet with a gold shackle. */
    VIOLET_GOLD("IconVioletGold"),

    /** Warm orange on charcoal. */
    WARM("IconWarm"),

    /** White/blue/red bands. */
    FLAG("IconFlag"),

    /** Google Play–style four-colour mark. */
    GOOGLE_PLAY("IconGooglePlay"),
}

/**
 * Accent palette for when Material You dynamic colors are unavailable or
 * disabled.
 */
enum class AccentPalette {
    TEAL,
    INDIGO,
    VIOLET,
    GREEN,
    AMBER,
}

/**
 * How downloaded APKs are installed.
 */
enum class InstallMethod {
    /** System `PackageInstaller`: the confirmation dialog is always shown. */
    SYSTEM,

    /** Silent install via Shizuku/Sui, if available and permitted. */
    SHIZUKU,
}

/**
 * User settings.
 *
 * Background update checks are enabled by default, local diagnostics are
 * disabled (AGENTS.md: diagnostics only at the user's explicit request).
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accent: AccentPalette = AccentPalette.TEAL,
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** Bottom navigation bar style: floating pill or docked standard bar. */
    val navBarStyle: NavBarStyle = NavBarStyle.FLOATING,
    /**
     * Shape of app icons in lists, cards, and on details screens. The app's own
     * brand mark is deliberately always round.
     */
    val appIconShape: AppIconShape = AppIconShape.CIRCLE,
    /**
     * Colour variant of the launcher icon. Applied by switching the matching
     * `<activity-alias>` (see `LauncherIconController`).
     */
    val appIconVariant: AppIconVariant = AppIconVariant.ORIGINAL,
    /**
     * Show the "Games" tab. Disabling hides the page without affecting
     * game sections in the feed or search results.
     */
    val gamesEnabled: Boolean = true,
    val backgroundChecksEnabled: Boolean = false,
    val updateCheckIntervalHours: Int = DEFAULT_INTERVAL_HOURS,
    val unmeteredOnly: Boolean = false,
    val diagnosticsEnabled: Boolean = false,
    /**
     * First launch: the user has completed (or skipped) onboarding.
     * The setting is local and deliberately not carried over in a backup.
     */
    val onboardingCompleted: Boolean = false,
    /** Show apps installed from Google Play in the library. */
    val showGooglePlayApps: Boolean = false,
    /** Show manually installed apps (APK, adb) in the library. */
    val showSideloadedApps: Boolean = true,
    /**
     * SAF tree where APKs are saved by the "Save APK" action.
     * `null` — no folder selected.
     */
    val apkExportTreeUri: String? = null,
    /** Verify the downloaded APK signature before install. */
    val verifySignatures: Boolean = true,
    /** Automatically start the install after the download completes. */
    val autoInstall: Boolean = false,
    /**
     * How downloaded APKs are installed: the system confirmation dialog or
     * silent install via Shizuku/Sui (opt-in, falls back to the system dialog).
     */
    val installMethod: InstallMethod = InstallMethod.SYSTEM,
    /**
     * Updates hidden by the user. Record format: `packageName` — never offer an
     * update, `packageName@versionCode` — do not offer up to and including this
     * version.
     */
    val ignoredUpdates: Set<String> = emptySet(),
) {
    companion object {
        const val DEFAULT_INTERVAL_HOURS = 24

        /** Allowed background check intervals, hours. */
        val INTERVAL_OPTIONS = listOf(6, 12, 24, 72)

        /** Record key in [ignoredUpdates] for a specific version. */
        fun ignoredUpdateKey(packageName: String, versionCode: Long?): String =
            if (versionCode == null) packageName else "$packageName@$versionCode"

        /** Record key for sources without a numeric version code (GitHub tags). */
        fun ignoredUpdateKey(packageName: String, version: String): String =
            "$packageName@$version"
    }
}
