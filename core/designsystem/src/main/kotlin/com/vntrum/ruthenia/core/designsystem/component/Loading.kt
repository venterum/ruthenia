// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.graphics.shapes.RoundedPolygon

/**
 * The branded indicator shape set: loading morphs between the same
 * organic shapes throughout the app, so it is recognizable at first
 * glance. The base Material set is good too but faceless — this is the only
 * "branded" loading accent.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val RutheniaLoadingPolygons: List<RoundedPolygon> = listOf(
    MaterialShapes.SoftBurst,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Pill,
    MaterialShapes.Oval,
)

/**
 * Expressive loading indicator: the shape smoothly morphs between
 * `MaterialShapes` instead of spinning as a static arc.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RutheniaLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    if (color == null) {
        LoadingIndicator(modifier = modifier, polygons = RutheniaLoadingPolygons)
    } else {
        LoadingIndicator(modifier = modifier, color = color, polygons = RutheniaLoadingPolygons)
    }
}

/**
 * Deterministic variant: [progress] returns a fraction 0f..1f. Uses the same
 * branded shape set as the indeterminate indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RutheniaLoadingIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    LoadingIndicator(progress = progress, modifier = modifier, polygons = RutheniaLoadingPolygons)
}

/** Wavy linear progress — an expressive replacement for LinearProgressIndicator. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RutheniaWavyProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    LinearWavyProgressIndicator(progress = progress, modifier = modifier)
}

/**
 * Progress semantics for TalkBack: reports the 0..1 fraction so the indicator
 * is read as "progress" rather than just a decorative bar.
 */
fun Modifier.progressSemantics(progress: Float): Modifier = semantics {
    progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
}
