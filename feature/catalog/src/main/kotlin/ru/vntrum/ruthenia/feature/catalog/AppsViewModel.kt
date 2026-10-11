/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.catalog

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.vntrum.ruthenia.core.data.identity.LocalIdentity
import com.vntrum.ruthenia.core.data.identity.LocalIdentityRepository
import com.vntrum.ruthenia.core.data.identity.ProfileEmblem
import com.vntrum.ruthenia.core.data.model.UpdateStatus
import com.vntrum.ruthenia.core.data.model.isUpdateIgnored
import com.vntrum.ruthenia.core.data.model.isVisibleForCounters
import com.vntrum.ruthenia.core.data.model.toUserMessage
import com.vntrum.ruthenia.core.data.repository.FeedBuildProgress
import com.vntrum.ruthenia.core.data.repository.HomeRepository
import com.vntrum.ruthenia.core.data.repository.HomeSection
import com.vntrum.ruthenia.core.data.repository.LibraryRepository
import com.vntrum.ruthenia.core.data.settings.SettingsRepository

/** State of the "Apps" tab: the curated feed of catalog sections. */
data class AppsUiState(
    val homeSections: List<HomeSection> = emptyList(),
    val homeLoading: Boolean = true,
    val homeError: String? = null,
)

/**
 * "Apps" tab: the curated home feed. Search lives on its own tab and has its
 * own ViewModel ([SearchViewModel]).
 */
@HiltViewModel
class AppsViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    libraryRepository: LibraryRepository,
    settingsRepository: SettingsRepository,
    identityRepository: LocalIdentityRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    /**
     * Progress of the initial feed build. The first launch resolves the whole
     * curated catalog, so the skeleton shows how far along it is instead of
     * looking frozen.
     */
    val loadProgress: StateFlow<FeedBuildProgress> = homeRepository.buildProgress

    /**
     * The locally generated device token shown in the header. Not an account:
     * the identity is minted on the device and never sent anywhere.
     */
    val identity: StateFlow<LocalIdentity> = identityRepository.identity
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = LocalIdentity(
                seed = "",
                firstSeen = 0L,
                emblem = ProfileEmblem.fromSeed(""),
            ),
        )

    /**
     * How many apps are awaiting an update — for the banner on the Apps page.
     * Accounts for source settings and ignored updates, like the tab badge.
     */
    val updateCount: StateFlow<Int> = combine(
        libraryRepository.observeLibrary(),
        settingsRepository.settings,
    ) { apps, settings ->
        apps.count { app ->
            app.status == UpdateStatus.UPDATE_AVAILABLE &&
                app.isVisibleForCounters(settings) &&
                !app.isUpdateIgnored(settings.ignoredUpdates)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = 0,
    )

    init {
        loadHome()
    }

    /** Feed: from cache or freshly loaded. */
    fun loadHome(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(homeLoading = true, homeError = null) }
            runCatching { homeRepository.homeFeed(forceRefresh) }
                .onSuccess { sections ->
                    _uiState.update {
                        it.copy(
                            homeLoading = false,
                            homeSections = sections,
                            homeError = if (sections.isEmpty()) {
                                context.getString(R.string.home_home_error)
                            } else {
                                null
                            },
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(homeLoading = false, homeError = throwable.toUserMessage(context))
                    }
                }
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
