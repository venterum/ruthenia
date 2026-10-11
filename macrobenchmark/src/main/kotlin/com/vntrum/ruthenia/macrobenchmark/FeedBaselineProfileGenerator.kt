/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Feed journey of the baseline profile: opening an app page from the
 * "Recommended" carousel and scrolling the catalog feed with its rails and
 * sticky headers.
 *
 * The feed loads from the network: if it is unavailable, the block degrades to
 * a no-op instead of failing the generation.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class FeedBaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun feedAndDetails() = rule.collect(packageName = TARGET_PACKAGE) {
        startActivityAndWait()
        skipOnboardingIfVisible()

        // Open an app page from the featured card: it is expanded without any
        // scrolling, so the selector is deterministic.
        device.findObject(By.res(UiTags.FEATURED_CARD))?.let { card ->
            card.click()
            if (device.wait(Until.hasObject(By.res(UiTags.APP_DETAILS)), 10_000)) {
                device.waitForIdle()
                device.pressBack()
                device.waitForIdle()
            }
        }

        // Scroll the feed: rails, sticky headers, and pull-to-refresh container.
        val feedReady = device.wait(Until.hasObject(By.res(UiTags.APPS_FEED)), 10_000)
        if (feedReady) {
            device.findObject(By.res(UiTags.APPS_FEED))?.let { feed ->
                feed.setGestureMargin(device.displayWidth / 5)
                repeat(3) { feed.fling(Direction.DOWN) }
                device.waitForIdle()
            }
        }
    }
}
