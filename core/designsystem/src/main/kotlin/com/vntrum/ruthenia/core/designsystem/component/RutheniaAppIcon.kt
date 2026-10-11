// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Stable key that pairs an app icon in a list or a rail with the hero icon on
 * the app details screen (see the `SharedTransitionLayout` in `RutheniaApp`).
 * The package name is the app's identity: display names can change, and only
 * one element with a given key may be visible at a time, so callers must only
 * use it on screens where a package appears once.
 */
fun appIconTransitionKey(packageName: String): String = "app-icon-$packageName"

/**
 * App icon — shape follows the user's icon shape setting (round by default),
 * in all lists and on all screens (catalog, search, library, app detail).
 * Until the image loads or if it is missing, [fallbackIcon] is shown.
 *
 * The app's own brand mark is a separate component ([RutheniaBrandMark]) and
 * is always round.
 */
@Composable
fun RutheniaAppIcon(
    iconUrl: String?,
    fallbackIcon: Painter,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(rutheniaAppIconShape(size))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center,
    ) {
        if (iconUrl.isNullOrBlank()) {
            Icon(
                painter = fallbackIcon,
                contentDescription = null,
                modifier = Modifier.size(size / 2),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
