/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.model

/**
 * An app installed on the device and visible through `PackageManager`.
 *
 * Read strictly locally and used only to match against the catalog: only the
 * package name goes out, and only when the user starts an import themselves.
 */
data class DeviceApp(
    val packageName: String,
    val label: String?,
    val versionCode: Long?,
    val versionName: String?,
    val isSystem: Boolean,
    val source: AppSource,
)
