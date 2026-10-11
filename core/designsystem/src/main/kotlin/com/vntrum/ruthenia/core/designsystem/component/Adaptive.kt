// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaBreakpoints

/**
 * Window width class by Material 3 boundaries: `compact` (< 600dp), `medium`
 * (600–839dp), `expanded` (≥ 840dp).
 *
 * The value comes from the platform `LocalConfiguration.screenWidthDp`, not
 * from the local width of `BoxWithConstraints`: this way the threshold accounts for the real window
 * (including multitasking and foldables), not just the content area.
 */
enum class RutheniaWidthClass { Compact, Medium, Expanded }

@Composable
fun rememberRutheniaWidthClass(): RutheniaWidthClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < RutheniaBreakpoints.Medium.value -> RutheniaWidthClass.Compact
        widthDp < RutheniaBreakpoints.Expanded.value -> RutheniaWidthClass.Medium
        else -> RutheniaWidthClass.Expanded
    }
}

/** Maximum width of single-column content on wide screens. */
val RutheniaContentMaxWidth: Dp = RutheniaBreakpoints.Expanded

/**
 * Constrains single-column content to a comfortable reading width so that on
 * tablets and foldables lists do not stretch to the full width. Centering
 * is handled by the parent (`Arrangement`/`align`).
 */
fun Modifier.rutheniaContentWidth(): Modifier =
    this.widthIn(max = RutheniaContentMaxWidth)

/**
 * Maximum width of two-column app detail content: on very
 * wide windows the columns do not stretch to the full width but stay together.
 */
val RutheniaTwoPaneMaxWidth: Dp = 1200.dp

/**
 * Bottom content padding under the floating tab bar. The shell sets the value
 * once: on a phone it is the bar height, on a wide screen a regular padding.
 * The default (16dp) targets screens without a tab bar —
 * app detail, collections, settings — so the local can be read
 * anywhere without introducing custom "magic" constants.
 */
val LocalRutheniaBottomContentPadding = staticCompositionLocalOf { 16.dp }
