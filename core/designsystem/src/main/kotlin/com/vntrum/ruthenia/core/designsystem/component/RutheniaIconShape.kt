// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * Shape of catalog app icons in the interface. Chosen in settings; the app's
 * own brand mark is deliberately not affected.
 */
enum class RutheniaIconShape {
    /** Round icons (launcher default). */
    CIRCLE,

    /** Rounded square. */
    ROUNDED_SQUARE,

    /** Sharp square. */
    SQUARE,
}

/**
 * Current app icon shape. The shell sets the value once from user settings, so
 * every screen and component follows it without extra parameters.
 */
val LocalRutheniaIconShape = staticCompositionLocalOf { RutheniaIconShape.CIRCLE }

/**
 * Corner shape for an app icon of the given [size]. The rounded square uses a
 * proportional radius, so icons of any size keep the same silhouette.
 */
@Composable
fun rutheniaAppIconShape(size: Dp): Shape = when (LocalRutheniaIconShape.current) {
    RutheniaIconShape.CIRCLE -> CircleShape
    RutheniaIconShape.ROUNDED_SQUARE -> RoundedCornerShape(size * ROUNDED_SQUARE_FACTOR)
    RutheniaIconShape.SQUARE -> RectangleShape
}

private const val ROUNDED_SQUARE_FACTOR = 0.25f
