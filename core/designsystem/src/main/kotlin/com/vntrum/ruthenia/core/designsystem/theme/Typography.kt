// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.vntrum.ruthenia.core.designsystem.R

/**
 * Branded display font — Onest (SIL OFL, full Cyrillic).
 *
 * The font is applied only to `display`/`headline` styles: screen and
 * hero-block headings get a recognizable character, while long text stays in
 * the system font — so the interface does not get "heavy" and stays readable.
 *
 * The file is variable, so each weight is declared through
 * `FontVariation.Settings` with an exact `wght` axis; no synthesis occurs.
 */
private val OnestFamily = FontFamily(
    Font(
        resId = R.font.onest,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.onest,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = R.font.onest,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId = R.font.onest,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/**
 * Material 3 Expressive typography scale with branded `display`/`headline`
 * styles. Emphasized variants (used by section headers and hero)
 * are overridden together with the base ones.
 */
internal val RutheniaTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.branded(),
        displayMedium = base.displayMedium.branded(),
        displaySmall = base.displaySmall.branded(),
        headlineLarge = base.headlineLarge.branded(),
        headlineMedium = base.headlineMedium.branded(),
        headlineSmall = base.headlineSmall.branded(),
        displayLargeEmphasized = base.displayLargeEmphasized.branded(),
        displayMediumEmphasized = base.displayMediumEmphasized.branded(),
        displaySmallEmphasized = base.displaySmallEmphasized.branded(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.branded(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.branded(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.branded(),
    )
}

private fun TextStyle.branded(): TextStyle = copy(fontFamily = OnestFamily)
