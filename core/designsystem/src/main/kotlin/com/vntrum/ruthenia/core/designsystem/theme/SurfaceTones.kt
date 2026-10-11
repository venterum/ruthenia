// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Tonal hierarchy of app surfaces. The hierarchy is built with tone rather than
 * shadows: the "higher" the semantic layer, the lighter the surface (in the dark
 * theme, the lighter the tone). The same semantic layer always uses one role,
 * so list cards, hero headers, and stats stay consistent across screens.
 *
 * The values are `colorScheme` roles, so Material You dynamic colors
 * work unchanged.
 */
object RutheniaSurfaceTones {
    /** App and list cards — the base content layer. */
    val card: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerLow

    /**
     * Large hero surfaces: the app detail header, the featured card, and the
     * progress card. Anything that should read as a separate "screen within a
     * screen".
     */
    val hero: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHigh

    /** Stat cards: version, size, date. */
    val stat: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainer

    /** Icon tiles on top of cards. */
    val tile: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerLowest
}
