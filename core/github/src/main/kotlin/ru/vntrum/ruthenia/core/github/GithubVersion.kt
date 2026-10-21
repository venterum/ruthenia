/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.github

/**
 * Normalizes release tags for display and comparison.
 *
 * Common forms are `v1.2.3`, `1.2.3`, `release-1.2.3` and `app/v1.2.3`.
 * The first version-like token is extracted; when none is found the tag is
 * returned unchanged so the caller can still show it and fall back to the
 * release id for ordering.
 */
object GithubVersion {

    private val VERSION_TOKEN = Regex("""\d+(?:\.\d+){0,3}(?:[-+][0-9A-Za-z.-]+)?""")

    fun normalize(tag: String): String {
        val trimmed = tag.trim().removePrefix("v").removePrefix("V")
        if (trimmed.isEmpty()) return tag.trim()
        return VERSION_TOKEN.find(trimmed)?.value ?: trimmed
    }

    /** Whether two normalized tags can be compared by [GithubVersion.normalize]. */
    fun looksComparable(tag: String): Boolean = VERSION_TOKEN.containsMatchIn(tag)
}
