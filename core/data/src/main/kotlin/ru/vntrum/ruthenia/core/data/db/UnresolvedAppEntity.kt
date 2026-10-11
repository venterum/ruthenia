/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Negative cache of the app feed: a curated package the catalog could not
 * resolve, with the time of the last attempt.
 *
 * Without it every feed access retried the missing packages over the network,
 * so opening a collection that was already on screen still waited seconds.
 * Entries are retried on an explicit reload or once [attemptedAt] ages out.
 */
@Entity(tableName = "unresolved_apps")
data class UnresolvedAppEntity(
    @PrimaryKey val packageName: String,
    val attemptedAt: Long,
)
