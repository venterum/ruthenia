/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.device

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vntrum.ruthenia.core.data.model.AppSource
import com.vntrum.ruthenia.core.data.model.DeviceApp

/**
 * Reads installed apps through `PackageManager`.
 *
 * The `QUERY_ALL_PACKAGES` permission is declared solely for this list: without
 * it `PackageManager` only sees packages from `<queries>`. The data stays on the
 * device — nothing reads or sends it except an explicit import.
 */
@Singleton
class DeviceAppReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Installed non-system apps, sorted by name. */
    suspend fun installedApps(): List<DeviceApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
        }

        packages
            .asSequence()
            .filterNotNull()
            .mapNotNull { info ->
                val applicationInfo = info.applicationInfo ?: return@mapNotNull null
                if (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
                    return@mapNotNull null
                }

                DeviceApp(
                    packageName = info.packageName,
                    label = runCatching {
                        applicationInfo.loadLabel(packageManager).toString()
                    }.getOrNull(),
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        info.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        info.versionCode.toLong()
                    },
                    versionName = info.versionName,
                    isSystem = false,
                    source = installerSource(info.packageName, packageManager),
                )
            }
            .sortedBy { it.label?.lowercase() ?: it.packageName }
            .toList()
    }

    /** Where the app came from: the new API first, then the deprecated one. */
    private fun installerSource(packageName: String, packageManager: PackageManager): AppSource {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            runCatching { packageManager.getInstallerPackageName(packageName) }.getOrNull()
        }

        return AppSource.fromInstaller(installer)
    }
}
