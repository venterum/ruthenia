/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * The device's local identity — deliberately **not** an account.
 *
 * [seed] is a random value minted on the device on first use, stored in DataStore
 * and never transmitted anywhere. It seeds the codename and the token rotation.
 * [firstSeen] is the first-launch timestamp, used for the "with us since" line.
 * [emblem] is the locally chosen look of the token.
 *
 * All of it is intentionally excluded from backups (like the APK export folder
 * and the local diagnostics): the identity belongs to a specific device.
 */
data class LocalIdentity(
    val seed: String,
    val firstSeen: Long,
    val emblem: ProfileEmblem,
)

/**
 * Reads and creates the local identity.
 *
 * The identity is reactive: [regenerate] mints a new seed and [setEmblem] saves a
 * new token look, and a fresh value is emitted so the UI updates without a restart.
 */
@Singleton
class LocalIdentityRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** The device's identity, creating and persisting it on first read. */
    val identity: Flow<LocalIdentity> = dataStore.data
        .map { preferences -> preferences.toIdentityOrNull() }
        .map { stored ->
            when {
                stored == null -> {
                    // First run: mint a seed and persist it. The write triggers a
                    // new emission, so the flow then resolves to the stored value.
                    dataStore.edit { it.mintIdentity() }
                    null
                }

                // Upgraded installs have a seed but no timestamp yet: backfill it
                // once, without touching the seed.
                stored.firstSeen == 0L -> {
                    val now = System.currentTimeMillis()
                    dataStore.edit { it[Keys.FIRST_SEEN] = now }
                    stored.copy(firstSeen = now)
                }

                else -> stored
            }
        }
        .filterNotNull()

    /**
     * Replaces the seed with a fresh one — the "another codename" action.
     * Purely local: nothing leaves the device.
     */
    suspend fun regenerate() {
        dataStore.edit { it[Keys.SEED] = UUID.randomUUID().toString() }
    }

    /** Saves the chosen token look. */
    suspend fun setEmblem(emblem: ProfileEmblem) {
        dataStore.edit { preferences ->
            preferences[Keys.EMBLEM_MOTIF] = emblem.motif
            preferences[Keys.EMBLEM_ACCENT] = emblem.accent.name
            preferences[Keys.EMBLEM_RING] = emblem.ring.name
            preferences[Keys.EMBLEM_RING_COLOR] = emblem.ringColor.name
        }
    }

    private fun Preferences.toIdentityOrNull(): LocalIdentity? {
        val seed = this[Keys.SEED] ?: return null
        val firstSeen = this[Keys.FIRST_SEEN] ?: 0L
        val motif = this[Keys.EMBLEM_MOTIF]
        val accent = this[Keys.EMBLEM_ACCENT]?.let { name ->
            TokenColor.entries.firstOrNull { it.name == name }
        }
        // Unknown or legacy frame names safely fall back to the default.
        val ring = this[Keys.EMBLEM_RING]?.let { name ->
            TokenRing.entries.firstOrNull { it.name == name }
        }
        val ringColor = this[Keys.EMBLEM_RING_COLOR]?.let { name ->
            TokenColor.entries.firstOrNull { it.name == name }
        }
        val emblem = if (motif != null && accent != null && ring != null) {
            ProfileEmblem(
                motif = motif,
                accent = accent,
                ring = ring,
                ringColor = ringColor ?: accent,
            )
        } else {
            // Installations from before the token existed fall back to the seed.
            ProfileEmblem.fromSeed(seed)
        }
        return LocalIdentity(seed = seed, firstSeen = firstSeen, emblem = emblem)
    }

    private fun MutablePreferences.mintIdentity() {
        val seed = UUID.randomUUID().toString()
        this[Keys.SEED] = seed
        this[Keys.FIRST_SEEN] = System.currentTimeMillis()
        val emblem = ProfileEmblem.fromSeed(seed)
        this[Keys.EMBLEM_MOTIF] = emblem.motif
        this[Keys.EMBLEM_ACCENT] = emblem.accent.name
        this[Keys.EMBLEM_RING] = emblem.ring.name
        this[Keys.EMBLEM_RING_COLOR] = emblem.ringColor.name
    }

    private object Keys {
        val SEED = stringPreferencesKey("local_identity_seed")
        val FIRST_SEEN = longPreferencesKey("local_identity_first_seen")
        val EMBLEM_MOTIF = intPreferencesKey("profile_emblem_motif")
        val EMBLEM_ACCENT = stringPreferencesKey("profile_emblem_accent")
        val EMBLEM_RING = stringPreferencesKey("profile_emblem_ring")
        val EMBLEM_RING_COLOR = stringPreferencesKey("profile_emblem_ring_color")
    }
}
