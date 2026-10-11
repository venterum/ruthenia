/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.catalog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vntrum.ruthenia.core.designsystem.component.AppRow
import com.vntrum.ruthenia.core.designsystem.component.AppRowListSkeleton
import com.vntrum.ruthenia.core.designsystem.component.ExpressiveCard
import com.vntrum.ruthenia.core.designsystem.component.LocalRutheniaBottomContentPadding
import com.vntrum.ruthenia.core.designsystem.component.RatingRow
import com.vntrum.ruthenia.core.designsystem.component.RutheniaLoadingIndicator
import com.vntrum.ruthenia.core.designsystem.component.SectionHeader
import com.vntrum.ruthenia.core.designsystem.component.SkeletonBlock
import com.vntrum.ruthenia.core.designsystem.component.StateMessage
import com.vntrum.ruthenia.core.designsystem.component.appIconTransitionKey
import com.vntrum.ruthenia.core.designsystem.component.rememberSkeletonShimmer
import com.vntrum.ruthenia.core.designsystem.text.appCountLabel
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaPillShape
import com.vntrum.ruthenia.core.network.CatalogApp
import com.vntrum.ruthenia.core.network.RuStoreSelection

/**
 * "Search" tab: a live search field on top; while the query is empty, the
 * browse content (recent queries, category grid, editorial selections) is
 * shown, and typing replaces it with results.
 *
 * The scopes drive the shared element transition of the app icon into the
 * details hero (see `SharedTransitionLayout` in `RutheniaApp`).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onAppClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.searchHistory.collectAsStateWithLifecycle()
    val motion = MaterialTheme.motionScheme

    Column(modifier = Modifier.fillMaxSize()) {
        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::search,
        )

        AnimatedContent(
            targetState = state.isSearchMode,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (
                    fadeIn(animationSpec = motion.defaultEffectsSpec()) +
                        scaleIn(
                            initialScale = 0.98f,
                            animationSpec = motion.defaultSpatialSpec(),
                        )
                    ) togetherWith fadeOut(animationSpec = motion.fastEffectsSpec())
            },
            label = "searchMode",
        ) { searchMode ->
            if (searchMode) {
                SearchContent(
                    state = state,
                    suggestions = state.categories.map { it.title },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onSuggestion = viewModel::searchSuggestion,
                    onRetry = viewModel::search,
                    onLoadMore = viewModel::loadMore,
                    onAppClick = onAppClick,
                )
            } else {
                BrowseContent(
                    state = state,
                    history = history,
                    onHistoryClick = viewModel::searchSuggestion,
                    onClearHistory = viewModel::clearSearchHistory,
                    onCollectionClick = onCollectionClick,
                    onRetry = viewModel::loadBrowse,
                )
            }
        }
    }
}

/** Search field pinned to the top — search is available from anywhere on the tab. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        singleLine = true,
        shape = RutheniaPillShape,
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.common_clear),
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboard?.hide()
                onSearch()
            },
        ),
    )
}

/**
 * Browse content for the empty query: recent queries above the catalog
 * categories and RuStore editorial selections.
 */
@Composable
private fun BrowseContent(
    state: SearchUiState,
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onCollectionClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.browseLoading && state.categories.isEmpty() -> BrowseSkeleton()

        state.browseFailed -> StateMessage(
            title = stringResource(R.string.common_error_title),
            text = stringResource(R.string.home_home_error),
            icon = painterResource(R.drawable.ic_refresh),
            actionLabel = stringResource(R.string.common_retry),
            onAction = onRetry,
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = LocalRutheniaBottomContentPadding.current,
            ),
        ) {
            if (history.isNotEmpty()) {
                item(key = "history") {
                    SearchHistoryRow(
                        history = history,
                        onHistoryClick = onHistoryClick,
                        onClearHistory = onClearHistory,
                    )
                }
            }

            item(key = "categories-header") {
                SectionHeader(
                    text = stringResource(R.string.home_categories_title),
                    showAccent = true,
                    largeTitle = true,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
                )
            }
            item(key = "categories-grid") {
                CategoriesGrid(
                    categories = state.categories,
                    onCollectionClick = onCollectionClick,
                )
            }

            if (state.selections.isNotEmpty()) {
                item(key = "selections-header") {
                    SectionHeader(
                        text = stringResource(R.string.categories_selections_title),
                        subtitle = stringResource(R.string.categories_selections_source),
                        showAccent = true,
                        largeTitle = true,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
                    )
                }
                item(key = "selections-grid") {
                    SelectionsGrid(
                        selections = state.selections,
                        onCollectionClick = onCollectionClick,
                    )
                }
            }
        }
    }
}

/**
 * Recent query history: queries never leave the device. Shown above the
 * categories while the field is empty.
 */
@Composable
private fun SearchHistoryRow(
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_search_recent),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClearHistory) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.home_search_clear_history),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(history) { historyQuery ->
                AssistChip(
                    onClick = { onHistoryClick(historyQuery) },
                    label = { Text(historyQuery) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
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
}

/**
 * Selections grid: 2 columns of cards with a cover and a caption. Tapping opens
 * the selection in the same collection screen as categories.
 */
@Composable
private fun SelectionsGrid(
    selections: List<RuStoreSelection>,
    onCollectionClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        selections.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { selection ->
                    SelectionCard(
                        selection = selection,
                        onClick = { onCollectionClick(selection.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SelectionCard(
    selection: RuStoreSelection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier,
    ) {
        Column {
            if (selection.coverUrl != null) {
                AsyncImage(
                    model = selection.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.large),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = selection.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                selection.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SearchContent(
    state: SearchUiState,
    suggestions: List<String>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSuggestion: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onAppClick: (String) -> Unit,
) {
    when {
        // While there's no new page, show a skeleton of rows — the screen
        // doesn't "jump" when results appear.
        state.isLoading && state.results.isEmpty() -> AppRowListSkeleton(
            modifier = Modifier.padding(top = 8.dp),
        )

        state.error != null && state.results.isEmpty() -> StateMessage(
            title = stringResource(R.string.common_error_title),
            text = state.error.orEmpty(),
            icon = painterResource(R.drawable.ic_refresh),
            actionLabel = stringResource(R.string.common_retry),
            onAction = onRetry,
        )

        state.results.isNotEmpty() -> LazyColumn(
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = LocalRutheniaBottomContentPadding.current,
            ),
        ) {
            item(key = "results-count") {
                Text(
                    text = appCountLabel(state.results.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            items(state.results, key = { it.packageName }) { app ->
                SearchResultCard(
                    app = app,
                    modifier = Modifier.animateItem(),
                    iconModifier = with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(
                                appIconTransitionKey(app.packageName),
                            ),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    },
                    onClick = { onAppClick(app.packageName) },
                )
            }

            // Loading the next page: the last item in the list appears only
            // once the user has scrolled to the end.
            when {
                state.isLoadingMore -> item(key = "search-loading-more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        RutheniaLoadingIndicator(modifier = Modifier.size(32.dp))
                    }
                }

                // Load-more failure: show "Retry", otherwise the list would
                // silently stop forever.
                state.loadMoreFailed -> item(key = "search-load-more-retry") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FilledTonalButton(onClick = onLoadMore) {
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }

                !state.endReached -> item(key = "search-load-more") {
                    LaunchedEffect(state.results.size) { onLoadMore() }
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }

        state.hasSearched -> StateMessage(
            title = stringResource(R.string.home_search_empty_title),
            text = stringResource(R.string.home_search_empty_text),
            icon = painterResource(R.drawable.ic_search),
            extraContent = if (suggestions.isEmpty()) null else {
                { SuggestionChips(suggestions = suggestions, onSuggestion = onSuggestion) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SuggestionChips(
    suggestions: List<String>,
    onSuggestion: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { title ->
            AssistChip(
                onClick = { onSuggestion(title) },
                label = { Text(title) },
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

/** Search result card — the same shape as library items. */
@Composable
private fun SearchResultCard(
    app: CatalogApp,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppRow(
        title = app.name,
        subtitle = app.shortDescription ?: app.packageName,
        iconUrl = app.iconUrl,
        iconFallback = painterResource(R.drawable.ic_library),
        iconSize = 52.dp,
        iconModifier = iconModifier,
        onClick = onClick,
        modifier = modifier,
        meta = {
            RatingRow(
                rating = app.averageUserRating,
                downloadsText = app.downloadsText,
            )
        },
    )
}

/** Browse skeleton: softly pulsing category tiles and selection cards. */
@Composable
private fun BrowseSkeleton() {
    val shimmer = rememberSkeletonShimmer()
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) {
                    SkeletonBlock(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        color = placeholder,
                        shimmer = shimmer,
                        shape = MaterialTheme.shapes.large,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) {
                    SkeletonBlock(
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp),
                        color = placeholder,
                        shimmer = shimmer,
                        shape = MaterialTheme.shapes.large,
                    )
                }
            }
        }
    }
}
