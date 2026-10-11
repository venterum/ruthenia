// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.data.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Search query history: the most recent successful queries, without duplicates,
 * in reverse chronological order. Stored locally in the same DataStore as the
 * settings, and never leaves the device.
 */
@Singleton
class SearchHistoryRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {

    val history: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[HISTORY].decodeHistory()
    }

    /** Adds a query to the top of the list; a repeated query moves back to the top. */
    suspend fun add(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        dataStore.edit { preferences ->
            val updated = (listOf(normalized) +
                preferences[HISTORY].decodeHistory()
                    .filterNot { it.equals(normalized, ignoreCase = true) })
                .take(MAX_ENTRIES)
            preferences[HISTORY] = json.encodeToString(updated)
        }
    }

    /** Clears the history completely. */
    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(HISTORY) }
    }

    private fun String?.decodeHistory(): List<String> {
        if (this == null) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(this) }.getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_ENTRIES = 8
        val HISTORY = stringPreferencesKey("search_history")
    }
}
