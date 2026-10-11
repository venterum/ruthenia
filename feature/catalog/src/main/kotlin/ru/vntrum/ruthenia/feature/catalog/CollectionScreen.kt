/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.catalog

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vntrum.ruthenia.core.designsystem.component.AppRow
import com.vntrum.ruthenia.core.designsystem.component.RutheniaLoadingIndicator
import com.vntrum.ruthenia.core.designsystem.component.StateMessage
import com.vntrum.ruthenia.core.designsystem.component.appIconTransitionKey
import com.vntrum.ruthenia.core.designsystem.component.rutheniaContentWidth
import com.vntrum.ruthenia.core.designsystem.text.appCountLabel

/**
 * Collection screen: all apps of the selected home feed section. The section is
 * loaded by id from the cache, so the screen survives process death.
 *
 * The scopes drive the shared element transition of the app icon into the
 * details hero (see `SharedTransitionLayout` in `RutheniaApp`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun CollectionScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onAppClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val section = state.section
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        if (section != null) {
                            feedSectionTitle(section.id, section.title)
                        } else {
                            stringResource(R.string.collection_title)
                        },
                    )
                },
                subtitle = section?.let { loaded ->
                    { Text(appCountLabel(loaded.apps.size)) }
                },
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
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    RutheniaLoadingIndicator()
                }

                state.failed || section == null -> StateMessage(
                    title = stringResource(R.string.common_error_title),
                    text = stringResource(R.string.home_home_error),
                    icon = painterResource(R.drawable.ic_refresh),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = viewModel::load,
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .rutheniaContentWidth()
                        .fillMaxWidth()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                ) {
                    items(section.apps, key = { it.packageName }) { app ->
                        AppRow(
                            title = app.name,
                            subtitle = app.shortDescription,
                            iconUrl = app.iconUrl,
                            iconFallback = painterResource(R.drawable.ic_library),
                            iconSize = 52.dp,
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
                }
            }
        }
    }
}
