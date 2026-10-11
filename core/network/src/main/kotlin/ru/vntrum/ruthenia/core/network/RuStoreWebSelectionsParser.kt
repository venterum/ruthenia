/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A selection card from the RuStore web catalog payload. */
@Serializable
internal data class WebSelectionDto(
    val id: String,
    val title: String? = null,
    val subtitle: String? = null,
    val imageUrl: String? = null,
)

/** An app inside a selection from the web catalog payload. */
@Serializable
internal data class WebAppDto(
    val appId: Long,
    val appName: String? = null,
    val packageName: String? = null,
    val shortDescription: String? = null,
    val iconUrl: String? = null,
    val averageUserRating: Double? = null,
    val totalRatings: Long? = null,
    val labels: List<WebLabelDto> = emptyList(),
)

@Serializable
internal data class WebLabelDto(val id: Int? = null)

/**
 * Parsing of RuStore editorial selections from the server payload of the web catalog.
 *
 * There is no official JSON API for selections, but the pages `rustore.ru/catalog` and
 * `/catalog/featuring/{id}` serve data in the Next.js streaming format (RSC).
 * The format is not a public contract, so the parser is intentionally lenient:
 * it extracts balanced JSON objects by markers and ignores anything
 * unfamiliar. If RuStore changes the format, the layer returns an empty list, and the UI
 * simply does not show the selections section.
 */
internal object RuStoreWebSelectionsParser {

    /** UUID tail: ids of selections and apps. */
    private val SELECTION_ID = Regex("""\{"id":"([0-9a-fA-F-]{36})"""")

    /** Start of the apps array inside the `content` object. */
    private val APPS_ARRAY = Regex(""""apps":\[""")

    /** Fallback: individual app cards `{"app":{...}}`. */
    private val APP_OBJECT = Regex(""""app":\{""")

    fun parseSelections(text: String, json: Json): List<RuStoreSelection> {
        val result = LinkedHashMap<String, RuStoreSelection>()
        var match = SELECTION_ID.find(text)
        while (match != null) {
            val raw = extractJsonValue(text, match.range.first)
            if (raw != null) {
                val dto = runCatching { json.decodeFromString<WebSelectionDto>(raw) }.getOrNull()
                val title = dto?.title?.takeIf { it.isNotBlank() }
                if (dto != null && title != null) {
                    result[dto.id] = RuStoreSelection(
                        id = dto.id,
                        title = title,
                        subtitle = dto.subtitle?.takeIf { it.isNotBlank() },
                        coverUrl = dto.imageUrl?.takeIf { it.isNotBlank() },
                    )
                }
            }
            match = match.next()
        }
        return result.values.toList()
    }

    fun parseApps(text: String, json: Json): List<CatalogApp> {
        val apps = parseAppsArray(text, json)
            ?: parseAppObjects(text, json)
        val seen = LinkedHashMap<String, CatalogApp>()
        apps.forEach { app ->
            val model = app.toCatalogApp() ?: return@forEach
            seen.putIfAbsent(model.packageName, model)
        }
        return seen.values.toList()
    }

    private fun parseAppsArray(text: String, json: Json): List<WebAppDto>? {
        val match = APPS_ARRAY.find(text) ?: return null
        val raw = extractJsonValue(text, match.range.last) ?: return null
        return runCatching { json.decodeFromString<List<WebAppDto>>(raw) }.getOrNull()
    }

    private fun parseAppObjects(text: String, json: Json): List<WebAppDto> {
        val result = mutableListOf<WebAppDto>()
        var match = APP_OBJECT.find(text)
        while (match != null) {
            val raw = extractJsonValue(text, match.range.last)
            if (raw != null) {
                runCatching { json.decodeFromString<WebAppDto>(raw) }
                    .getOrNull()
                    ?.let(result::add)
            }
            match = match.next()
        }
        return result
    }

    /**
     * Returns the JSON value from [start] (a `{` or `[` is expected) up to the matching
     * closing bracket, correctly skipping strings and escaping. `null`
     * if the value is not balanced.
     */
    private fun extractJsonValue(text: String, start: Int): String? {
        val first = text.getOrNull(start) ?: return null
        val expectedClose = when (first) {
            '{' -> '}'
            '[' -> ']'
            else -> return null
        }
        val stack = ArrayDeque<Char>().apply { addLast(expectedClose) }
        var inString = false
        var escaped = false
        var index = start + 1
        while (index < text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> stack.addLast('}')
                    '[' -> stack.addLast(']')
                    '}', ']' -> {
                        if (stack.isEmpty() || stack.last() != char) return null
                        stack.removeLast()
                        if (stack.isEmpty()) return text.substring(start, index + 1)
                    }
                }
            }
            index++
        }
        return null
    }

    private fun WebAppDto.toCatalogApp(): CatalogApp? {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: return null
        return CatalogApp(
            appId = appId,
            packageName = pkg,
            name = appName?.takeIf { it.isNotBlank() } ?: pkg,
            shortDescription = shortDescription,
            iconUrl = iconUrl,
            versionCode = null,
            minSdkVersion = null,
            averageUserRating = averageUserRating,
            totalRatings = totalRatings,
            downloadsText = null,
            labelIds = labels.mapNotNull { it.id },
        )
    }
}
