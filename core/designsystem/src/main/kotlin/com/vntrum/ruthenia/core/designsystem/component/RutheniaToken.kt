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
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaTokenColor
import com.vntrum.ruthenia.core.designsystem.theme.faceColor
import com.vntrum.ruthenia.core.designsystem.theme.frameColor
import com.vntrum.ruthenia.core.designsystem.theme.motifColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Frame style of the device token. Mirrors the persisted `TokenRing` by name. */
enum class RutheniaTokenRing {
    NONE,
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
 * keeps symmetric motifs distinct. Thin and thick frames leave a small gap
 * between the frame and the face, so the mark reads as a medallion.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RutheniaToken(
    motif: Int,
    accent: RutheniaTokenColor,
    ring: RutheniaTokenRing,
    ringAccent: RutheniaTokenColor,
    rotation: Float,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val ringColor = ringAccent.frameColor()
    val frameWidth = when (ring) {
        RutheniaTokenRing.THIN -> 1.5.dp
        RutheniaTokenRing.THICK -> 2.5.dp
        RutheniaTokenRing.NONE, RutheniaTokenRing.TEETH -> 0.dp
    }
    val faceSize = if (frameWidth > 0.dp) size - (frameWidth + FRAME_GAP) * 2 else size
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(semantics),
        contentAlignment = Alignment.Center,
    ) {
        if (frameWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .border(frameWidth, ringColor, CircleShape),
            )
        }

        Box(
            modifier = Modifier
                .size(faceSize)
                .clip(CircleShape)
                .background(accent.faceColor()),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(faceSize * TOKEN_FILL)
                    .graphicsLayer { rotationZ = rotation }
                    .background(accent.motifColor(), tokenMotif(motif)),
            )
        }

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
    4 -> MaterialShapes.VerySunny.toShape()
    5 -> MaterialShapes.Burst.toShape()
    6 -> MaterialShapes.SoftBurst.toShape()
    7 -> MaterialShapes.Boom.toShape()
    8 -> MaterialShapes.SoftBoom.toShape()
    9 -> MaterialShapes.Gem.toShape()
    10 -> MaterialShapes.Diamond.toShape()
    11 -> MaterialShapes.Pentagon.toShape()
    12 -> MaterialShapes.Triangle.toShape()
    13 -> MaterialShapes.Heart.toShape()
    14 -> MaterialShapes.Puffy.toShape()
    15 -> MaterialShapes.PuffyDiamond.toShape()
    16 -> MaterialShapes.ClamShell.toShape()
    17 -> MaterialShapes.Cookie4Sided.toShape()
    18 -> MaterialShapes.Cookie6Sided.toShape()
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
private const val MOTIF_COUNT = 20

/** Breathing space between the frame and the face. */
private val FRAME_GAP = 2.dp

private const val TEETH_COUNT = 16
private const val TEETH_LENGTH = 0.09f
private const val TEETH_WIDTH = 0.03f
