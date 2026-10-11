// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaShapeTokens

/**
 * A clickable card with a press shape morph: the corner radius
 * contracts on the fast spatial spring from the theme's motion system.
 *
 * The surface is [androidx.compose.material3.ColorScheme.surfaceContainerLow],
 * i.e. one tone above the background; hierarchy reads by tone, not by shadow.
 * Radii come from the [RutheniaShapeTokens] tokens, so the component does not
 * scatter "magic" numbers, and one card type always uses one
 * shape.
 */
@Composable
fun ExpressiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) RutheniaShapeTokens.cardPressed else RutheniaShapeTokens.cardRest,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "ExpressiveCardCorner",
    )

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(corner),
        color = containerColor,
        interactionSource = interactionSource,
        content = content,
    )
}
