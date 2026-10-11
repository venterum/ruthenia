// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaSpacing

/**
 * App row skeleton — mirrors the shape of [AppRow], so loading does not
 * "jump" when data appears. Uses the shared shimmer phase
 * [rememberSkeletonShimmer] so all blocks on the screen sweep in sync.
 */
@Composable
fun AppRowSkeleton(
    shimmer: Float,
    modifier: Modifier = Modifier,
    iconSize: Dp = 48.dp,
) {
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RutheniaSpacing.lg, vertical = RutheniaSpacing.xs)
            .padding(RutheniaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(
            modifier = Modifier.size(iconSize),
            shimmer = shimmer,
            color = placeholder,
            shape = rutheniaAppIconShape(iconSize),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = RutheniaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RutheniaSpacing.sm),
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(16.dp),
                shimmer = shimmer,
                color = placeholder,
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(12.dp),
                shimmer = shimmer,
                color = placeholder,
            )
        }
    }
}

/**
 * App row list skeleton: [count] placeholders with the same rhythm as
 * the real list. Screens show it instead of a flickering empty
 * state until Room emits the first value.
 */
@Composable
fun AppRowListSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 6,
    shimmer: Float = rememberSkeletonShimmer(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RutheniaSpacing.sm),
    ) {
        repeat(count) {
            AppRowSkeleton(shimmer = shimmer)
        }
    }
}
