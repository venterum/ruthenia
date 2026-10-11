/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.model

import com.vntrum.ruthenia.core.data.db.InstalledAppEntity
import com.vntrum.ruthenia.core.data.settings.AppSettings

/** An app from the Ruthenia Store library along with its computed update status. */
data class InstalledApp(
    val packageName: String,
    val appId: Long?,
    val name: String?,
    val iconUrl: String?,
    val publisher: String?,
    val installedVersionCode: Long?,
    val installedVersionName: String?,
    val latestVersionCode: Long?,
    val latestVersionName: String?,
    val whatsNew: String?,
    val lastCheckedAt: Long?,
    val lastInstalledAt: Long?,
    val source: AppSource = AppSource.UNKNOWN,
    /** Whether the app was found in the RuStore catalog: without it an update is impossible. */
    val inCatalog: Boolean = false,
) {

    /**
     * Update status. If the app is not in the RuStore catalog there is nothing
     * to compare versions with — regardless of the other fields the status is
     * `UNKNOWN`.
     */
    val status: UpdateStatus
        get() = if (!inCatalog) {
            UpdateStatus.UNKNOWN
        } else {
            VersionComparator.status(
                installedVersionCode = installedVersionCode,
                installedVersionName = installedVersionName,
                latestVersionCode = latestVersionCode,
                latestVersionName = latestVersionName,
            )
        }
}

/**
 * Whether the app update is hidden by the user.
 *
 * A record without a version (`packageName`) hides updates forever, a record
 * with a version (`packageName@versionCode`) hides only a specific version:
 * when the next one appears in the catalog, the app returns to the list.
 */
fun InstalledApp.isUpdateIgnored(ignored: Set<String>): Boolean {
    if (packageName in ignored) return true
    val version = latestVersionCode ?: return false
    return AppSettings.ignoredUpdateKey(packageName, version) in ignored
}

/**
 * Whether the app is visible given the source settings. A shared filter for
 * counters and badges: Google Play / sideloaded apps hidden in settings end up
 * neither in the tab badge nor in the updates banner on Home.
 */
fun InstalledApp.isVisibleForCounters(settings: AppSettings): Boolean = when (source) {
    AppSource.GOOGLE_PLAY -> settings.showGooglePlayApps
    AppSource.SIDELOAD -> settings.showSideloadedApps
    else -> true
}

internal fun InstalledAppEntity.toModel(): InstalledApp = InstalledApp(
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
    lastCheckedAt = lastCheckedAt,
    lastInstalledAt = lastInstalledAt,
    source = source,
    inCatalog = inCatalog,
)
