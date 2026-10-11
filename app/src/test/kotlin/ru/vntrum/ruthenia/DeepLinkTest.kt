/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {

    @Test
    fun `www link yields package name`() {
        assertEquals(
            "ru.sberbankmobile",
            rustorePackageNameFromLink("https://www.rustore.ru/catalog/app/ru.sberbankmobile"),
        )
    }

    @Test
    fun `bare domain is accepted`() {
        assertEquals(
            "ru.example.app",
            rustorePackageNameFromLink("https://rustore.ru/catalog/app/ru.example.app"),
        )
    }

    @Test
    fun `query and trailing slash are ignored`() {
        assertEquals(
            "ru.example.app",
            rustorePackageNameFromLink(
                "https://www.rustore.ru/catalog/app/ru.example.app/?utm_source=share",
            ),
        )
    }

    @Test
    fun `other hosts are rejected`() {
        assertNull(rustorePackageNameFromLink("https://example.com/catalog/app/ru.example.app"))
        assertNull(rustorePackageNameFromLink("https://evil.rustore.ru/catalog/app/ru.example.app"))
    }

    @Test
    fun `other paths are rejected`() {
        assertNull(rustorePackageNameFromLink("https://www.rustore.ru/catalog/"))
        assertNull(rustorePackageNameFromLink("https://www.rustore.ru/catalog/app"))
        assertNull(rustorePackageNameFromLink("https://www.rustore.ru/other/app/ru.example.app"))
    }

    @Test
    fun `non https schemes are rejected`() {
        assertNull(rustorePackageNameFromLink("http://www.rustore.ru/catalog/app/ru.example.app"))
        assertNull(rustorePackageNameFromLink("rustore://catalog/app/ru.example.app"))
        assertNull(rustorePackageNameFromLink(null))
        assertNull(rustorePackageNameFromLink(""))
    }
}