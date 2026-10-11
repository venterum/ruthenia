/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/**
 * The app shows onboarding on the first launch after a fresh install, while the
 * profiles and benchmarks are about the catalog. Skip it when it is visible;
 * the flag is persisted, so later runs are a no-op.
 *
 * The journey ends when the onboarding screen leaves the composition, not when
 * the feed appears: the feed also needs the network and can take longer.
 */
fun MacrobenchmarkScope.skipOnboardingIfVisible() {
    val skip = device.findObject(By.res(UiTags.ONBOARDING_SKIP)) ?: return
    skip.click()
    device.wait(Until.gone(By.res(UiTags.ONBOARDING_SKIP)), 5_000)
    device.waitForIdle()
}
