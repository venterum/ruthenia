// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaSpacing

/**
 * A compact "poster" app card: icon, title, and rating centered.
 * Shared by category feeds, the small state of the
 * "Recommended" carousel, and the "More from the developer" block.
 *
 * The size is set by the calling screen via [width]/[height], and the content
 * adapts to it.
 *
 * @param iconModifier applied to the icon — used for shared element transitions.
 */
@Composable
fun AppPosterCard(
    title: String,
    iconUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rating: Double? = null,
    fallbackIcon: Painter,
    iconModifier: Modifier = Modifier,
    width: Dp = 136.dp,
    height: Dp = 184.dp,
) {
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(height),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(RutheniaSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RutheniaAppIcon(
                iconUrl = iconUrl,
                fallbackIcon = fallbackIcon,
                modifier = iconModifier,
                size = 72.dp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.weight(1f))
            RatingRow(
                rating = rating,
                modifier = Modifier.fillMaxWidth(),
                iconSize = 12.dp,
                textStyle = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
