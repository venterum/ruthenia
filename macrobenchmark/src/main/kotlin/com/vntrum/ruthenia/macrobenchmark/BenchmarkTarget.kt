/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.macrobenchmark

/** The application under test. */
const val TARGET_PACKAGE = "com.vntrum.ruthenia"

/**
 * Compose test tags exposed to UiAutomator as resource IDs.
 *
 * They are mirrored from the app sources and must be kept in sync manually:
 * the benchmark module cannot depend on the app module. See
 * `Modifier.semantics { testTagsAsResourceId = true }` at the root of
 * `RutheniaApp`.
 */
object UiTags {
    /** "Skip" on the first-launch onboarding. */
    const val ONBOARDING_SKIP = "onboarding_skip"

    /** The scrollable catalog feed on the Apps tab. */
    const val APPS_FEED = "apps_feed"

    /** The expanded card of the "Recommended" carousel. */
    const val FEATURED_CARD = "featured_card"

    /** Root of the app details screen. */
    const val APP_DETAILS = "app_details"
}
