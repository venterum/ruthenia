// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.core.designsystem.motion.pressScaleEffect

/** Navigation tab description (bottom bar or rail on wide screens). */
data class RutheniaNavItem(
    val label: String,
    val icon: Painter,
    /** Badge count (e.g. available updates). 0 — no badge. */
    val badgeCount: Int = 0,
)

/**
 * Bottom content padding so the floating tab bar does not cover the last
 * list item. The content scrolls "under" the bar.
 */
val FloatingToolbarBottomPadding = 140.dp

/**
 * Bottom content padding for the standard navigation bar (80dp tall plus a
 * small gap), so the last list item is not covered.
 */
val NavigationBarBottomPadding = 96.dp

/**
 * Screen width threshold beyond which the bottom bar gives way to
 * a vertical rail. 600dp is the compact/medium boundary in Material 3.
 */
val RutheniaRailMinWidth = 600.dp

/**
 * Material 3 Expressive floating tab bar.
 *
 * It is a pill of `surfaceContainerHigh` with a shadow, inside which the active
 * item expands into a "capsule": a label is added to the icon,
 * the container is tinted `primaryContainer`, and the whole cell slightly
 * grows. Selection is returned as an index so it can be tied to the
 * `PagerState` position.
 */
@Composable
fun RutheniaFloatingTabBar(
    items: List<RutheniaNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { index, item ->
                FloatingTabItem(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun FloatingTabItem(
    item: RutheniaNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = motion.fastSpatialSpec(),
        label = "tabScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "tabContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "tabContent",
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.pressScaleEffect(pressedScale = 0.92f),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = selectionScale
                    scaleY = selectionScale
                }
                .animateContentSize(animationSpec = motion.defaultSpatialSpec<IntSize>())
                .heightIn(min = 56.dp)
                .padding(
                    horizontal = if (selected) 18.dp else 14.dp,
                    vertical = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BadgedBox(
                badge = {
                    if (item.badgeCount > 0) {
                        AnimatedCountBadge(item.badgeCount)
                    }
                },
            ) {
                Icon(
                    painter = item.icon,
                    contentDescription = item.label,
                    modifier = Modifier.size(24.dp),
                    tint = contentColor,
                )
            }
            if (selected) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = contentColor,
                )
            }
        }
    }
}

/**
 * Standard Material 3 navigation bar — the "normal" navbar variant.
 *
 * Same items, selection logic, and badges as [RutheniaFloatingTabBar]; only the
 * container differs: full width, docked to the bottom edge, labels always
 * visible.
 */
@Composable
fun RutheniaBottomNavigationBar(
    items: List<RutheniaNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (item.badgeCount > 0) {
                                AnimatedCountBadge(item.badgeCount)
                            }
                        },
                    ) {
                        Icon(
                            painter = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                label = { Text(item.label) },
            )
        }
    }
}

/**
 * Vertical rail for wide screens (tablets, foldables, landscape).
 * The same item list and the same selection logic as the bottom bar —
 * only the layout differs.
 */
@Composable
fun RutheniaNavigationRail(
    items: List<RutheniaNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { index, item ->
                NavigationRailItem(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (item.badgeCount > 0) {
                                    AnimatedCountBadge(item.badgeCount)
                                }
                            },
                        ) {
                            Icon(
                                painter = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                    label = { Text(item.label) },
                )
            }
        }
    }
}
