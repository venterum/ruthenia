// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vntrum.ruthenia.core.data.identity.LocalIdentityRepository
import com.vntrum.ruthenia.core.data.identity.ProfileEmblem
import com.vntrum.ruthenia.core.data.identity.TokenRing
import com.vntrum.ruthenia.core.data.model.UpdateStatus
import com.vntrum.ruthenia.core.data.model.isUpdateIgnored
import com.vntrum.ruthenia.core.data.model.isVisibleForCounters
import com.vntrum.ruthenia.core.data.repository.LibraryRepository
import com.vntrum.ruthenia.core.data.settings.AccentPalette
import com.vntrum.ruthenia.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The local "device token": the token look, the codename seed and two on-device
 * counters.
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
        ringColor = AccentPalette.TEAL,
    ),
    val installedCount: Int = 0,
    val updateCount: Int = 0,
)

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

    /**
     * Generates a whole new token: the codename and the look change together,
     * because the name is part of the token.
     */
    fun randomizeToken() {
        viewModelScope.launch {
            identityRepository.regenerate()
            identityRepository.setEmblem(ProfileEmblem.random())
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
