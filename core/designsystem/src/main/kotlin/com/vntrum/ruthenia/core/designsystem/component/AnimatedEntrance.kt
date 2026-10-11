// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Staggered entrance of a section: it fades in and slides up by a few
 * dp after [index] * [staggerMs] milliseconds.
 *
 * Used to give long screens (About, Settings) a light, ordered appearance
 * instead of everything popping in at once.
 */
@Composable
fun AnimatedEntrance(
    index: Int = 0,
    modifier: Modifier = Modifier,
    staggerMs: Long = DEFAULT_STAGGER_MS,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.animatedEntrance(index = index, staggerMs = staggerMs)) {
        content()
    }
}

/** The modifier form of [AnimatedEntrance] for screens that lay out their own containers. */
fun Modifier.animatedEntrance(
    index: Int = 0,
    staggerMs: Long = DEFAULT_STAGGER_MS,
): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(staggerMs * index)
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "entranceAlpha",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 24f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "entranceOffsetY",
    )
    graphicsLayer {
        this.alpha = alpha
        translationY = offsetY.dp.toPx()
    }
}

private const val DEFAULT_STAGGER_MS = 70L
