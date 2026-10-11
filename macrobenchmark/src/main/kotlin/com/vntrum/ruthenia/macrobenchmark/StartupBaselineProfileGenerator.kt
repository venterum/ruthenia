/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup journey of the baseline profile: from the launcher icon to the first
 * frame of the Apps feed.
 *
 * Rules collected here are also used for the startup profile (DEX layout
 * optimizations), so they must stay limited to the cold start path.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        startActivityAndWait()
        // On a fresh install the first iteration covers onboarding; the rest
        // capture the cold start of the feed.
        skipOnboardingIfVisible()
        device.waitForIdle()
    }
}
