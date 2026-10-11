/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.core.data.settings.AppIconVariant
import com.vntrum.ruthenia.core.designsystem.haptics.rememberRutheniaHaptics

/**
 * One selectable launcher icon: its colour variant plus the resources needed to
 * preview it.
 *
 * The resources are the app's adaptive-icon layers, which live in the app
 * module — hence they are passed in as ids rather than referenced here.
 */
data class AppIconPreview(
    val variant: AppIconVariant,
    val foregroundRes: Int,
    val backgroundRes: Int,
)

/**
 * A wrapping row of launcher-icon previews. Each swatch shows the icon's
 * background colour with its foreground mark, cropped the way a launcher crops
 * an adaptive icon (the visible 72 of the 108 layer units).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppIconPicker(
    previews: List<AppIconPreview>,
    selected: AppIconVariant,
    onSelect: (AppIconVariant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberRutheniaHaptics()
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        previews.forEach { preview ->
            val isSelected = preview.variant == selected
            val shape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(shape)
                    .background(colorResource(preview.backgroundRes))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = shape,
                    )
                    .clickable {
                        haptics.tick()
                        onSelect(preview.variant)
                    },
            ) {
                Icon(
                    painter = painterResource(preview.foregroundRes),
                    contentDescription = preview.variant.label(),
                    // The vector carries its own colours; do not tint it.
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .fillMaxSize()
                        // The foreground keeps the adaptive-icon safe zone
                        // (66 of 108); scale it so the mark reads at icon size.
                        .graphicsLayer(scaleX = 1.5f, scaleY = 1.5f),
                )
            }
        }
    }
}

@Composable
private fun AppIconVariant.label(): String = stringResource(
    when (this) {
        AppIconVariant.ORIGINAL -> R.string.settings_app_icon_original
        AppIconVariant.DARK -> R.string.settings_app_icon_dark
        AppIconVariant.EMERALD -> R.string.settings_app_icon_emerald
        AppIconVariant.RASPBERRY -> R.string.settings_app_icon_raspberry
        AppIconVariant.VIOLET_GOLD -> R.string.settings_app_icon_violet_gold
        AppIconVariant.WARM -> R.string.settings_app_icon_warm
        AppIconVariant.FLAG -> R.string.settings_app_icon_flag
        AppIconVariant.GOOGLE_PLAY -> R.string.settings_app_icon_google_play
    },
)
