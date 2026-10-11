// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaAccent
import com.vntrum.ruthenia.core.designsystem.theme.onPrimaryContainerColor
import com.vntrum.ruthenia.core.designsystem.theme.primaryColor
import com.vntrum.ruthenia.core.designsystem.theme.primaryContainerColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Frame style of the device token. Mirrors the persisted `TokenRing` by name. */
enum class RutheniaTokenRing {
    THIN,
    THICK,
    TEETH,
}

/** Maps a persisted frame name to the design-system enum, with a safe fallback. */
fun rutheniaTokenRingByName(name: String): RutheniaTokenRing =
    RutheniaTokenRing.entries.firstOrNull { it.name == name } ?: RutheniaTokenRing.THIN

/**
 * The "device token": a round mark generated from the local identity, used in
 * place of an account avatar.
 *
 * [accent] colours the face and motif, [ringAccent] colours the frame; [rotation]
 * keeps symmetric motifs distinct. Everything is drawn from theme roles, so the
 * token follows the light/dark theme without any assets.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RutheniaToken(
    motif: Int,
    accent: RutheniaAccent,
    ring: RutheniaTokenRing,
    ringAccent: RutheniaAccent,
    rotation: Float,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val ringColor = ringAccent.primaryColor()
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.primaryContainerColor())
            .then(
                when (ring) {
                    RutheniaTokenRing.THIN -> Modifier.border(1.5.dp, ringColor, CircleShape)
                    RutheniaTokenRing.THICK -> Modifier.border(3.5.dp, ringColor, CircleShape)
                    RutheniaTokenRing.TEETH -> Modifier
                },
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(semantics),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size * TOKEN_FILL)
                .graphicsLayer { rotationZ = rotation }
                .background(accent.onPrimaryContainerColor(), tokenMotif(motif)),
        )
        if (ring == RutheniaTokenRing.TEETH) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawTeeth(ringColor)
            }
        }
    }
}

/** Emblem-like shapes from the expressive shape set. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun tokenMotif(index: Int): Shape = when (index.mod(MOTIF_COUNT)) {
    0 -> MaterialShapes.Clover4Leaf.toShape()
    1 -> MaterialShapes.Clover8Leaf.toShape()
    2 -> MaterialShapes.Flower.toShape()
    3 -> MaterialShapes.Sunny.toShape()
    4 -> MaterialShapes.Burst.toShape()
    5 -> MaterialShapes.SoftBurst.toShape()
    6 -> MaterialShapes.Gem.toShape()
    else -> MaterialShapes.Cookie9Sided.toShape()
}

/** Small notches around the rim — the "teeth" frame. */
private fun DrawScope.drawTeeth(color: Color) {
    val outer = size.minDimension / 2f
    val tickLength = size.minDimension * TEETH_LENGTH
    val strokeWidth = size.minDimension * TEETH_WIDTH
    repeat(TEETH_COUNT) { index ->
        val angle = 2.0 * PI * index / TEETH_COUNT
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        drawLine(
            color = color,
            start = Offset(
                x = center.x + dx * (outer - tickLength),
                y = center.y + dy * (outer - tickLength),
            ),
            end = Offset(
                x = center.x + dx * (outer - strokeWidth),
                y = center.y + dy * (outer - strokeWidth),
            ),
            strokeWidth = strokeWidth,
        )
    }
}

/** How much of the token the motif covers. */
private const val TOKEN_FILL = 0.56f

/** Kept in sync with `TOKEN_MOTIF_VARIANTS` in `:core:data`. */
private const val MOTIF_COUNT = 8

private const val TEETH_COUNT = 16
private const val TEETH_LENGTH = 0.09f
private const val TEETH_WIDTH = 0.03f
