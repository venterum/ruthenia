/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing of the catalog feed: jank while scrolling rails and sticky
 * headers after a warm start.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class FeedScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollFeed() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            // Complete onboarding once (fresh install): the measured start must
            // land on the feed.
            pressHome()
            startActivityAndWait()
            skipOnboardingIfVisible()
        },
    ) {
        startActivityAndWait()

        // The feed needs the network; wait for it instead of failing on an
        // empty state.
        val feedReady = device.wait(Until.hasObject(By.res(UiTags.APPS_FEED)), 10_000)
        check(feedReady) { "Apps feed did not appear: no network or an empty catalog?" }

        val feed = device.findObject(By.res(UiTags.APPS_FEED))
        feed.setGestureMargin(device.displayWidth / 5)
        repeat(3) { feed.fling(Direction.DOWN) }
    }
}
