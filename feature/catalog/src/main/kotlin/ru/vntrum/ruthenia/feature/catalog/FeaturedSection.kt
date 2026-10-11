/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.catalog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.core.data.repository.HomeSection
import com.vntrum.ruthenia.core.designsystem.component.RatingRow
import com.vntrum.ruthenia.core.designsystem.component.RutheniaAppIcon
import com.vntrum.ruthenia.core.designsystem.component.SectionHeader
import com.vntrum.ruthenia.core.designsystem.haptics.rememberRutheniaHaptics
import com.vntrum.ruthenia.core.designsystem.text.formatRating
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaPillShape
import com.vntrum.ruthenia.core.network.CatalogApp
import kotlin.math.roundToInt

/**
 * "Recommended" selection on the stock Material 3 multi-browse carousel.
 *
 * Neighboring cards appear as edges and are masked by the carousel shape, and
 * when the center card expands, the description and metrics fade in smoothly —
 * the reveal progress is read from [CarouselItemDrawInfo] in the draw phase
 * (without recomposition on every scroll frame).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FeaturedSection(
    section: HomeSection,
    onAppClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        SectionHeader(
            text = section.title,
            subtitle = stringResource(R.string.featured_editorial),
            showAccent = true,
            onClick = { onCollectionClick(section.id) },
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))

        val carouselState = rememberCarouselState { section.apps.size }
        val haptics = rememberRutheniaHaptics()

        // A light tick whenever the centered item changes — tap or swipe.
        LaunchedEffect(carouselState) {
            var firstItem = true
            snapshotFlow { carouselState.currentItem }.collect {
                if (!firstItem) haptics.tick()
                firstItem = false
            }
        }

        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = 302.dp,
            minSmallItemWidth = 92.dp,
            maxSmallItemWidth = 108.dp,
            modifier = Modifier.fillMaxWidth(),
            itemSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) { index ->
            val app = section.apps.getOrNull(index) ?: return@HorizontalMultiBrowseCarousel
            FeaturedAppCard(
                app = app,
                rank = index + 1,
                drawInfo = carouselItemDrawInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(184.dp)
                    .maskClip(MaterialTheme.shapes.large),
                onClick = { onAppClick(app.packageName) },
            )
        }
    }
}

/**
 * At what fraction of the size range a card is considered expanded and swaps to
 * the title-on-the-right layout. Kept above [ICON_SLIDE_START_FRACTION] so the
 * icon is already in its corner when the text appears; small side cards sit
 * below [ICON_SLIDE_START_FRACTION] and keep a centered icon.
 */
private const val EXPANDED_THRESHOLD_FRACTION = 0.8f

/**
 * At what fraction of the size range the icon starts sliding from the center to
 * the corner. Below this it stays centered — small side cards never move.
 */
private const val ICON_SLIDE_START_FRACTION = 0.6f

/** Icon size in the card. */
private val FeaturedIconSize = 64.dp

/** Icon padding in the expanded card. */
private val FeaturedIconPadding = 16.dp

/** Top padding of the icon. */
private val FeaturedIconTop = 16.dp

/** Gap between the icon and the text in the expanded card. */
private val FeaturedIconTextGap = 16.dp

/** Padding of the expanded card's text from the left edge (icon + gap). */
private val ExpandedTextStart = FeaturedIconPadding + FeaturedIconSize + FeaturedIconTextGap

/** 0 — the card is compact, 1 — fully expanded. */
private fun revealProgress(drawInfo: CarouselItemDrawInfo): Float {
    val range = drawInfo.maxSize - drawInfo.minSize
    return if (range <= 0f) 1f else ((drawInfo.size - drawInfo.minSize) / range).coerceIn(0f, 1f)
}

@Composable
private fun FeaturedAppCard(
    app: CatalogApp,
    rank: Int,
    drawInfo: CarouselItemDrawInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    // We read the threshold via derivedStateOf: size is snapshot state, so
    // recomposition happens only when the threshold is crossed, not every frame.
    val expanded by remember(drawInfo) {
        derivedStateOf {
            val range = drawInfo.maxSize - drawInfo.minSize
            range > 0f && drawInfo.size >= drawInfo.minSize + range * EXPANDED_THRESHOLD_FRACTION
        }
    }

    Surface(
        onClick = onClick,
        // Stable handle for macrobenchmarks (see :macrobenchmark).
        modifier = modifier.testTag("featured_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    fadeIn(animationSpec = motion.defaultEffectsSpec()) togetherWith
                        fadeOut(animationSpec = motion.fastEffectsSpec())
                },
                label = "featuredCardContent",
            ) { isExpanded ->
                if (isExpanded) {
                    ExpandedFeaturedText(app = app, rank = rank, drawInfo = drawInfo)
                } else {
                    CompactFeaturedText(app = app)
                }
            }

            // A single icon for the whole card: it doesn't participate in the
            // crossfade, but moves from the center to the top-left corner. The
            // carousel places the item at the full width of the large card and
            // clips it by maskRect, so we compute the icon center from the
            // visible window, not the left edge of the layout — otherwise on
            // small cards the icon goes beyond the mask. The position is
            // computed in the layout phase (offset { }), so it's always visible.
            // Drawn last: while the icon slides and the text crossfades, it
            // stays above the text instead of the text running over it.
            RutheniaAppIcon(
                iconUrl = app.iconUrl,
                fallbackIcon = painterResource(R.drawable.ic_library),
                size = FeaturedIconSize,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val reveal = revealProgress(drawInfo)
                        val iconPx = FeaturedIconSize.roundToPx()
                        val mask = drawInfo.maskRect
                        val centeredLeft = (mask.left + mask.right) / 2f - iconPx / 2f
                        val expandedLeft = FeaturedIconPadding.roundToPx().toFloat()
                        // The icon slides to the edge only in the stretch
                        // before the expanded layout appears, so it is already
                        // in place when the title switches to its right side.
                        // Until then it tracks the visible window's center.
                        val slide = ((reveal - ICON_SLIDE_START_FRACTION) /
                            (EXPANDED_THRESHOLD_FRACTION - ICON_SLIDE_START_FRACTION))
                            .coerceIn(0f, 1f)
                        val left = centeredLeft + (expandedLeft - centeredLeft) * slide
                        IntOffset(
                            x = left.roundToInt(),
                            y = FeaturedIconTop.roundToPx(),
                        )
                    },
            )
        }
    }
}

/** Characters at which the title is truncated in the compact card. A hyphen is
 *  excluded: it's often part of the name ("T-Bank", "Gosuslugi-Auto"). */
private const val NAME_STOP_CHARS = ".,:;!?…—–·"

/** Short name for the compact card: everything up to the first punctuation mark. */
private fun compactName(name: String): String {
    val stop = name.indexOfFirst { it in NAME_STOP_CHARS }
    return if (stop > 0) name.substring(0, stop).trimEnd() else name
}

/** Compact state: name and rating under the icon. */
@Composable
private fun CompactFeaturedText(app: CatalogApp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 10.dp, end = 10.dp, top = 88.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = compactName(app.name),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.weight(1f))
        RatingRow(
            rating = app.averageUserRating,
            iconSize = 12.dp,
            textStyle = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Expanded state: name, description, and metrics around the icon. */
@Composable
private fun ExpandedFeaturedText(
    app: CatalogApp,
    rank: Int,
    drawInfo: CarouselItemDrawInfo,
) {
    val reveal: () -> Float = { revealProgress(drawInfo) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = ExpandedTextStart, top = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            app.shortDescription
                ?.takeIf { it.isNotBlank() }
                ?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = reveal() },
                    )
                }
        }

        FeaturedMeta(
            app = app,
            rank = rank,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .graphicsLayer { alpha = reveal() },
        )
    }
}

/** Recommendation metrics: position in the selection, rating, and download count. */
@Composable
private fun FeaturedMeta(
    app: CatalogApp,
    rank: Int,
    modifier: Modifier = Modifier,
) {
    val rating = app.averageUserRating
    val downloads = app.downloadsText

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RutheniaPillShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                text = stringResource(R.string.featured_rank, rank),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        rating?.let {
            Surface(
                shape = RutheniaPillShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_star),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(text = formatRating(it), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        downloads?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
