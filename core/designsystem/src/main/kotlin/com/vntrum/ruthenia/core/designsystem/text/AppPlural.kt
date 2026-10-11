// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.vntrum.ruthenia.core.designsystem.R

/**
 * Number–noun agreement: "1 app", "2 apps", "5 apps". Moved into the
 * design system so the wording is consistent across all sections (catalog,
 * library, updates), and sourced from resources so it inflects and
 * translates correctly.
 */
@Composable
fun appCountLabel(count: Int): String = pluralStringResource(R.plurals.app_count, count, count)
