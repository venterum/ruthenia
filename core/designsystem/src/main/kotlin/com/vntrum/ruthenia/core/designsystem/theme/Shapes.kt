// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Card corner radii are extracted into tokens: the [ExpressiveCard] component
 * uses them for the press shape morph, while the main [RutheniaShapes] scale
 * uses them for the resting state. This leaves no "random" numbers in screen code.
 */
object RutheniaShapeTokens {
    val cardRest = 28.dp
    val cardPressed = 16.dp

    /**
     * "Panel" radius: bottom action bar and large surfaces. Matches the outer
     * radius of the expressive button at XLarge height (64dp / 2), so the
     * background behind the button and the button itself are rounded consistently.
     */
    val panel = 32.dp
}

/**
 * Expressive shape scale: larger radii and additional steps
 * (largeIncreased / extraLargeIncreased / extraExtraLarge) that are absent
 * from the classic stable scale.
 */
internal val RutheniaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(RutheniaShapeTokens.cardPressed),
    large = RoundedCornerShape(RutheniaShapeTokens.cardRest),
    extraLarge = RoundedCornerShape(RutheniaShapeTokens.panel),
    largeIncreased = RoundedCornerShape(36.dp),
    extraLargeIncreased = RoundedCornerShape(40.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

/**
 * Fully rounded "pill". The only shape outside the base scale —
 * defined as a percentage rather than an arbitrary dp value.
 */
val RutheniaPillShape = RoundedCornerShape(percent = 50)
