// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.R
import com.vntrum.ruthenia.core.designsystem.haptics.rememberRutheniaHaptics
import com.vntrum.ruthenia.core.designsystem.motion.RutheniaMotion
import kotlin.math.abs
import kotlinx.coroutines.launch

/** A single onboarding page: icon, title and body text. */
private data class OnboardingPage(
    val iconRes: Int,
    val titleRes: Int,
    val bodyRes: Int,
)

private val PAGES = listOf(
    OnboardingPage(
        iconRes = R.drawable.ic_download,
        titleRes = R.string.onboarding_welcome_title,
        bodyRes = R.string.onboarding_welcome_body,
    ),
    OnboardingPage(
        iconRes = R.drawable.ic_check,
        titleRes = R.string.onboarding_privacy_title,
        bodyRes = R.string.onboarding_privacy_body,
    ),
    OnboardingPage(
        iconRes = R.drawable.ic_library,
        titleRes = R.string.onboarding_install_title,
        bodyRes = R.string.onboarding_install_body,
    ),
    OnboardingPage(
        iconRes = R.drawable.ic_settings,
        titleRes = R.string.onboarding_disclaimer_title,
        bodyRes = R.string.onboarding_disclaimer_body,
    ),
)

/**
 * First launch: four pages about what this app is, how it cares about
 * privacy and how it installs updates. The screen is shown once;
 * "Skip" and "Start" do the same thing — mark onboarding as done.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    val haptics = rememberRutheniaHaptics()
    val isLastPage = pagerState.currentPage == PAGES.lastIndex

    // A tick on every page change — swipe or button.
    LaunchedEffect(pagerState) {
        var firstPage = true
        snapshotFlow { pagerState.currentPage }.collect {
            if (!firstPage) haptics.tick()
            firstPage = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onFinish,
                // Stable handle for macrobenchmarks (see :macrobenchmark).
                modifier = Modifier.testTag("onboarding_skip"),
            ) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val item = PAGES[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Same depth language as the main pager: the outgoing
                        // page dims and shrinks while the next one arrives.
                        val pageDistance = abs(
                            (pagerState.currentPage - page) +
                                pagerState.currentPageOffsetFraction,
                        ).coerceAtMost(1f)
                        alpha = 1f - pageDistance * (1f - RutheniaMotion.PAGE_MIN_ALPHA)
                        val pageScale = 1f - pageDistance * (1f - RutheniaMotion.PAGE_MIN_SCALE)
                        scaleX = pageScale
                        scaleY = pageScale
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialShapes.Cookie9Sided.toShape(),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(item.bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PAGES.indices.forEach { index ->
                val active = index == pagerState.currentPage
                val width by animateDpAsState(
                    targetValue = if (active) 24.dp else 8.dp,
                    animationSpec = motion.defaultSpatialSpec(),
                    label = "onboardingDot",
                )
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isLastPage) {
                    haptics.confirm()
                    onFinish()
                } else {
                    haptics.tick()
                    scope.launch {
                        pagerState.animateScrollToPage(
                            page = pagerState.currentPage + 1,
                            animationSpec = motion.defaultSpatialSpec(),
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                text = stringResource(
                    if (isLastPage) R.string.onboarding_start else R.string.onboarding_next,
                ),
            )
        }
    }
}
