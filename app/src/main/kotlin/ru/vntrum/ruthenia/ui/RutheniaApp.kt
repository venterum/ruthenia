// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vntrum.ruthenia.MainViewModel
import com.vntrum.ruthenia.R
import com.vntrum.ruthenia.core.data.settings.AppSettings
import com.vntrum.ruthenia.core.data.settings.NavBarStyle
import com.vntrum.ruthenia.core.designsystem.component.FloatingToolbarBottomPadding
import com.vntrum.ruthenia.core.designsystem.component.LocalRutheniaBottomContentPadding
import com.vntrum.ruthenia.core.designsystem.component.NavigationBarBottomPadding
import com.vntrum.ruthenia.core.designsystem.component.RutheniaBottomNavigationBar
import com.vntrum.ruthenia.core.designsystem.component.RutheniaFloatingTabBar
import com.vntrum.ruthenia.core.designsystem.component.RutheniaNavItem
import com.vntrum.ruthenia.core.designsystem.component.RutheniaNavigationRail
import com.vntrum.ruthenia.core.designsystem.component.RutheniaWidthClass
import com.vntrum.ruthenia.core.designsystem.component.rememberRutheniaWidthClass
import com.vntrum.ruthenia.core.designsystem.component.screenCorners
import com.vntrum.ruthenia.core.designsystem.haptics.rememberRutheniaHaptics
import com.vntrum.ruthenia.core.designsystem.motion.RutheniaMotion
import com.vntrum.ruthenia.core.designsystem.motion.navPopEnter
import com.vntrum.ruthenia.core.designsystem.motion.navPopExit
import com.vntrum.ruthenia.core.designsystem.motion.navPushEnter
import com.vntrum.ruthenia.core.designsystem.motion.navPushExit
import com.vntrum.ruthenia.feature.appdetails.AppDetailsScreen
import com.vntrum.ruthenia.feature.catalog.AppsScreen
import com.vntrum.ruthenia.feature.catalog.CollectionScreen
import com.vntrum.ruthenia.feature.catalog.CollectionViewModel
import com.vntrum.ruthenia.feature.catalog.GamesScreen
import com.vntrum.ruthenia.feature.catalog.SearchScreen
import com.vntrum.ruthenia.feature.library.LibraryScreen
import com.vntrum.ruthenia.feature.library.MyAppsScreen
import com.vntrum.ruthenia.feature.library.UpdatesScreen
import com.vntrum.ruthenia.feature.settings.AboutScreen
import com.vntrum.ruthenia.feature.settings.PrivacyScreen
import com.vntrum.ruthenia.feature.settings.SettingsScreen
import com.vntrum.ruthenia.ui.profile.ProfileSheet
import kotlin.math.abs
import kotlinx.coroutines.launch

object RutheniaRoutes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"
    const val COLLECTION = "collection/{sectionId}"
    const val LIBRARY_APPS = "library/apps"
    const val LIBRARY_UPDATES = "library/updates"
    const val ARG_PACKAGE_NAME = "packageName"
    const val APP_DETAILS = "app/{$ARG_PACKAGE_NAME}"

    fun appDetails(packageName: String): String = "app/$packageName"

    fun collection(sectionId: String): String = "collection/$sectionId"
}

/**
 * Main navigation tabs. Settings is not a tab: it opens from the header of the
 * Apps page.
 */
internal enum class MainTab(val labelRes: Int, val iconRes: Int) {
    APPS(R.string.nav_apps, R.drawable.ic_home),
    SEARCH(R.string.nav_search, R.drawable.ic_search),
    GAMES(R.string.nav_games, R.drawable.ic_games),
    LIBRARY(R.string.nav_library, R.drawable.ic_library),
}

/** Tabs for the current settings; the Games tab hides with the settings switch. */
internal fun visibleTabs(gamesEnabled: Boolean): List<MainTab> = buildList {
    add(MainTab.APPS)
    add(MainTab.SEARCH)
    if (gamesEnabled) add(MainTab.GAMES)
    add(MainTab.LIBRARY)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RutheniaApp(viewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    // Badge on the "Library" tab: how many apps are waiting for an update.
    val updateCount by viewModel.updateCount.collectAsStateWithLifecycle()
    val deepLink by viewModel.deepLink.collectAsStateWithLifecycle()
    // null means settings are not read yet; wait so we don't flash the onboarding.
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val motionScheme = MaterialTheme.motionScheme

    // A link to an app page in RuStore opens the details page inside the
    // app (see rustorePackageNameFromLink). Until onboarding is completed,
    // links are ignored: onboarding comes first.
    LaunchedEffect(deepLink, onboardingCompleted) {
        if (onboardingCompleted != true) return@LaunchedEffect
        deepLink?.let { packageName ->
            navController.navigate(RutheniaRoutes.appDetails(packageName)) {
                launchSingleTop = true
            }
            viewModel.consumeDeepLink()
        }
    }

    if (onboardingCompleted == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        )
        return
    }

    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = if (onboardingCompleted == true) {
                RutheniaRoutes.MAIN
            } else {
                RutheniaRoutes.ONBOARDING
            },
            // Forward and back transitions use the theme's motionScheme specs:
            // forward, the screen softly fades in with scale; back repeats the system
            // scale + fade-through with rounded corners (the same transition drives
            // the predictive gesture).
            enterTransition = { navPushEnter(motionScheme) },
            exitTransition = { navPushExit(motionScheme) },
            popEnterTransition = { navPopEnter(motionScheme) },
            popExitTransition = { navPopExit(motionScheme) },
            predictivePopEnterTransition = { navPopEnter(motionScheme) },
            predictivePopExitTransition = { navPopExit(motionScheme) },
            // Exposes Compose test tags as resource IDs: macrobenchmarks and the
            // baseline profile generator select UI nodes with By.res(...).
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true },
        ) {
            composable(RutheniaRoutes.ONBOARDING) {
                NavScreen {
                    OnboardingScreen(
                        onFinish = {
                            viewModel.completeOnboarding()
                            navController.navigate(RutheniaRoutes.MAIN) {
                                popUpTo(RutheniaRoutes.ONBOARDING) { inclusive = true }
                            }
                        },
                    )
                }
            }
    
            composable(RutheniaRoutes.MAIN) {
                NavScreen {
                    MainTabs(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        settings = settings,
                        updateCount = updateCount,
                        onAppClick = { navController.navigate(RutheniaRoutes.appDetails(it)) },
                        onCollectionClick = { sectionId ->
                            navController.navigate(RutheniaRoutes.collection(sectionId))
                        },
                        onPrivacyClick = { navController.navigate(RutheniaRoutes.PRIVACY) },
                        onSettingsClick = { navController.navigate(RutheniaRoutes.SETTINGS) },
                        onAboutClick = { navController.navigate(RutheniaRoutes.ABOUT) },
                        onOpenLibraryApps = { navController.navigate(RutheniaRoutes.LIBRARY_APPS) },
                        onOpenLibraryUpdates = { navController.navigate(RutheniaRoutes.LIBRARY_UPDATES) },
                    )
                }
            }
    
            composable(
                route = RutheniaRoutes.APP_DETAILS,
                arguments = listOf(
                    navArgument(RutheniaRoutes.ARG_PACKAGE_NAME) { type = NavType.StringType },
                ),
            ) {
                NavScreen {
                    AppDetailsScreen(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        onBack = { navController.popBackStack() },
                        onAppClick = { navController.navigate(RutheniaRoutes.appDetails(it)) },
                    )
                }
            }
    
            composable(RutheniaRoutes.SETTINGS) {
                NavScreen {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onPrivacyClick = { navController.navigate(RutheniaRoutes.PRIVACY) },
                        onAboutClick = { navController.navigate(RutheniaRoutes.ABOUT) },
                        appIconPreviews = appIconPreviews,
                    )
                }
            }
    
            composable(RutheniaRoutes.ABOUT) {
                NavScreen {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onPrivacyClick = { navController.navigate(RutheniaRoutes.PRIVACY) },
                    )
                }
            }
    
            composable(RutheniaRoutes.PRIVACY) {
                NavScreen {
                    PrivacyScreen(onBack = { navController.popBackStack() })
                }
            }
    
            composable(
                route = RutheniaRoutes.COLLECTION,
                arguments = listOf(
                    navArgument(CollectionViewModel.ARG_SECTION_ID) { type = NavType.StringType },
                ),
            ) {
                NavScreen {
                    CollectionScreen(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        onAppClick = { navController.navigate(RutheniaRoutes.appDetails(it)) },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
    
            composable(RutheniaRoutes.LIBRARY_APPS) {
                NavScreen {
                    MyAppsScreen(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        onBack = { navController.popBackStack() },
                        onAppClick = { navController.navigate(RutheniaRoutes.appDetails(it)) },
                    )
                }
            }
    
            composable(RutheniaRoutes.LIBRARY_UPDATES) {
                NavScreen {
                    UpdatesScreen(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        onBack = { navController.popBackStack() },
                        onAppClick = { navController.navigate(RutheniaRoutes.appDetails(it)) },
                    )
                }
            }
        }
    }
}

/** Root of a navigation destination: rounds the corners so going back looks like a card. */
@Composable
private fun NavScreen(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().screenCorners()) {
        content()
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainTabs(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    settings: AppSettings,
    updateCount: Int,
    onAppClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
    onPrivacyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onOpenLibraryApps: () -> Unit,
    onOpenLibraryUpdates: () -> Unit,
) {
    val haptics = rememberRutheniaHaptics()
    val motion = MaterialTheme.motionScheme
    val tabs = visibleTabs(settings.gamesEnabled)
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { tabs.size },
    )
    val scope = rememberCoroutineScope()
    val goToTab: (Int) -> Unit = { index ->
        scope.launch {
            pagerState.animateScrollToPage(
                page = index,
                animationSpec = motion.defaultSpatialSpec(),
            )
        }
    }
    var selectedTab by remember { mutableIntStateOf(pagerState.currentPage) }
    // The local device-profile sheet, opened from the Apps header avatar.
    var showProfileSheet by rememberSaveable { mutableStateOf(false) }

    // A tab disappears when games are disabled in settings; keep the pager on
    // an existing page.
    LaunchedEffect(tabs.size) {
        if (pagerState.currentPage > tabs.lastIndex) {
            pagerState.scrollToPage(tabs.lastIndex)
        }
    }

    // Haptic tick on tab change — for both tap and swipe.
    LaunchedEffect(pagerState) {
        var firstPage = true
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (!firstPage) haptics.tick()
            firstPage = false
            selectedTab = page
        }
    }

    val navItems = tabs.map { tab ->
        RutheniaNavItem(
            label = stringResource(tab.labelRes),
            icon = painterResource(tab.iconRes),
            // Show the badge only on the library tab.
            badgeCount = if (tab == MainTab.LIBRARY) updateCount else 0,
        )
    }

    // On a wide screen the bottom bar gives way to a vertical rail —
    // the same tab selection, a different layout. The threshold comes from the
    // platform window width class, not the local content width.
    val wideLayout = rememberRutheniaWidthClass() != RutheniaWidthClass.Compact
    // The docked bar draws above the system navigation bar, so the content
    // padding must cover both the bar itself and the inset.
    val navigationBarInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val bottomContentPadding = when {
        wideLayout -> 16.dp
        settings.navBarStyle == NavBarStyle.NORMAL -> NavigationBarBottomPadding + navigationBarInset
        else -> FloatingToolbarBottomPadding
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (wideLayout) {
                RutheniaNavigationRail(
                    items = navItems,
                    selectedIndex = selectedTab,
                    onSelect = goToTab,
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pageDistance = abs(
                                (pagerState.currentPage - page) +
                                    pagerState.currentPageOffsetFraction,
                            ).coerceAtMost(1f)
                            alpha = 1f - pageDistance * (1f - RutheniaMotion.PAGE_MIN_ALPHA)
                            val pageScale = 1f - pageDistance * (1f - RutheniaMotion.PAGE_MIN_SCALE)
                            scaleX = pageScale
                            scaleY = pageScale
                        },
                ) {
                    CompositionLocalProvider(
                        LocalRutheniaBottomContentPadding provides bottomContentPadding,
                    ) {
                        when (tabs.getOrNull(page)) {
                            MainTab.APPS -> AppsScreen(
                                onAppClick = onAppClick,
                                onCollectionClick = onCollectionClick,
                                onOpenUpdates = onOpenLibraryUpdates,
                                onPrivacyClick = onPrivacyClick,
                                onProfileClick = { showProfileSheet = true },
                                onAboutClick = onAboutClick,
                            )
                            MainTab.SEARCH -> SearchScreen(
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onAppClick = onAppClick,
                                onCollectionClick = onCollectionClick,
                            )
                            MainTab.GAMES -> GamesScreen(
                                onAppClick = onAppClick,
                                onCollectionClick = onCollectionClick,
                            )
                            MainTab.LIBRARY -> LibraryScreen(
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onOpenApps = onOpenLibraryApps,
                                onOpenUpdates = onOpenLibraryUpdates,
                                onAppClick = onAppClick,
                            )
                            // The page can be composed for a frame longer than
                            // the tab exists while the list shrinks.
                            null -> Unit
                        }
                    }
                }
            }
        }

        if (!wideLayout) {
            when (settings.navBarStyle) {
                NavBarStyle.FLOATING -> RutheniaFloatingTabBar(
                    items = navItems,
                    selectedIndex = selectedTab,
                    onSelect = goToTab,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp),
                )
                NavBarStyle.NORMAL -> RutheniaBottomNavigationBar(
                    items = navItems,
                    selectedIndex = selectedTab,
                    onSelect = goToTab,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }
        }
    }

    if (showProfileSheet) {
        // Navigate first, then hide the sheet, so it never lingers over the
        // destination it opens.
        val openAndClose: (() -> Unit) -> Unit = { action ->
            showProfileSheet = false
            action()
        }
        ProfileSheet(
            onDismiss = { showProfileSheet = false },
            onOpenSettings = { openAndClose(onSettingsClick) },
            onOpenUpdates = { openAndClose(onOpenLibraryUpdates) },
            onOpenAbout = { openAndClose(onAboutClick) },
        )
    }
}
