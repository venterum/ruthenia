/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `prefers numeric version code`() {
        assertEquals(
            UpdateStatus.UPDATE_AVAILABLE,
            VersionComparator.status(
                installedVersionCode = 100,
                installedVersionName = "1.0.0",
                latestVersionCode = 101,
                latestVersionName = "1.0.0",
            ),
        )
        assertEquals(
            UpdateStatus.UP_TO_DATE,
            VersionComparator.status(
                installedVersionCode = 2026062216,
                installedVersionName = "17.12.0",
                latestVersionCode = 2026062216,
                latestVersionName = "17.13.0",
            ),
        )
    }

    @Test
    fun `falls back to version name when codes are missing`() {
        assertEquals(
            UpdateStatus.UPDATE_AVAILABLE,
            VersionComparator.status(
                installedVersionCode = null,
                installedVersionName = "17.9.0",
                latestVersionCode = null,
                latestVersionName = "17.12.0",
            ),
        )
        assertEquals(
            UpdateStatus.UP_TO_DATE,
            VersionComparator.status(
                installedVersionCode = null,
                installedVersionName = "17.12.0",
                latestVersionCode = null,
                latestVersionName = "17.12.0",
            ),
        )
    }

    @Test
    fun `reports unknown when one side has no data`() {
        assertEquals(
            UpdateStatus.UNKNOWN,
            VersionComparator.status(null, null, 101, "1.0.1"),
        )
        assertEquals(
            UpdateStatus.UNKNOWN,
            VersionComparator.status(100, null, null, null),
        )
    }

    @Test
    fun `compares name segments numerically not lexicographically`() {
        // Lexicographically "9" > "12", numerically the opposite.
        assertTrue(VersionComparator.compareNames("1.12.0", "1.9.0") > 0)
        assertTrue(VersionComparator.compareNames("2.0", "10.0") < 0)
        assertEquals(0, VersionComparator.compareNames("1.2.3", "1.2.3"))
    }

    @Test
    fun `treats a longer version with equal prefix as newer`() {
        assertTrue(VersionComparator.compareNames("1.2.3.1", "1.2.3") > 0)
        assertTrue(VersionComparator.compareNames("1.2", "1.2.1") < 0)
    }

    @Test
    fun `handles hyphenated and underscored build suffixes`() {
        assertTrue(VersionComparator.compareNames("1.0.0-2", "1.0.0-1") > 0)
        assertTrue(VersionComparator.compareNames("1.0.0_2", "1.0.0-1") > 0)
    }
}
