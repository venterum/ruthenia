/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.vntrum.ruthenia.core.network.RuStoreApi
import com.vntrum.ruthenia.core.network.RuStoreSelection

/**
 * RuStore editorial selections.
 *
 * The source is the web catalog, so the selections are not personal. We keep
 * the result in memory for [CACHE_TTL_MS]: the data is small, and if the web
 * payload format changes the list simply comes back empty and the UI does not
 * show the section.
 */
@Singleton
class SelectionRepository @Inject constructor(
    private val api: RuStoreApi,
) {

    private val mutex = Mutex()
    private var cachedSelections: List<RuStoreSelection> = emptyList()
    private var cachedAt: Long = 0L

    /** The list of selections: fresh from cache or rebuilt from the network. */
    suspend fun selections(forceRefresh: Boolean = false): List<RuStoreSelection> = mutex.withLock {
        val fresh = System.currentTimeMillis() - cachedAt < CACHE_TTL_MS
        if (!forceRefresh && fresh && cachedSelections.isNotEmpty()) {
            return cachedSelections
        }
        val loaded = runCatching { api.selections() }.getOrDefault(emptyList())
        if (loaded.isNotEmpty()) {
            cachedSelections = loaded
            cachedAt = System.currentTimeMillis()
        }
        cachedSelections
    }

    /**
     * A selection as a home feed section — for reusing the collection screen.
     * `null` if the selection was not found or the apps did not arrive.
     */
    suspend fun section(selectionId: String): HomeSection? {
        val apps = runCatching { api.selectionApps(selectionId) }.getOrDefault(emptyList())
        if (apps.isEmpty()) return null
        val title = selections().firstOrNull { it.id == selectionId }?.title
            ?: DEFAULT_TITLE
        return HomeSection(
            id = selectionId,
            title = title,
            apps = apps,
        )
    }

    private companion object {
        const val CACHE_TTL_MS = 3 * 60 * 60 * 1000L
        const val DEFAULT_TITLE = "Подборка"
    }
}
