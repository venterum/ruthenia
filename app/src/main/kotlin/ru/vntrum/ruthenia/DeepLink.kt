/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia

import java.net.URI

/** Hosts whose links open the app details page. */
private val RUSTORE_LINK_HOSTS = setOf("www.rustore.ru", "rustore.ru")

/** Path of the public app page in the catalog. */
private const val CATALOG_PATH = "catalog"
private const val APP_PATH = "app"

/**
 * Extracts the package name from a public RuStore link
 * `https://www.rustore.ru/catalog/app/<packageName>`.
 *
 * Returns `null` for all other links: the deep link must not open arbitrary
 * addresses, only catalog app pages.
 */
fun rustorePackageNameFromLink(url: String?): String? {
    if (url.isNullOrBlank()) return null

    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (uri.scheme != "https") return null
    if (uri.host?.lowercase() !in RUSTORE_LINK_HOSTS) return null

    val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
    if (segments.size < 3) return null
    if (segments[0] != CATALOG_PATH || segments[1] != APP_PATH) return null

    return segments[2].takeIf { it.isNotBlank() }
}