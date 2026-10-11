/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.settings

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build

/** App version name and code, as reported by the package manager. */
internal data class AppVersionInfo(
    val name: String?,
    val code: Long?,
)

/** Version of the running app; fields are `null` if the package info is unavailable. */
internal fun Context.appVersionInfo(): AppVersionInfo = runCatching {
    @Suppress("DEPRECATION")
    val info: PackageInfo = packageManager.getPackageInfo(packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    AppVersionInfo(name = info.versionName, code = code)
}.getOrDefault(AppVersionInfo(name = null, code = null))
