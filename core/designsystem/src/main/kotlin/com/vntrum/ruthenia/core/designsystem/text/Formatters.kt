// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.text

import java.text.NumberFormat
import java.util.Locale

/**
 * Locale-aware rating formatting: always one digit after the separator.
 * In the Russian locale that is "4,5", in English — "4.5"; a hard-coded
 * `"%.1f"` produced a period in both.
 */
fun formatRating(rating: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).run {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
        format(rating)
    }
