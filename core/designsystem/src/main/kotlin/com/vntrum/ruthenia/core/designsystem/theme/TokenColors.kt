// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The token's own palette, independent of the theme accent.
 *
 * Every entry is a single hue; the face, motif and frame shades are derived per
 * light/dark theme, so one hue always yields a coherent trio and the palette can
 * grow by adding a hue instead of a full colour scheme.
 */
enum class RutheniaTokenColor(val hue: Float) {
    CRIMSON(355f),
    ROSE(325f),
    AMBER(45f),
    LIME(85f),
    GREEN(130f),
    TEAL(175f),
    SKY(205f),
    INDIGO(235f),
    VIOLET(275f),
    MAGENTA(305f),
}

/** Maps a persisted colour name to the enum, with a safe fallback. */
fun rutheniaTokenColorByName(name: String): RutheniaTokenColor =
    RutheniaTokenColor.entries.firstOrNull { it.name == name } ?: RutheniaTokenColor.TEAL

/** The token face — a soft tint in both themes. */
@Composable
fun RutheniaTokenColor.faceColor(darkTheme: Boolean = isSystemInDarkTheme()): Color =
    if (darkTheme) Color.hsl(hue, 0.42f, 0.24f) else Color.hsl(hue, 0.55f, 0.84f)

/** The motif — high contrast against the face. */
@Composable
fun RutheniaTokenColor.motifColor(darkTheme: Boolean = isSystemInDarkTheme()): Color =
    if (darkTheme) Color.hsl(hue, 0.55f, 0.85f) else Color.hsl(hue, 0.55f, 0.25f)

/** The frame — a saturated mid-tone, also used for the palette dots. */
@Composable
fun RutheniaTokenColor.frameColor(darkTheme: Boolean = isSystemInDarkTheme()): Color =
    if (darkTheme) Color.hsl(hue, 0.5f, 0.68f) else Color.hsl(hue, 0.55f, 0.45f)
