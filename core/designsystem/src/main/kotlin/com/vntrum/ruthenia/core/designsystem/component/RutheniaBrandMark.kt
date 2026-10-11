// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.core.designsystem.R

/**
 * The app's own brand mark: a theme-tinted, background-less glyph.
 *
 * Unlike catalog app icons, it does not follow the user's icon shape setting
 * and is always drawn the same way.
 */
@Composable
fun RutheniaBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        painter = painterResource(R.drawable.ic_brand_mark),
        contentDescription = contentDescription,
        tint = tint,
        // The source vector keeps the adaptive-icon safe zone, so the glyph is
        // scaled up to fill the given size.
        modifier = modifier
            .size(size)
            .scale(1.3f),
    )
}
