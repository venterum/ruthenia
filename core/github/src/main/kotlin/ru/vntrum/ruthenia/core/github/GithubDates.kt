/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.github

import java.time.Instant

internal object GithubDates {

    /** GitHub timestamps are ISO-8601 UTC (`2026-10-20T21:17:09Z`). */
    fun parseMillis(value: String): Long? = runCatching { Instant.parse(value).toEpochMilli() }
        .getOrNull()
}
