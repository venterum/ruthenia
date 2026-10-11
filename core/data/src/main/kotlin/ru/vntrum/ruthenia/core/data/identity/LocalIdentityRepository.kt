/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
 * and never transmitted anywhere. It only seeds the drawn "device seal" in the
 * Apps header, so every install looks a little different without a login.
 * It is intentionally excluded from backups (like the APK export folder and the
 * local diagnostics): the seal belongs to a specific device.
 */
data class LocalIdentity(
    val seed: String,
)

/**
 * Reads and creates the local identity.
 *
 * The identity is reactive: [regenerate] mints a new seed and a fresh value is
 * emitted, so the seal on screen can change without a restart.
 */
@Singleton
class LocalIdentityRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** The device's identity, creating and persisting the seed on first read. */
    val identity: Flow<LocalIdentity> = dataStore.data
        .map { preferences -> preferences[Keys.INSTALL_ID] }
        .map { stored ->
            if (stored != null) {
                LocalIdentity(seed = stored)
            } else {
                // First run: mint a seed and persist it. The write triggers a
                // new emission, so the flow then resolves to the stored value.
                dataStore.edit { it[Keys.INSTALL_ID] = newSeed() }
                null
            }
        }
        .filterNotNull()

    /**
     * Replaces the seed with a fresh one — the "change the seal" action.
     * Purely local: nothing leaves the device.
     */
    suspend fun regenerate() {
        dataStore.edit { it[Keys.INSTALL_ID] = newSeed() }
    }

    private fun newSeed(): String = UUID.randomUUID().toString()

    private object Keys {
        val INSTALL_ID = stringPreferencesKey("local_identity_seed")
    }
}
