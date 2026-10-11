// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * A single spacing scale. Screen code takes values from here rather than
 * arbitrary dp — this keeps the interface rhythm consistent across all modules.
 *
 * Step names are intentionally relative: `sm` is the gap between an icon and
 * text, `xxl` is between large sections. Absolute numbers are not part of
 * the contract.
 */
object RutheniaSpacing {
    /** 2dp — micro gap, e.g. between lines of a single caption. */
    val xxs = 2.dp

    /** 4dp — spacing inside compact elements (tag icon + text). */
    val xs = 4.dp

    /** 8dp — base gap inside cards. */
    val sm = 8.dp

    /** 12dp — spacing between cards in a list. */
    val md = 12.dp

    /** 16dp — horizontal content padding and card inner padding. */
    val lg = 16.dp

    /** 20dp — padding for home feed sections. */
    val xl = 20.dp

    /** 24dp — spacing between large blocks. */
    val xxl = 24.dp

    /** 32dp — padding for empty states and landing screens. */
    val xxxl = 32.dp
}
