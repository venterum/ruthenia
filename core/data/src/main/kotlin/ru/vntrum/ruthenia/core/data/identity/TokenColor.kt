/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.identity

/**
 * The token's own colour palette, deliberately independent of the app theme
 * accent: the mark is personal, not themed.
 *
 * Only the names are persisted; the actual shades live in the design system.
 */
enum class TokenColor {
    CRIMSON,
    ROSE,
    AMBER,
    LIME,
    GREEN,
    TEAL,
    SKY,
    INDIGO,
    VIOLET,
    MAGENTA,
}
