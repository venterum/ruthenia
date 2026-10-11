/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vntrum.ruthenia.core.data.model.InstalledApp
import com.vntrum.ruthenia.core.designsystem.component.AppRow
import com.vntrum.ruthenia.core.designsystem.component.LocalRutheniaBottomContentPadding
import com.vntrum.ruthenia.core.designsystem.component.RutheniaAppIcon
import com.vntrum.ruthenia.core.designsystem.component.RutheniaContentMaxWidth
import com.vntrum.ruthenia.core.designsystem.component.RutheniaSectionCard
import com.vntrum.ruthenia.core.designsystem.component.SectionHeader
import com.vntrum.ruthenia.core.designsystem.component.SkeletonBlock
import com.vntrum.ruthenia.core.designsystem.component.StateMessage
import com.vntrum.ruthenia.core.designsystem.component.appIconTransitionKey
import com.vntrum.ruthenia.core.designsystem.component.rememberSkeletonShimmer
import com.vntrum.ruthenia.core.designsystem.haptics.rememberRutheniaHaptics
import com.vntrum.ruthenia.core.designsystem.text.appCountLabel
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaSpacing
import com.vntrum.ruthenia.core.updates.isActionable

/**
 * Library — the app management hub.
 *
 * At the top is the download manager (when there are active, ready, or failed
 * downloads), then available updates with an action right in the hub, and a
 * rail of installed apps. Counters live in the section subtitles — the tab has
 * no separate wordmark, and its role is clear from the tab bar label.
 *
 * Shared element transitions live only on the rail: an app with an update
 * appears both in the update rows and in the rail, and two elements with the
 * same key must not be visible at once.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenApps: () -> Unit,
    onOpenUpdates: () -> Unit,
    onAppClick: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val libraryLoaded by viewModel.libraryLoaded.collectAsStateWithLifecycle()
    val updateRows by viewModel.updateRows.collectAsStateWithLifecycle()
    val downloads by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val installingPackage by viewModel.installingPackage.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val confirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // One quiet run per session so the update counter stays current.
    LaunchedEffect(Unit) {
        viewModel.autoCheckOnce()
    }

    // We launch the system install dialog from the Activity — it can't be bypassed.
    LaunchedEffect(confirmation) {
        confirmation?.let { intent ->
            runCatching { context.startActivity(intent) }
            viewModel.onConfirmationLaunched()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val actionable = updateRows.count { it.download.isActionable }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = RutheniaContentMaxWidth)
                    .fillMaxWidth()
                    .padding(innerPadding)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(RutheniaSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(RutheniaSpacing.md),
            ) {
                DownloadManagerCard(
                    tasks = downloads,
                    installingPackage = installingPackage,
                    onInstall = viewModel::install,
                    onCancel = viewModel::cancelDownload,
                    onRetry = viewModel::retry,
                )

                if (!libraryLoaded) {
                    HubSkeleton()
                } else {
                    if (updateRows.isNotEmpty()) {
                        SectionHeader(
                            text = stringResource(R.string.library_updates_title),
                            subtitle = updatesSubtitle(count = updateRows.size, actionable = actionable),
                            showAccent = true,
                            trailing = {
                                TextButton(onClick = onOpenUpdates) {
                                    Text(stringResource(R.string.library_see_all))
                                }
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        updateRows.take(HUB_UPDATE_PREVIEW_COUNT).forEach { row ->
                            // No shared element here: the same app can also be
                            // in the rail below (see the screen doc).
                            AppRow(
                                title = row.app.name ?: row.app.packageName,
                                subtitle = updateVersionLine(row.app),
                                iconUrl = row.app.iconUrl,
                                iconFallback = painterResource(R.drawable.ic_library),
                                onClick = { onAppClick(row.app.packageName) },
                                trailing = {
                                    DownloadActionButton(
                                        state = row.download,
                                        installing = installingPackage == row.app.packageName,
                                        startLabel = stringResource(R.string.updates_start),
                                        onStart = { viewModel.update(row.app) },
                                        onCancel = { viewModel.cancelDownload(row.app) },
                                        onInstall = { viewModel.install(row.app) },
                                    )
                                },
                            )
                        }
                    } else {
                        LibrarySectionCard(
                            title = stringResource(R.string.library_updates_title),
                            subtitle = stringResource(R.string.library_updates_all_ok),
                            iconRes = R.drawable.ic_download,
                            onClick = onOpenUpdates,
                        )
                    }

                    if (library.isNotEmpty()) {
                        SectionHeader(
                            text = stringResource(R.string.library_apps_title),
                            subtitle = appCountLabel(library.size),
                            trailing = {
                                TextButton(onClick = onOpenApps) {
                                    Text(stringResource(R.string.library_see_all))
                                }
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        LibraryAppsRail(
                            apps = library,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onAppClick = onAppClick,
                        )
                    } else {
                        StateMessage(
                            title = stringResource(R.string.my_apps_empty_title),
                            text = stringResource(R.string.my_apps_empty_text),
                            icon = painterResource(R.drawable.ic_library),
                            actionLabel = stringResource(R.string.my_apps_find),
                            onAction = viewModel::importInstalledApps,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(LocalRutheniaBottomContentPadding.current))
            }
        }
    }
}

/** How many updates to show right in the hub before going to the full section. */
private const val HUB_UPDATE_PREVIEW_COUNT = 3

/**
 * Compact section card: icon, title, and summary. The visuals come from the
 * shared [RutheniaSectionCard], so the library and settings look the same.
 */
@Composable
private fun LibrarySectionCard(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val haptics = rememberRutheniaHaptics()
    RutheniaSectionCard(
        title = title,
        subtitle = subtitle,
        painter = painterResource(iconRes),
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = modifier,
        highlight = highlight,
    )
}

/**
 * Rail of installed apps: icons with names. More compact than a list, and it
 * fills the hub with content when there are no updates.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LibraryAppsRail(
    apps: List<InstalledApp>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onAppClick: (String) -> Unit,
) {
    val haptics = rememberRutheniaHaptics()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(RutheniaSpacing.sm),
    ) {
        items(apps, key = { it.packageName }) { app ->
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .clip(MaterialTheme.shapes.large)
                    .clickable {
                        haptics.tick()
                        onAppClick(app.packageName)
                    }
                    .animateItem()
                    .padding(vertical = RutheniaSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RutheniaAppIcon(
                    iconUrl = app.iconUrl,
                    fallbackIcon = painterResource(R.drawable.ic_library),
                    modifier = with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(
                                appIconTransitionKey(app.packageName),
                            ),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    },
                    size = 56.dp,
                )
                Text(
                    text = app.name ?: app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun updatesSubtitle(count: Int, actionable: Int): String = when {
    count == 0 -> stringResource(R.string.library_updates_all_ok)
    actionable > 0 -> stringResource(R.string.library_updates_ready, updateCountLabel(count))
    else -> updateCountLabel(count)
}

/**
 * Hub skeleton until the library's first emission: a label and a section card,
 * then a rail of tiles. Prevents the empty state from flashing before the data.
 */
@Composable
private fun HubSkeleton() {
    val shimmer = rememberSkeletonShimmer()
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(verticalArrangement = Arrangement.spacedBy(RutheniaSpacing.md)) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(20.dp),
            color = placeholder,
            shimmer = shimmer,
            shape = MaterialTheme.shapes.small,
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            color = placeholder,
            shimmer = shimmer,
            shape = MaterialTheme.shapes.large,
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(20.dp),
            color = placeholder,
            shimmer = shimmer,
            shape = MaterialTheme.shapes.small,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(RutheniaSpacing.md)) {
            repeat(2) {
                SkeletonBlock(
                    modifier = Modifier
                        .weight(1f)
                        .height(128.dp),
                    color = placeholder,
                    shimmer = shimmer,
                    shape = MaterialTheme.shapes.large,
                )
            }
        }
    }
}
