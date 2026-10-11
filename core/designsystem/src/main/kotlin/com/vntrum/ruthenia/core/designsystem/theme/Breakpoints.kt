// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * Adaptive layout breakpoints. Window width classes (`compact`/`medium`/
 * `expanded`) are Material 3 platform bounds; [TwoPane] is a local
 * boundary beyond which the app detail card lays out into two columns.
 * Screens do not define their own numbers.
 */
object RutheniaBreakpoints {
    /** Material 3 compact/medium boundary: 600dp. */
    val Medium = 600.dp

    /** Material 3 medium/expanded boundary: 840dp. */
    val Expanded = 840.dp

    /** Threshold for the two-column app card layout: 720dp. */
    val TwoPane = 720.dp
}
