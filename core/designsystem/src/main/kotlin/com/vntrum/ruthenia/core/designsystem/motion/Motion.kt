// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The app's unified motion system.
 *
 * There are no custom durations or hand-tuned springs here: every animation
 * takes its specs from [MaterialTheme.motionScheme]
 * (`MotionScheme.expressive()` is set in the theme). Spatial transitions
 * (scale, position) run on spatial springs with honest overshoot, while
 * opacity uses effects specs without overshoot so color and alpha
 * do not "overshoot" the target.
 *
 * Going back mirrors Android's system animation (design guidance
 * "Predictive back", full-screen surfaces): the outgoing screen shrinks 100%→90%
 * and fades out, while the previous one appears from 110%→100% (fade-through).
 * `Modifier.screenCorners` is applied to the navigation root, so the shrinking
 * screen looks like a card with rounded corners.
 */
object RutheniaMotion {
    /** How much the adjacent pager page dims and shrinks. */
    const val PAGE_MIN_ALPHA = 0.65f
    const val PAGE_MIN_SCALE = 0.94f

    /** Scale from which the previous screen appears on back navigation. */
    const val POP_ENTER_INITIAL_SCALE = 1.1f

    /** Scale to which the outgoing screen shrinks on back navigation. */
    const val POP_EXIT_TARGET_SCALE = 0.9f

    /** Scale from which the new screen appears on forward navigation. */
    const val PUSH_ENTER_INITIAL_SCALE = 0.96f
}

/**
 * Back navigation: the previous screen appears from 110% on the theme's
 * spatial spring, opacity uses the effects spec.
 */
fun AnimatedContentTransitionScope<*>.navPopEnter(motion: MotionScheme): EnterTransition {
    val spatial = motion.defaultSpatialSpec<Float>()
    val effects = motion.defaultEffectsSpec<Float>()
    return scaleIn(
        animationSpec = spatial,
        initialScale = RutheniaMotion.POP_ENTER_INITIAL_SCALE,
    ) + fadeIn(animationSpec = effects)
}

/**
 * Back navigation: the outgoing screen shrinks to 90% and fades out quickly
 * (effects fast — the exit should not "linger").
 */
fun AnimatedContentTransitionScope<*>.navPopExit(motion: MotionScheme): ExitTransition {
    val spatial = motion.defaultSpatialSpec<Float>()
    val effects = motion.fastEffectsSpec<Float>()
    return scaleOut(
        animationSpec = spatial,
        targetScale = RutheniaMotion.POP_EXIT_TARGET_SCALE,
    ) + fadeOut(animationSpec = effects)
}

/**
 * Forward navigation: the new screen gently "moves in" by scale on the spatial
 * spring and appears while the previous one leaves via opacity. The same physical
 * language as back navigation, so navigation feels cohesive.
 */
fun AnimatedContentTransitionScope<*>.navPushEnter(motion: MotionScheme): EnterTransition {
    val spatial = motion.defaultSpatialSpec<Float>()
    val effects = motion.defaultEffectsSpec<Float>()
    return scaleIn(
        animationSpec = spatial,
        initialScale = RutheniaMotion.PUSH_ENTER_INITIAL_SCALE,
    ) + fadeIn(animationSpec = effects)
}

/** Forward navigation: the old screen only fades out — no opposing scale. */
fun AnimatedContentTransitionScope<*>.navPushExit(motion: MotionScheme): ExitTransition {
    val effects = motion.fastEffectsSpec<Float>()
    return fadeOut(animationSpec = effects)
}

/**
 * A slight "press-in" on tap — on the fast spatial spring from the theme's
 * motion system, not a custom value.
 */
fun Modifier.pressScaleEffect(pressedScale: Float = 0.96f): Modifier = composed {
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spec,
        label = "pressScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
        }
}
