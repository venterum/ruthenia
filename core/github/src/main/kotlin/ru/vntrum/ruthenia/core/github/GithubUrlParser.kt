/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.github

import java.net.URI

/** `owner/repo` coordinates parsed from a URL or a short form. */
data class GithubRepoCoordinates(val owner: String, val repo: String) {
    val fullName: String get() = "$owner/$repo"
}

/**
 * Accepts `owner/repo`, `https://github.com/owner/repo` and GitHub URLs with
 * trailing paths. Rejects other hosts and credentials.
 */
object GithubUrlParser {

    private val OWNER = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})")
    private val REPO = Regex("[A-Za-z0-9._-]{1,100}")

    fun parse(input: String): GithubRepoCoordinates? {
        val value = input.trim()
        if (value.isEmpty()) return null

        val short = parseShort(value)
        if (short != null) return short

        val normalized = if (value.startsWith("github.com/")) "https://$value" else value
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "github.com" && host != "www.github.com") return null

        val segments = uri.path.orEmpty()
            .split('/')
            .filter { it.isNotBlank() }
        if (segments.size < 2) return null
        return validate(segments[0], segments[1])
    }

    private fun parseShort(value: String): GithubRepoCoordinates? {
        if (value.contains('/').not()) return null
        if (value.contains(':')) return null
        val parts = value.split('/').filter { it.isNotBlank() }
        if (parts.size != 2) return null
        return validate(parts[0], parts[1])
    }

    private fun validate(owner: String, repo: String): GithubRepoCoordinates? {
        val cleanRepo = repo.removeSuffix(".git")
        if (!OWNER.matches(owner) || !REPO.matches(cleanRepo)) return null
        // `.` and `..` are valid by the regex but never valid repo names.
        if (cleanRepo == "." || cleanRepo == "..") return null
        return GithubRepoCoordinates(owner, cleanRepo)
    }
}
