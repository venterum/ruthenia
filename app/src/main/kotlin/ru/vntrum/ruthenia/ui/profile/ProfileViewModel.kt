// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vntrum.ruthenia.core.data.identity.LocalIdentityRepository
import com.vntrum.ruthenia.core.data.identity.ProfileEmblem
import com.vntrum.ruthenia.core.data.identity.TOKEN_MOTIF_VARIANTS
import com.vntrum.ruthenia.core.data.identity.TokenRing
import com.vntrum.ruthenia.core.data.model.UpdateStatus
import com.vntrum.ruthenia.core.data.model.isUpdateIgnored
import com.vntrum.ruthenia.core.data.model.isVisibleForCounters
import com.vntrum.ruthenia.core.data.repository.LibraryRepository
import com.vntrum.ruthenia.core.data.settings.AccentPalette
import com.vntrum.ruthenia.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The local "device profile": the token look, the codename seed and two
 * on-device counters. The rank is derived from the counters, never stored.
 *
 * There is no account behind this — the seed is generated locally and the
 * counters are read from the local library only.
 */
data class ProfileUiState(
    val seed: String = "",
    val firstSeen: Long = 0L,
    val emblem: ProfileEmblem = ProfileEmblem(
        motif = 0,
        accent = AccentPalette.TEAL,
        ring = TokenRing.THIN,
    ),
    val installedCount: Int = 0,
    val updateCount: Int = 0,
) {

    /** Rank of the token, derived from the library size. */
    val rank: TokenRank get() = TokenRank.forCount(installedCount)
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val identityRepository: LocalIdentityRepository,
    libraryRepository: LibraryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        identityRepository.identity,
        libraryRepository.observeLibrary(),
        settingsRepository.settings,
    ) { identity, apps, settings ->
        val visible = apps.filter { it.isVisibleForCounters(settings) }
        ProfileUiState(
            seed = identity.seed,
            firstSeen = identity.firstSeen,
            emblem = identity.emblem,
            installedCount = visible.size,
            updateCount = visible.count { app ->
                app.status == UpdateStatus.UPDATE_AVAILABLE &&
                    !app.isUpdateIgnored(settings.ignoredUpdates)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = ProfileUiState(),
    )

    /** Saves a personalised token look. */
    fun setEmblem(emblem: ProfileEmblem) {
        viewModelScope.launch { identityRepository.setEmblem(emblem) }
    }

    /** Rerolls motif and accent while keeping the ring the user chose. */
    fun shuffleEmblem() {
        val current = uiState.value.emblem
        // Guarantee a different motif: step 1..7 around the ring of variants.
        val motif = (current.motif + 1 + Random.nextInt(TOKEN_MOTIF_VARIANTS - 1))
            .mod(TOKEN_MOTIF_VARIANTS)
        val accent = AccentPalette.entries.filter { it != current.accent }.random()
        viewModelScope.launch {
            identityRepository.setEmblem(current.copy(motif = motif, accent = accent))
        }
    }

    /** Mints a fresh codename seed. Purely local. */
    fun regenerateCodename() {
        viewModelScope.launch { identityRepository.regenerate() }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
