// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The app's single haptic vocabulary.
 *
 * Every screen uses these named intents instead of raw [HapticFeedbackType]
 * constants, so the same interaction always feels the same:
 *
 * - [tick] — moving between tabs, selecting an option, a carousel item;
 * - [gestureTick] — fine ticks while dragging a selector;
 * - [confirm] — a committed action: install, copy, apply;
 * - [reject] — a refused action or an error;
 * - [longPress] — entering selection mode, hidden gestures;
 * - [toggle] — switches: different feedback for on and off.
 *
 * The platform maps the types to the device's own haptic engine; unsupported
 * types degrade gracefully to no-op or a system default.
 */
@Stable
class RutheniaHaptics internal constructor(
    private val haptics: HapticFeedback,
) {

    /** Moving between tabs, selecting a segmented option or a carousel item. */
    fun tick() {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    /** Very fine ticks: continuous selection while dragging. */
    fun gestureTick() {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    /** A committed action: install, copy, apply. */
    fun confirm() {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    /** A refused action or an error. */
    fun reject() {
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }

    /** Long press: selection mode, hidden gestures. */
    fun longPress() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** Crossing a gesture threshold (pull-to-refresh armed, swipe commit). */
    fun threshold() {
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    /** A gesture finished (pager settled, drag released). */
    fun gestureEnd() {
        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
    }

    /** Toggling a switch: different feedback for on and off. */
    fun toggle(on: Boolean) {
        haptics.performHapticFeedback(
            if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        )
    }
}

/** Remembers the app's haptic helper bound to the current view. */
@Composable
fun rememberRutheniaHaptics(): RutheniaHaptics {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { RutheniaHaptics(haptics) }
}
