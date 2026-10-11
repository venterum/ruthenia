/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vntrum.ruthenia.core.data.identity.LocalIdentity
import com.vntrum.ruthenia.core.data.identity.tokenRotation
import com.vntrum.ruthenia.core.data.repository.HomeSection
import com.vntrum.ruthenia.core.designsystem.component.ExpressiveCard
import com.vntrum.ruthenia.core.designsystem.component.LocalRutheniaBottomContentPadding
import com.vntrum.ruthenia.core.designsystem.component.RutheniaBrandMark
import com.vntrum.ruthenia.core.designsystem.component.RutheniaIconTile
import com.vntrum.ruthenia.core.designsystem.component.RutheniaToken
import com.vntrum.ruthenia.core.designsystem.component.SkeletonBlock
import com.vntrum.ruthenia.core.designsystem.component.StateMessage
import com.vntrum.ruthenia.core.designsystem.component.rememberSkeletonShimmer
import com.vntrum.ruthenia.core.designsystem.component.rutheniaTokenRingByName
import com.vntrum.ruthenia.core.designsystem.text.appCountLabel
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaPillShape
import com.vntrum.ruthenia.core.designsystem.theme.rutheniaTokenColorByName

/**
 * "Apps" tab: the curated catalog feed — a "Recommended" carousel, an updates
 * banner, and category sections. The header has the app mark and the local
 * device token (a profile, not an account); search lives on its own tab.
 */
@Composable
fun AppsScreen(
    onAppClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
    onOpenUpdates: () -> Unit,
    onPrivacyClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAboutClick: () -> Unit,
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateCount by viewModel.updateCount.collectAsStateWithLifecycle()
    val identity by viewModel.identity.collectAsStateWithLifecycle()
    // Category quick chips — everything except the special "Recommended"
    // selection is a category.
    val categories = state.homeSections.filter { !it.featured }

    Column(modifier = Modifier.fillMaxSize()) {
        AppsTopBar(
            identity = identity,
            onProfileClick = onProfileClick,
            onAboutClick = onAboutClick,
        )

        if (categories.isNotEmpty()) {
            CategoryChips(
                categories = categories,
                onCategoryClick = onCollectionClick,
            )
        }

        AppsContent(
            state = state,
            updateCount = updateCount,
            onRefresh = { viewModel.loadHome(forceRefresh = true) },
            onAppClick = onAppClick,
            onCollectionClick = onCollectionClick,
            onOpenUpdates = onOpenUpdates,
            onPrivacyClick = onPrivacyClick,
        )
    }
}

/** Header of the Apps page: the brand mark (opens "About") and the device token. */
@Composable
private fun AppsTopBar(
    identity: LocalIdentity,
    onProfileClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RutheniaBrandMark(
            size = 44.dp,
            scale = 1.55f,
            contentDescription = stringResource(R.string.apps_about),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onAboutClick),
        )
        Spacer(modifier = Modifier.weight(1f))
        RutheniaToken(
            motif = identity.emblem.motif,
            accent = rutheniaTokenColorByName(identity.emblem.accent.name),
            ring = rutheniaTokenRingByName(identity.emblem.ring.name),
            ringAccent = rutheniaTokenColorByName(identity.emblem.ringColor.name),
            rotation = tokenRotation(identity.seed),
            size = 44.dp,
            contentDescription = stringResource(R.string.apps_profile),
            onClick = onProfileClick,
        )
    }
}

/**
 * Category quick chips under the header: rail sections of the feed. Tapping a
 * chip opens the full collection.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryChips(
    categories: List<HomeSection>,
    onCategoryClick: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(bottom = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.id }) { section ->
            AssistChip(
                onClick = { onCategoryClick(section.id) },
                label = { Text(section.title) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(categoryIcon(section.id)),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                shape = RutheniaPillShape,
                border = null,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/**
 * Feed with a pull-to-refresh gesture. While there's no content yet, we show a
 * skeleton — it feels faster than a lone spinner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsContent(
    state: AppsUiState,
    updateCount: Int,
    onRefresh: () -> Unit,
    onAppClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
    onOpenUpdates: () -> Unit,
    onPrivacyClick: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.homeLoading && state.homeSections.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.homeLoading && state.homeSections.isEmpty() -> HomeSkeleton()

            state.homeSections.isEmpty() -> StateMessage(
                title = stringResource(R.string.common_error_title),
                text = state.homeError ?: stringResource(R.string.home_home_error),
                icon = painterResource(R.drawable.ic_refresh),
                actionLabel = stringResource(R.string.common_retry),
                onAction = onRefresh,
            )

            else -> AppsFeed(
                sections = state.homeSections,
                updateCount = updateCount,
                onAppClick = onAppClick,
                onCollectionClick = onCollectionClick,
                onOpenUpdates = onOpenUpdates,
                onPrivacyClick = onPrivacyClick,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppsFeed(
    sections: List<HomeSection>,
    updateCount: Int,
    onAppClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
    onOpenUpdates: () -> Unit,
    onPrivacyClick: () -> Unit,
) {
    val featured = sections.firstOrNull { it.featured }
    val categories = sections.filter { !it.featured }

    LazyColumn(
        // Stable handle for macrobenchmarks (see :macrobenchmark).
        modifier = Modifier
            .fillMaxSize()
            .testTag("apps_feed"),
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = LocalRutheniaBottomContentPadding.current,
        ),
    ) {
        featured?.let { section ->
            item(key = "featured") {
                FeaturedSection(
                    section = section,
                    onAppClick = onAppClick,
                    onCollectionClick = onCollectionClick,
                )
            }
        }

        // Updates from the library — a visible bridge to the "Updates" section.
        if (updateCount > 0) {
            item(key = "updates-banner") {
                UpdatesBanner(count = updateCount, onClick = onOpenUpdates)
            }
        }

        // Section headers "stick" to the top: in a long feed you can always
        // see which section you're in.
        categories.forEach { section ->
            stickyHeader(key = "header-${section.id}") {
                FeedSectionHeader(
                    title = section.title,
                    subtitle = appCountLabel(section.apps.size),
                    onClick = { onCollectionClick(section.id) },
                )
            }
            item(key = "content-${section.id}") {
                CategoryRail(
                    section = section,
                    onAppClick = onAppClick,
                )
            }
        }

        item(key = "home-footer") { HomeFooter(onPrivacyClick = onPrivacyClick) }
    }
}

/**
 * "N apps are awaiting an update" banner: links the Apps page to the library's
 * updates section and fills the feed with a useful action.
 */
@Composable
private fun UpdatesBanner(count: Int, onClick: () -> Unit) {
    ExpressiveCard(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RutheniaIconTile(
                painter = painterResource(R.drawable.ic_download),
                highlight = true,
                size = 44.dp,
                iconSize = 22.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = pluralStringResource(R.plurals.home_updates_banner, count, count),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.home_updates_banner_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Feed skeleton: softly pulsing placeholders. */
@Composable
private fun HomeSkeleton() {
    val shimmer = rememberSkeletonShimmer()
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .width(160.dp)
                    .height(20.dp),
                color = placeholder,
                shimmer = shimmer,
                shape = MaterialTheme.shapes.small,
            )
            SkeletonBlock(
                modifier = Modifier
                    .width(240.dp)
                    .height(184.dp),
                color = placeholder,
                shimmer = shimmer,
                shape = MaterialTheme.shapes.large,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .width(120.dp)
                    .height(20.dp),
                color = placeholder,
                shimmer = shimmer,
                shape = MaterialTheme.shapes.small,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    SkeletonBlock(
                        modifier = Modifier
                            .weight(1f)
                            .height(168.dp),
                        color = placeholder,
                        shimmer = shimmer,
                        shape = MaterialTheme.shapes.large,
                    )
                }
            }
        }
    }
}

/** Closing card of the feed: privacy as an explicit menu item. */
@Composable
private fun HomeFooter(onPrivacyClick: () -> Unit) {
    ExpressiveCard(
        onClick = onPrivacyClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RutheniaIconTile(
                painter = painterResource(R.drawable.ic_check),
                size = 44.dp,
                iconSize = 22.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_footer_privacy),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.home_footer_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
