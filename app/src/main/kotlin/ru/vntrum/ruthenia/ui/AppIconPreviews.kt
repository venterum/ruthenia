// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.ui

import com.vntrum.ruthenia.R
import com.vntrum.ruthenia.core.data.settings.AppIconVariant
import com.vntrum.ruthenia.feature.settings.AppIconPreview

/**
 * Launcher-icon previews for the settings picker.
 *
 * The adaptive-icon layers live in this module, so the resource ids are mapped
 * here and handed to the settings feature, which only renders them.
 */
val appIconPreviews: List<AppIconPreview> = listOf(
    AppIconPreview(
        variant = AppIconVariant.ORIGINAL,
        foregroundRes = R.drawable.ic_launcher_foreground,
        backgroundRes = R.color.ic_launcher_background,
    ),
    AppIconPreview(
        variant = AppIconVariant.DARK,
        foregroundRes = R.drawable.ic_launcher_foreground_dark,
        backgroundRes = R.color.ic_launcher_background_dark,
    ),
    AppIconPreview(
        variant = AppIconVariant.EMERALD,
        foregroundRes = R.drawable.ic_launcher_foreground_emerald,
        backgroundRes = R.color.ic_launcher_background_emerald,
    ),
    AppIconPreview(
        variant = AppIconVariant.RASPBERRY,
        foregroundRes = R.drawable.ic_launcher_foreground_raspberry,
        backgroundRes = R.color.ic_launcher_background_raspberry,
    ),
    AppIconPreview(
        variant = AppIconVariant.VIOLET_GOLD,
        foregroundRes = R.drawable.ic_launcher_foreground_violet_gold,
        backgroundRes = R.color.ic_launcher_background_violet_gold,
    ),
    AppIconPreview(
        variant = AppIconVariant.WARM,
        foregroundRes = R.drawable.ic_launcher_foreground_warm,
        backgroundRes = R.color.ic_launcher_background_warm,
    ),
    AppIconPreview(
        variant = AppIconVariant.FLAG,
        foregroundRes = R.drawable.ic_launcher_foreground_flag,
        backgroundRes = R.color.ic_launcher_background_flag,
    ),
    AppIconPreview(
        variant = AppIconVariant.GOOGLE_PLAY,
        foregroundRes = R.drawable.ic_launcher_foreground_google_play,
        backgroundRes = R.color.ic_launcher_background_google_play,
    ),
)
