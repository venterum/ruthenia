/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.library

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vntrum.ruthenia.core.data.model.InstalledApp
import com.vntrum.ruthenia.core.data.model.UpdateStatus
import com.vntrum.ruthenia.core.designsystem.component.ExpressiveCard
import com.vntrum.ruthenia.core.designsystem.component.RutheniaAppIcon
import com.vntrum.ruthenia.core.designsystem.component.StateMessage
import com.vntrum.ruthenia.core.designsystem.component.appIconTransitionKey
import com.vntrum.ruthenia.core.designsystem.component.rutheniaContentWidth
import com.vntrum.ruthenia.core.designsystem.haptics.rememberRutheniaHaptics
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaPillShape
import com.vntrum.ruthenia.core.designsystem.text.appCountLabel

/** Filter for the "My apps" list. */
private enum class MyAppsFilter { ALL, UPDATES, UP_TO_DATE }

/** Sort order for the "My apps" list. */
private enum class MyAppsSort { NAME, UPDATES }

/**
 * "My apps": installed apps with update status, search, filters, sorting, and a
 * list/grid toggle. Imports already-installed apps that are in RuStore.
 *
 * The scopes drive the shared element transition of the app icon into the
 * details hero (see `SharedTransitionLayout` in `RutheniaApp`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MyAppsScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onAppClick: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motion = MaterialTheme.motionScheme
    val haptics = rememberRutheniaHaptics()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(MyAppsFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(MyAppsSort.NAME) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val visible = remember(library, query, filter, sort) {
        library
            .asSequence()
            .filter { app ->
                query.isBlank() ||
                    (app.name ?: app.packageName).contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            }
            .filter { app ->
                when (filter) {
                    MyAppsFilter.ALL -> true
                    MyAppsFilter.UPDATES -> app.status == UpdateStatus.UPDATE_AVAILABLE
                    MyAppsFilter.UP_TO_DATE -> app.status == UpdateStatus.UP_TO_DATE
                }
            }
            .sortedWith(
                when (sort) {
                    MyAppsSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) {
                        it.name ?: it.packageName
                    }

                    MyAppsSort.UPDATES -> compareByDescending<InstalledApp> {
                        it.status == UpdateStatus.UPDATE_AVAILABLE
                    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: it.packageName }
                },
            )
            .toList()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.my_apps_title)) },
                subtitle = { Text(appCountLabel(library.size)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .rutheniaContentWidth()
                    .fillMaxWidth(),
            ) {
                FilledTonalButton(
                    onClick = {
                        haptics.confirm()
                        viewModel.importInstalledApps()
                    },
                    enabled = importProgress == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_library),
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(R.string.my_apps_find),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                importProgress?.let { progress ->
                    ImportProgressCard(
                        progress = progress,
                        onCancel = viewModel::cancelImport,
                    )
                }

                if (library.isNotEmpty()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text(stringResource(R.string.my_apps_search_hint)) },
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
                                IconButton(onClick = { query = "" }) {
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
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(
                                listOf(
                                    MyAppsFilter.ALL to R.string.my_apps_filter_all,
                                    MyAppsFilter.UPDATES to R.string.my_apps_filter_updates,
                                    MyAppsFilter.UP_TO_DATE to R.string.my_apps_filter_uptodate,
                                ),
                            ) { (value, labelRes) ->
                                FilterChip(
                                    selected = filter == value,
                                    onClick = {
                                        haptics.tick()
                                        filter = value
                                    },
                                    label = { Text(stringResource(labelRes)) },
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = {
                                    haptics.tick()
                                    menuExpanded = true
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vert),
                                    contentDescription = stringResource(R.string.my_apps_sort),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.my_apps_sort_name)) },
                                    onClick = {
                                        haptics.tick()
                                        sort = MyAppsSort.NAME
                                        menuExpanded = false
                                    },
                                    leadingIcon = {
                                        if (sort == MyAppsSort.NAME) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_check),
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.my_apps_sort_updates)) },
                                    onClick = {
                                        haptics.tick()
                                        sort = MyAppsSort.UPDATES
                                        menuExpanded = false
                                    },
                                    leadingIcon = {
                                        if (sort == MyAppsSort.UPDATES) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_check),
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (grid) {
                                                    R.string.my_apps_view_list
                                                } else {
                                                    R.string.my_apps_view_grid
                                                },
                                            ),
                                        )
                                    },
                                    onClick = {
                                        haptics.tick()
                                        grid = !grid
                                        menuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                when {
                    library.isEmpty() -> StateMessage(
                        title = stringResource(R.string.my_apps_empty_title),
                        text = stringResource(R.string.my_apps_empty_text),
                        icon = painterResource(R.drawable.ic_library),
                    )

                    visible.isEmpty() -> StateMessage(
                        title = stringResource(R.string.my_apps_empty_title),
                        text = stringResource(R.string.my_apps_no_results),
                        icon = painterResource(R.drawable.ic_search),
                    )

                    else -> AnimatedContent(
                        targetState = grid,
                        transitionSpec = {
                            (
                                fadeIn(animationSpec = motion.defaultEffectsSpec()) +
                                    scaleIn(
                                        initialScale = 0.98f,
                                        animationSpec = motion.defaultSpatialSpec(),
                                    )
                                ) togetherWith fadeOut(animationSpec = motion.fastEffectsSpec())
                        },
                        label = "myAppsLayout",
                    ) { isGrid ->
                        if (isGrid) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 150.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = 16.dp,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                gridItems(visible, key = { it.packageName }) { app ->
                                    LibraryGridTile(
                                        app = app,
                                        // Only the current layout carries shared
                                        // elements: during the crossfade both
                                        // layouts are composed, and two elements
                                        // with one key must not coexist.
                                        iconModifier = if (isGrid == grid) {
                                            with(sharedTransitionScope) {
                                                Modifier.sharedElement(
                                                    sharedContentState = rememberSharedContentState(
                                                        appIconTransitionKey(app.packageName),
                                                    ),
                                                    animatedVisibilityScope = animatedVisibilityScope,
                                                )
                                            }
                                        } else {
                                            Modifier
                                        },
                                        onClick = { onAppClick(app.packageName) },
                                        onUpdate = { viewModel.update(app) },
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                            ) {
                                items(visible, key = { it.packageName }) { app ->
                                    LibraryCard(
                                        app = app,
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = motion.defaultEffectsSpec(),
                                            placementSpec = motion.defaultSpatialSpec(),
                                            fadeOutSpec = motion.fastEffectsSpec(),
                                        ),
                                        // See the grid branch: shared elements
                                        // live only on the current layout.
                                        iconModifier = if (isGrid == grid) {
                                            with(sharedTransitionScope) {
                                                Modifier.sharedElement(
                                                    sharedContentState = rememberSharedContentState(
                                                        appIconTransitionKey(app.packageName),
                                                    ),
                                                    animatedVisibilityScope = animatedVisibilityScope,
                                                )
                                            }
                                        } else {
                                            Modifier
                                        },
                                        onClick = { onAppClick(app.packageName) },
                                        onUpdate = { viewModel.update(app) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Grid tile: icon, name, status, and an update button when available. */
@Composable
private fun LibraryGridTile(
    app: InstalledApp,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onUpdate: () -> Unit,
) {
    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RutheniaAppIcon(
                iconUrl = app.iconUrl,
                fallbackIcon = painterResource(R.drawable.ic_library),
                modifier = iconModifier,
                size = 64.dp,
            )
            Text(
                text = app.name ?: app.packageName,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            StatusIndicator(app.status)
            if (app.status == UpdateStatus.UPDATE_AVAILABLE) {
                FilledTonalButton(onClick = onUpdate) {
                    Text(stringResource(R.string.updates_start))
                }
            }
        }
    }
}
