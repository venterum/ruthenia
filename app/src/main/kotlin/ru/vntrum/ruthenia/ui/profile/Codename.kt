// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.ui.profile

/**
 * Deterministic "codename" of the device: adjective + noun, built from the
 * identity seed.
 *
 * Pure and stable — the same seed always yields the same name, and nothing is
 * stored or sent anywhere. The word lists come from resources, so the codename
 * is localised while staying deterministic for a given locale.
 */
internal fun codenameFor(
    seed: String,
    adjectives: List<String>,
    nouns: List<String>,
): String {
    require(adjectives.isNotEmpty() && nouns.isNotEmpty()) {
        "Codename word lists must not be empty"
    }
    val hash = seed.hashCode()
    val adjective = adjectives[hash.mod(adjectives.size)]
    val noun = nouns[(hash * 31).mod(nouns.size)]
    return "$adjective $noun"
}
