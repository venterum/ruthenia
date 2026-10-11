// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaSpacing

/**
 * A universal app row in a vertical list: icon, title,
 * subtitle, and arbitrary metadata and action slots.
 *
 * Replaces three nearly identical implementations (search result, collection
 * item, library card), so all app lists in the app
 * look and behave the same.
 *
 * @param meta slot below the subtitle — rating, update status.
 * @param trailing right-hand slot — action button, status badge.
 * @param iconModifier applied to the icon — used for shared element transitions.
 */
@Composable
fun AppRow(
    title: String,
    iconUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconFallback: Painter,
    iconSize: Dp = 48.dp,
    iconModifier: Modifier = Modifier,
    maxTitleLines: Int = 1,
    maxSubtitleLines: Int = 2,
    meta: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RutheniaSpacing.lg, vertical = RutheniaSpacing.xs),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RutheniaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RutheniaAppIcon(
                iconUrl = iconUrl,
                fallbackIcon = iconFallback,
                modifier = iconModifier,
                size = iconSize,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = RutheniaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(RutheniaSpacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = maxTitleLines,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = maxSubtitleLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                meta?.invoke()
            }
            trailing?.invoke()
        }
    }
}
