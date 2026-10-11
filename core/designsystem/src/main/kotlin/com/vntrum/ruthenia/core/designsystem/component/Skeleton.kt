// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * Phase of the skeleton shimmer, `0f..1f`, looping. Called once per screen so
 * all placeholders sweep in sync.
 */
@Composable
fun rememberSkeletonShimmer(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    // infiniteRepeatable only accepts a duration-based spec, while motionScheme
    // provides springs; for the infinite sweep we use a linear tween.
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SKELETON_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeletonShimmer",
    )
    return shimmer
}

/**
 * A single skeleton placeholder: a rounded rectangle with a highlight sweeping
 * across it. [shimmer] is the shared phase from [rememberSkeletonShimmer].
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier,
    shimmer: Float,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    shape: Shape = MaterialTheme.shapes.small,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val base = color.copy(alpha = 0.4f)
                val highlight = color.copy(alpha = 0.9f)
                // The highlight starts off the left edge and leaves past the
                // right edge, so every block gets a full sweep per period.
                val sweep = shimmer * 2.6f - 1.3f
                val brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(x = size.width * (sweep - 0.35f), y = 0f),
                    end = Offset(x = size.width * (sweep + 0.35f), y = size.height),
                )
                onDrawBehind { drawRect(brush) }
            },
    )
}

private const val SKELETON_PERIOD_MS = 1400
