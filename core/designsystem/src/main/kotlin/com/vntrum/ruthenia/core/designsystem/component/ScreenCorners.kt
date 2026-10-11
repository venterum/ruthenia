// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaShapeTokens

/**
 * Rounds the screen edges. Applied to the root of each navigation destination:
 * when a screen shrinks on back navigation, its corners are visible like a card —
 * the same technique used in Google apps. At rest the rounding is invisible, since
 * the background behind the screen is from the same theme.
 *
 * By default the radius matches the card radius
 * ([RutheniaShapeTokens.cardRest]), so the "screen → card" transition looks like
 * a single shape motion.
 */
fun Modifier.screenCorners(radius: Dp = RutheniaShapeTokens.cardRest): Modifier = graphicsLayer {
    shape = RoundedCornerShape(radius)
    clip = true
}
