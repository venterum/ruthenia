/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.catalog

import org.junit.Assert.assertEquals
import org.junit.Test
import com.vntrum.ruthenia.core.network.CatalogApp

class SearchPaginationTest {

    @Test
    fun `next page is appended after existing results`() {
        val existing = listOf(app("com.a"), app("com.b"))
        val next = listOf(app("com.c"))

        val merged = appendDistinctApps(existing, next)

        assertEquals(listOf("com.a", "com.b", "com.c"), merged.map { it.packageName })
    }

    @Test
    fun `duplicates between pages are dropped`() {
        val existing = listOf(app("com.a"), app("com.b"))
        val next = listOf(app("com.b"), app("com.c"))

        val merged = appendDistinctApps(existing, next)

        assertEquals(listOf("com.a", "com.b", "com.c"), merged.map { it.packageName })
    }

    @Test
    fun `empty page keeps existing results`() {
        val existing = listOf(app("com.a"))

        assertEquals(existing, appendDistinctApps(existing, emptyList()))
    }

    private fun app(packageName: String): CatalogApp = CatalogApp(
        appId = packageName.hashCode().toLong(),
        packageName = packageName,
        name = packageName,
        shortDescription = null,
        iconUrl = null,
        versionCode = 1L,
        minSdkVersion = 26,
    )
}