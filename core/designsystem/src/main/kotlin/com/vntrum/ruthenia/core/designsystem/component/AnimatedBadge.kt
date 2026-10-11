// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/**
 * A numeric [Badge] whose value rolls in the direction of the change:
 * an increase slides the new number up from below, a decrease from above.
 * Zero means "no badge".
 */
@Composable
fun AnimatedCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val motion = MaterialTheme.motionScheme

    AnimatedContent(
        targetState = count,
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (
                slideInVertically(
                    animationSpec = motion.defaultSpatialSpec<IntOffset>(),
                    initialOffsetY = { height -> direction * height },
                ) + fadeIn(animationSpec = motion.defaultEffectsSpec())
                ) togetherWith (
                slideOutVertically(
                    animationSpec = motion.fastSpatialSpec<IntOffset>(),
                    targetOffsetY = { height -> -direction * height },
                ) + fadeOut(animationSpec = motion.fastEffectsSpec())
                )
        },
        label = "badgeCount",
    ) { value ->
        Badge(modifier = modifier) {
            Text(value.toString())
        }
    }
}
