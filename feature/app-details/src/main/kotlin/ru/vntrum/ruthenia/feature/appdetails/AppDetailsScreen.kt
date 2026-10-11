/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.appdetails

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vntrum.ruthenia.core.designsystem.component.AppPosterCard
import com.vntrum.ruthenia.core.designsystem.component.ExpressiveCard
import com.vntrum.ruthenia.core.designsystem.component.NoticeSurface
import com.vntrum.ruthenia.core.designsystem.component.RutheniaAppIcon
import com.vntrum.ruthenia.core.designsystem.component.RutheniaContentMaxWidth
import com.vntrum.ruthenia.core.designsystem.component.RutheniaLoadingIndicator
import com.vntrum.ruthenia.core.designsystem.component.RutheniaStatCard
import com.vntrum.ruthenia.core.designsystem.component.RutheniaTag
import com.vntrum.ruthenia.core.designsystem.component.RutheniaTwoPaneMaxWidth
import com.vntrum.ruthenia.core.designsystem.component.SectionHeader
import com.vntrum.ruthenia.core.designsystem.component.SkeletonBlock
import com.vntrum.ruthenia.core.designsystem.component.StateMessage
import com.vntrum.ruthenia.core.designsystem.component.appIconTransitionKey
import com.vntrum.ruthenia.core.designsystem.component.progressSemantics
import com.vntrum.ruthenia.core.designsystem.component.rememberSkeletonShimmer
import com.vntrum.ruthenia.core.designsystem.component.rutheniaAppIconShape
import com.vntrum.ruthenia.core.designsystem.haptics.rememberRutheniaHaptics
import com.vntrum.ruthenia.core.designsystem.text.formatRating
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaBreakpoints
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaPillShape
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaShapeTokens
import com.vntrum.ruthenia.core.network.AppDetails
import com.vntrum.ruthenia.core.network.CatalogApp

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun AppDetailsScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onAppClick: (String) -> Unit,
    viewModel: AppDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val confirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val haptics = rememberRutheniaHaptics()
    val motion = MaterialTheme.motionScheme
    var showUninstallDialog by remember { mutableStateOf(false) }

    // We launch the system install confirmation dialog from the Activity.
    LaunchedEffect(confirmation) {
        confirmation?.let { intent ->
            runCatching { context.startActivity(intent) }
            viewModel.onConfirmationLaunched()
        }
    }

    LaunchedEffect(state.install) {
        if (state.install is InstallUiState.Done) {
            haptics.confirm()
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.app_details_installed_toast),
                actionLabel = context.getString(R.string.app_details_open),
            )
            if (result == SnackbarResult.ActionPerformed) {
                state.details?.packageName?.let { context.launchApp(it) }
            }
            viewModel.dismissInstallState()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val details = state.details
    val scrollState = rememberScrollState()
    val openIntent = remember(details?.packageName) {
        details?.packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }
    }
    // Threshold after which the title appears in the top bar: the hero has scrolled away.
    val collapseThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val showTitle by remember(scrollState) {
        derivedStateOf { scrollState.value > collapseThreshold }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Stable handle for macrobenchmarks (see :macrobenchmark).
        modifier = Modifier.testTag("app_details"),
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = showTitle && details != null,
                        enter = fadeIn(animationSpec = motion.defaultEffectsSpec()),
                        exit = fadeOut(animationSpec = motion.fastEffectsSpec()),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            details?.iconUrl?.takeIf { it.isNotBlank() }?.let { iconUrl ->
                                AsyncImage(
                                    model = iconUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(rutheniaAppIconShape(24.dp)),
                                )
                            }
                            Text(
                                text = details?.name.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { shareApp(context, details) },
                        enabled = details?.packageName != null,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = stringResource(R.string.app_details_share),
                        )
                    }
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isLoading,
                    ) {
                        AnimatedContent(
                            targetState = state.isLoading,
                            transitionSpec = {
                                fadeIn(animationSpec = motion.defaultEffectsSpec()) togetherWith
                                    fadeOut(animationSpec = motion.fastEffectsSpec())
                            },
                            label = "appDetailsRefresh",
                        ) { loading ->
                            if (loading) {
                                RutheniaLoadingIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_refresh),
                                    contentDescription = stringResource(R.string.app_details_refresh),
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            // The action is always at hand: the panel is pinned to the bottom
            // and doesn't scroll away with the content. Aggregated cards have
            // no action.
            if (details != null && !details.isAggregated) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                    // The panel's top corners match the button radius so the
                    // background under it doesn't look rectangular.
                    shape = RoundedCornerShape(
                        topStart = RutheniaShapeTokens.panel,
                        topEnd = RutheniaShapeTokens.panel,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .animateContentSize(motion.defaultSpatialSpec<IntSize>()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DetailsActionBar(
                            state = state,
                            details = details,
                            openAvailable = openIntent != null,
                            onInstall = viewModel::install,
                            onOpen = { openIntent?.let { context.startActivity(it) } },
                            onCancelDownload = viewModel::cancelDownload,
                            onSaveApk = viewModel::exportApk,
                            onReinstall = viewModel::install,
                            onUninstall = { showUninstallDialog = true },
                            onToggleUpdateVisibility = viewModel::toggleUpdateVisibility,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading && details == null -> DetailsSkeleton()

                details == null -> StateMessage(
                    title = stringResource(R.string.app_details_error_title),
                    text = state.error ?: stringResource(R.string.app_details_load_error),
                    icon = painterResource(R.drawable.ic_refresh),
                    actionLabel = stringResource(R.string.app_details_retry),
                    onAction = viewModel::refresh,
                )

                else -> PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    DetailsContent(
                        details = details,
                        state = state,
                        scrollState = scrollState,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onAppClick = onAppClick,
                    )
                }
            }
        }
    }

    if (showUninstallDialog) {
        val packageName = details?.packageName
        AlertDialog(
            onDismissRequest = { showUninstallDialog = false },
            title = { Text(stringResource(R.string.app_details_uninstall_title)) },
            text = { Text(stringResource(R.string.app_details_uninstall_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUninstallDialog = false
                        packageName?.let { context.uninstallApp(it) }
                    },
                ) {
                    Text(stringResource(R.string.app_details_uninstall_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }) {
                    Text(stringResource(R.string.app_details_cancel))
                }
            },
        )
    }
}

/**
 * Screen content. On wide screens (tablets, foldables) it's laid out in two
 * columns: primary content and the action on the left, description and
 * developer on the right.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DetailsContent(
    details: AppDetails,
    state: AppDetailsUiState,
    scrollState: ScrollState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onAppClick: (String) -> Unit,
) {
    val description = details.fullDescription?.takeIf { it.isNotBlank() }
        ?: details.shortDescription?.takeIf { it.isNotBlank() }
    val changelog = details.whatsNew?.trim().orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= RutheniaBreakpoints.TwoPane
        val spacing = Arrangement.spacedBy(20.dp)
        val contentMaxWidth = if (twoPane) RutheniaTwoPaneMaxWidth else RutheniaContentMaxWidth

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = contentMaxWidth)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = spacing,
        ) {
            val primary: @Composable () -> Unit = {
                HeroHeader(
                    details = details,
                    state = state,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
                if (state.isOffline) {
                    OfflineNotice()
                }
                StatGrid(state = state, details = details)
                if (details.screenshots.isNotEmpty()) {
                    ScreenshotCarousel(urls = details.screenshots)
                }
                if (details.categories.isNotEmpty()) {
                    CategoryChips(categories = details.categories)
                }
                if (details.isAggregated) {
                    AggregatedNotice()
                }
                state.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            val secondary: @Composable () -> Unit = {
                if (description != null) {
                    ExpandableTextCard(
                        title = stringResource(R.string.app_details_about),
                        text = description,
                    )
                }
                if (changelog.isNotEmpty()) {
                    ExpandableTextCard(
                        title = stringResource(R.string.app_details_changelog),
                        text = changelog,
                    )
                }
                details.website?.takeIf { it.isNotBlank() }?.let { website ->
                    WebsiteRow(url = website)
                }
                if (state.developerApps.isNotEmpty()) {
                    DeveloperAppsSection(
                        apps = state.developerApps,
                        publisher = details.publisher,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onAppClick = onAppClick,
                    )
                }
                Text(
                    text = stringResource(R.string.app_details_files_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (twoPane) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = spacing) { primary() }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = spacing) { secondary() }
                }
            } else {
                Column(verticalArrangement = spacing) { primary() }
                Column(verticalArrangement = spacing) { secondary() }
            }
        }
    }
}
/**
 * Screen header: large icon, name, publisher, and metric tags. One of the
 * screen's expressive accents — along with the primary action button.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun HeroHeader(
    details: AppDetails,
    state: AppDetailsUiState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val packageName = details.packageName
                RutheniaAppIcon(
                    iconUrl = details.iconUrl,
                    fallbackIcon = painterResource(R.drawable.ic_download),
                    // The destination of the shared element transition from
                    // lists and rails; deep links simply have no source and
                    // fade in as usual.
                    modifier = if (packageName != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(
                                    appIconTransitionKey(packageName),
                                ),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        }
                    } else {
                        Modifier
                    },
                    size = 88.dp,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = details.name ?: details.packageName.orEmpty(),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    details.publisher?.takeIf { it.isNotBlank() }?.let { publisher ->
                        Text(
                            text = publisher,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = details.packageName.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            MetaTags(details = details, state = state)
        }
    }
}

/** App metrics as tonal tags: rating, reviews, downloads, age. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaTags(details: AppDetails, state: AppDetailsUiState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.updateHidden) {
            RutheniaTag(text = stringResource(R.string.app_details_update_hidden))
        }
        state.rating?.let { rating ->
            RutheniaTag(
                text = formatRating(rating),
                icon = painterResource(R.drawable.ic_star),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        state.ratingsCount?.takeIf { it > 0 }?.let { count ->
            RutheniaTag(
                text = stringResource(R.string.app_details_ratings_count, count.toCompactCount()),
            )
        }
        details.downloads?.takeIf { it > 0 }?.let { downloads ->
            RutheniaTag(
                text = stringResource(R.string.app_details_downloads_count, downloads.toCompactCount()),
                icon = painterResource(R.drawable.ic_download),
            )
        }
        details.ageRestriction?.takeIf { it.isNotBlank() }?.let { age ->
            RutheniaTag(
                text = stringResource(R.string.app_details_age_limit, age),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** RuStore screenshot aspect ratio: 1080×1920. */
private const val ScreenshotAspectRatio = 9f / 16f

/** Screenshot carousel with full-screen viewing on tap. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScreenshotCarousel(urls: List<String>) {
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val haptics = rememberRutheniaHaptics()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            text = stringResource(R.string.app_details_screenshots),
            modifier = Modifier.fillMaxWidth(),
        )
        val carouselState = rememberCarouselState { urls.size }

        // A light tick when the centered screenshot changes.
        LaunchedEffect(carouselState) {
            var firstItem = true
            snapshotFlow { carouselState.currentItem }.collect {
                if (!firstItem) haptics.tick()
                firstItem = false
            }
        }

        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = 240.dp,
            minSmallItemWidth = 72.dp,
            maxSmallItemWidth = 104.dp,
            modifier = Modifier.fillMaxWidth(),
            itemSpacing = 12.dp,
        ) { index ->
            val url = urls.getOrNull(index) ?: return@HorizontalMultiBrowseCarousel
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.app_details_screenshot_n, index + 1),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ScreenshotAspectRatio)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .maskClip(MaterialTheme.shapes.large)
                    .clickable { viewerIndex = index },
            )
        }
    }

    viewerIndex?.let { initial ->
        ScreenshotViewer(
            urls = urls,
            initialIndex = initial,
            onDismiss = { viewerIndex = null },
        )
    }
}

/** Full-screen screenshot viewer with swiping between them. */
@Composable
private fun ScreenshotViewer(
    urls: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex) { urls.size }
        val haptics = rememberRutheniaHaptics()

        // A tick when moving to the next screenshot.
        LaunchedEffect(pagerState) {
            var firstPage = true
            snapshotFlow { pagerState.currentPage }.collect {
                if (!firstPage) haptics.tick()
                firstPage = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.96f)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val url = urls.getOrNull(page) ?: return@HorizontalPager
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.app_details_screenshot_n, page + 1),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 48.dp),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.app_details_screenshot_close),
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
            // Page counter: without it, in full-screen mode it's unclear how
            // many screenshots there are in total and where you are.
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                shape = RutheniaPillShape,
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Text(
                    text = stringResource(
                        R.string.app_details_screenshot_counter,
                        pagerState.currentPage + 1,
                        urls.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 2×2 fact grid: version, date, size, installed version. */
@Composable
private fun StatGrid(state: AppDetailsUiState, details: AppDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                label = stringResource(R.string.app_details_stat_version),
                value = details.versionName ?: "—",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.app_details_stat_updated),
                value = details.updatedAt?.substringBefore('T') ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                label = stringResource(R.string.app_details_stat_size),
                value = details.fileSize?.toReadableSize() ?: "—",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.app_details_stat_installed),
                value = state.installed?.installedVersionName ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    RutheniaStatCard(label = label, value = value, modifier = modifier)
}

/** App categories as non-interactive tags. */
@Composable
private fun CategoryChips(categories: List<String>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories) { category ->
            RutheniaTag(text = category)
        }
    }
}

@Composable
private fun AggregatedNotice() {
    NoticeSurface(
        text = stringResource(R.string.app_details_aggregated),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}

/** Offline-mode banner: the screen was assembled from saved data. */
@Composable
private fun OfflineNotice() {
    NoticeSurface(
        text = stringResource(R.string.app_details_offline),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

/**
 * Height of the bottom action button. The expressive XLarge token (136dp)
 * turned the persistent panel into a slab; Medium (56dp) matches the rest of
 * the app, while the split shape keeps the expressive character.
 */
private val DetailsActionButtonHeight = ButtonDefaults.MediumContainerHeight

/** Which button the action bar shows; drives the morph between states. */
private enum class DetailsActionState {
    DOWNLOADING,
    INSTALLING,
    READY,
    INSTALL,
    UPDATE,
    OPEN,
    REINSTALL,
}

/**
 * Action bar: while downloading/installing — a busy button with progress
 * (an active download can be cancelled right here), otherwise — an expressive
 * SplitButton. The primary action ("Install", "Update", "Open") is on the
 * left, and rare operations ("Save APK", "Reinstall", "Uninstall") are under
 * the arrow on the right.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailsActionBar(
    state: AppDetailsUiState,
    details: AppDetails,
    openAvailable: Boolean,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onCancelDownload: () -> Unit,
    onSaveApk: () -> Unit,
    onReinstall: () -> Unit,
    onUninstall: () -> Unit,
    onToggleUpdateVisibility: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val install = state.install
    val canAct = !details.isAggregated
    val installed = state.installed != null
    val downloadFraction = (install as? InstallUiState.Downloading)?.fraction

    val action = when {
        install is InstallUiState.Downloading -> DetailsActionState.DOWNLOADING
        install is InstallUiState.Installing -> DetailsActionState.INSTALLING
        install is InstallUiState.Ready -> DetailsActionState.READY
        state.installed == null -> DetailsActionState.INSTALL
        state.updateAvailable -> DetailsActionState.UPDATE
        openAvailable -> DetailsActionState.OPEN
        else -> DetailsActionState.REINSTALL
    }

    AnimatedContent(
        targetState = action,
        transitionSpec = {
            (
                fadeIn(animationSpec = motion.defaultEffectsSpec()) +
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec = motion.defaultSpatialSpec(),
                    )
                ) togetherWith fadeOut(animationSpec = motion.fastEffectsSpec())
        },
        contentAlignment = Alignment.BottomCenter,
        label = "detailsAction",
    ) { current ->
        when (current) {
            DetailsActionState.DOWNLOADING -> BusyButton(
                text = downloadFraction?.let {
                    stringResource(R.string.app_details_downloading_percent, (it * 100).toInt())
                } ?: stringResource(R.string.app_details_downloading),
                progress = downloadFraction,
                onCancel = onCancelDownload,
            )

            DetailsActionState.INSTALLING -> BusyButton(
                text = stringResource(R.string.app_details_installing),
                progress = null,
            )

            DetailsActionState.READY -> SplitActionButton(
                label = stringResource(R.string.app_details_install),
                iconRes = R.drawable.ic_download,
                enabled = true,
                canAct = canAct,
                installed = installed,
                onPrimary = onInstall,
                onSaveApk = onSaveApk,
                onReinstall = onReinstall,
                onUninstall = onUninstall,
                updateHidden = state.updateHidden,
                onToggleUpdateVisibility = onToggleUpdateVisibility,
            )

            DetailsActionState.INSTALL -> SplitActionButton(
                label = stringResource(R.string.app_details_install),
                iconRes = R.drawable.ic_download,
                enabled = state.canInstall,
                canAct = canAct,
                installed = installed,
                onPrimary = onInstall,
                onSaveApk = onSaveApk,
                onReinstall = onReinstall,
                onUninstall = onUninstall,
                updateHidden = state.updateHidden,
                onToggleUpdateVisibility = onToggleUpdateVisibility,
            )

            DetailsActionState.UPDATE -> SplitActionButton(
                label = stringResource(R.string.app_details_update),
                iconRes = R.drawable.ic_download,
                enabled = state.canInstall,
                canAct = canAct,
                installed = installed,
                onPrimary = onInstall,
                onSaveApk = onSaveApk,
                onReinstall = onReinstall,
                onUninstall = onUninstall,
                updateHidden = state.updateHidden,
                onToggleUpdateVisibility = onToggleUpdateVisibility,
            )

            DetailsActionState.OPEN -> SplitActionButton(
                label = stringResource(R.string.app_details_open),
                iconRes = R.drawable.ic_open_in_new,
                enabled = true,
                canAct = canAct,
                installed = installed,
                onPrimary = onOpen,
                onSaveApk = onSaveApk,
                onReinstall = onReinstall,
                onUninstall = onUninstall,
                updateHidden = state.updateHidden,
                onToggleUpdateVisibility = onToggleUpdateVisibility,
            )

            DetailsActionState.REINSTALL -> SplitActionButton(
                label = stringResource(R.string.app_details_reinstall),
                iconRes = R.drawable.ic_download,
                enabled = state.canInstall,
                canAct = canAct,
                installed = installed,
                onPrimary = onInstall,
                onSaveApk = onSaveApk,
                onReinstall = onReinstall,
                onUninstall = onUninstall,
                updateHidden = state.updateHidden,
                onToggleUpdateVisibility = onToggleUpdateVisibility,
            )
        }
    }

    (install as? InstallUiState.Failed)?.let { failure ->
        Text(
            text = failure.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SplitActionButton(
    label: String,
    iconRes: Int,
    enabled: Boolean,
    canAct: Boolean,
    installed: Boolean,
    onPrimary: () -> Unit,
    onSaveApk: () -> Unit,
    onReinstall: () -> Unit,
    onUninstall: () -> Unit,
    updateHidden: Boolean,
    onToggleUpdateVisibility: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptics = rememberRutheniaHaptics()
    val height = DetailsActionButtonHeight

    SplitButtonLayout(
        modifier = Modifier.fillMaxWidth(),
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = {
                    haptics.confirm()
                    onPrimary()
                },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
                shapes = SplitButtonDefaults.leadingButtonShapesFor(height),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(height),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(height)),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                checked = menuExpanded,
                onCheckedChange = {
                    haptics.tick()
                    menuExpanded = it
                },
                enabled = canAct,
                modifier = Modifier.height(height),
                shapes = SplitButtonDefaults.trailingButtonShapesFor(height),
                contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(height),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = stringResource(R.string.app_details_more),
                )
            }
        },
    )

    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.app_details_save_apk)) },
            enabled = canAct,
            onClick = {
                haptics.tick()
                menuExpanded = false
                onSaveApk()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.app_details_reinstall)) },
            enabled = canAct,
            onClick = {
                haptics.tick()
                menuExpanded = false
                onReinstall()
            },
        )
        if (installed) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (updateHidden) {
                                R.string.app_details_show_update
                            } else {
                                R.string.app_details_hide_update
                            },
                        ),
                    )
                },
                onClick = {
                    haptics.tick()
                    menuExpanded = false
                    onToggleUpdateVisibility()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_details_uninstall)) },
                onClick = {
                    haptics.tick()
                    menuExpanded = false
                    onUninstall()
                },
            )
        }
    }
}

@Composable
private fun BusyButton(
    text: String,
    progress: Float?,
    onCancel: (() -> Unit)? = null,
) {
    val height = DetailsActionButtonHeight
    val haptics = rememberRutheniaHaptics()
    val progressModifier = if (progress != null) Modifier.progressSemantics(progress) else Modifier

    if (onCancel != null) {
        // An active download can be cancelled right from the panel.
        FilledTonalButton(
            onClick = {
                haptics.tick()
                onCancel()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(progressModifier),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
        ) {
            BusyIndicator(
                progress = progress,
                size = ButtonDefaults.iconSizeFor(height),
            )
            Text(
                text = text,
                style = ButtonDefaults.textStyleFor(height),
                modifier = Modifier.padding(start = 12.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.app_details_cancel_download),
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
            )
        }
    } else {
        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(progressModifier),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
        ) {
            BusyIndicator(
                progress = progress,
                size = ButtonDefaults.iconSizeFor(height),
                color = LocalContentColor.current,
            )
            Text(
                text = text,
                style = ButtonDefaults.textStyleFor(height),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** Indeterminate while preparing, determinate when the download reports a fraction. */
@Composable
private fun BusyIndicator(
    progress: Float?,
    size: Dp,
    color: Color? = null,
) {
    if (progress != null) {
        RutheniaLoadingIndicator(
            progress = { progress },
            modifier = Modifier.size(size),
        )
    } else {
        RutheniaLoadingIndicator(
            modifier = Modifier.size(size),
            color = color,
        )
    }
}

/** Collapsible text block: description, "what's new". */
@Composable
private fun ExpandableTextCard(title: String, text: String) {
    val motion = MaterialTheme.motionScheme
    var expanded by rememberSaveable { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "textCardChevron",
    )

    ExpressiveCard(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            SectionHeader(
                text = title,
                modifier = Modifier.padding(16.dp),
                trailing = {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more),
                        contentDescription = stringResource(
                            if (expanded) R.string.app_details_collapse else R.string.app_details_expand,
                        ),
                        modifier = Modifier.rotate(chevronRotation),
                    )
                },
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = motion.defaultSpatialSpec()) +
                    fadeIn(animationSpec = motion.defaultEffectsSpec()),
                exit = shrinkVertically(animationSpec = motion.defaultSpatialSpec()) +
                    fadeOut(animationSpec = motion.fastEffectsSpec()),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }
}

/** Link to the app's website (opens in an external browser). */
@Composable
private fun WebsiteRow(url: String) {
    val context = LocalContext.current
    Surface(
        onClick = { context.openWebsite(url) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_open_in_new),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_details_website),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DeveloperAppsSection(
    apps: List<CatalogApp>,
    publisher: String?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onAppClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            text = stringResource(R.string.app_details_developer_apps),
            subtitle = publisher?.takeIf { it.isNotBlank() },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(apps, key = { it.packageName }) { app ->
                AppPosterCard(
                    title = app.name,
                    iconUrl = app.iconUrl,
                    rating = app.averageUserRating,
                    fallbackIcon = painterResource(R.drawable.ic_download),
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

/** Screen skeleton: mirrors the shape of the header, fact grid, and button. */
@Composable
private fun DetailsSkeleton() {
    val shimmer = rememberSkeletonShimmer()
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp),
            shimmer = shimmer,
            color = placeholder,
            shape = MaterialTheme.shapes.extraLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) {
                SkeletonBlock(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    shimmer = shimmer,
                    color = placeholder,
                    shape = MaterialTheme.shapes.large,
                )
            }
        }
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
            shimmer = shimmer,
            color = placeholder,
            shape = MaterialTheme.shapes.large,
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shimmer = shimmer,
            color = placeholder,
            shape = MaterialTheme.shapes.large,
        )
    }
}

/** Opens the installed app if the system provides a launch intent for it. */
private fun Context.launchApp(packageName: String) {
    val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** System dialog for uninstalling the app. */
private fun Context.uninstallApp(packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
    runCatching { startActivity(intent) }
}

/** Opens the app's website in an external browser. */
private fun Context.openWebsite(url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

/** Shares a link to the app in the RuStore catalog. */
private fun shareApp(context: Context, details: AppDetails?) {
    val packageName = details?.packageName ?: return
    val url = "https://www.rustore.ru/catalog/app/$packageName"
    val text = context.getString(
        R.string.app_details_share_text,
        details.name ?: packageName,
    ) + "\n" + url
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

/** Human-readable file size: "12.3 MB". */
@Composable
private fun Long.toReadableSize(): String {
    val kb = this / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> stringResource(R.string.app_details_size_mb, mb)
        kb >= 1 -> stringResource(R.string.app_details_size_kb, kb)
        else -> stringResource(R.string.app_details_size_b, this)
    }
}

/** Compact notation for large numbers: "90M", "200K", "512". */
@Composable
private fun Long.toCompactCount(): String = when {
    this >= 1_000_000 -> stringResource(R.string.app_details_count_millions, this / 1_000_000.0)
    this >= 1_000 -> stringResource(R.string.app_details_count_thousands, this / 1_000.0)
    else -> toString()
}
