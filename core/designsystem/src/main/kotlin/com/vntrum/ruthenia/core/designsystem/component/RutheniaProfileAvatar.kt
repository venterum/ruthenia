// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Number of seal motifs. Exposed for tests. */
internal const val PROFILE_SEAL_VARIANTS = 8

/**
 * Deterministically maps a [seed] to a seal motif index in
 * `0 until [PROFILE_SEAL_VARIANTS]`. Pure and stable: the same device always draws
 * the same seal, different installs tend to differ.
 */
internal fun profileSealIndex(seed: String): Int = seed.hashCode().mod(PROFILE_SEAL_VARIANTS)

/** Deterministic rotation in degrees, `0 until 360` — keeps symmetric motifs distinct. */
internal fun profileSealRotation(seed: String): Float = seed.hashCode().mod(360).toFloat()

/**
 * The "device seal": a round avatar generated from a local [seed], used in place
 * of an account avatar.
 *
 * The mark is deterministic (see [profileSealIndex]) and drawn from theme roles,
 * so it follows the accent and the light/dark theme without any assets.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RutheniaProfileAvatar(
    seed: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val motifIndex = remember(seed) { profileSealIndex(seed) }
    val rotation = remember(seed) { profileSealRotation(seed) }

    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(semantics),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size * SEAL_FILL)
                .graphicsLayer { rotationZ = rotation }
                .background(MaterialTheme.colorScheme.onPrimaryContainer, profileSealMotif(motifIndex)),
        )
    }
}

/** Emblem-like shapes from the expressive shape set. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun profileSealMotif(index: Int): Shape = when (index) {
    0 -> MaterialShapes.Clover4Leaf.toShape()
    1 -> MaterialShapes.Clover8Leaf.toShape()
    2 -> MaterialShapes.Flower.toShape()
    3 -> MaterialShapes.Sunny.toShape()
    4 -> MaterialShapes.Burst.toShape()
    5 -> MaterialShapes.SoftBurst.toShape()
    6 -> MaterialShapes.Gem.toShape()
    else -> MaterialShapes.Cookie9Sided.toShape()
}

/** How much of the avatar the motif covers. */
private const val SEAL_FILL = 0.56f
