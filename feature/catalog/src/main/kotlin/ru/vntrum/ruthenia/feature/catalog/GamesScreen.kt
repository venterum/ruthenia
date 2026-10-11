/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vntrum.ruthenia.core.data.repository.HomeSection
import com.vntrum.ruthenia.core.designsystem.component.LocalRutheniaBottomContentPadding
import com.vntrum.ruthenia.core.designsystem.component.SectionHeader
import com.vntrum.ruthenia.core.designsystem.component.SkeletonBlock
import com.vntrum.ruthenia.core.designsystem.component.StateMessage
import com.vntrum.ruthenia.core.designsystem.component.rememberSkeletonShimmer
import com.vntrum.ruthenia.core.designsystem.text.appCountLabel

/**
 * "Games" tab: a "Popular games" rail and genre rails built from fixed catalog
 * queries. Every section header opens the full collection.
 */
@Composable
fun GamesScreen(
    onAppClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
    viewModel: GamesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        GamesTopBar()

        GamesContent(
            state = state,
            onRefresh = { viewModel.load(forceRefresh = true) },
            onAppClick = onAppClick,
            onCollectionClick = onCollectionClick,
        )
    }
}

/** Header of the Games page: the title, in the same style as feed sections. */
@Composable
private fun GamesTopBar() {
    SectionHeader(
        text = stringResource(R.string.games_title),
        showAccent = true,
        largeTitle = true,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamesContent(
    state: GamesUiState,
    onRefresh: () -> Unit,
    onAppClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading && state.sections.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading && state.sections.isEmpty() -> GamesSkeleton()

            state.sections.isEmpty() -> StateMessage(
                title = stringResource(R.string.common_error_title),
                text = state.error ?: stringResource(R.string.games_error),
                icon = painterResource(R.drawable.ic_refresh),
                actionLabel = stringResource(R.string.common_retry),
                onAction = onRefresh,
            )

            else -> GamesFeed(
                sections = state.sections,
                onAppClick = onAppClick,
                onCollectionClick = onCollectionClick,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GamesFeed(
    sections: List<HomeSection>,
    onAppClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = LocalRutheniaBottomContentPadding.current,
        ),
    ) {
        sections.forEach { section ->
            stickyHeader(key = "header-${section.id}") {
                FeedSectionHeader(
                    title = feedSectionTitle(section.id, section.title),
                    subtitle = appCountLabel(section.apps.size),
                    showAccent = true,
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

        item(key = "games-footer") { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

/** Games skeleton: a title and softly pulsing poster rails. */
@Composable
private fun GamesSkeleton() {
    val shimmer = rememberSkeletonShimmer()
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        repeat(2) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(140.dp)
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
}
