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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.vntrum.ruthenia.core.data.model.toUserMessage
import com.vntrum.ruthenia.core.data.repository.HomeRepository
import com.vntrum.ruthenia.core.data.repository.HomeSection

/** State of the "Games" tab: a "Popular games" rail and genre rails. */
data class GamesUiState(
    val isLoading: Boolean = true,
    val sections: List<HomeSection> = emptyList(),
    val error: String? = null,
)

/**
 * "Games" tab: a curated, non-personal games feed built from fixed genre
 * queries, cached in the same store as the apps feed.
 */
@HiltViewModel
class GamesViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GamesUiState())
    val uiState: StateFlow<GamesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { homeRepository.gamesFeed(forceRefresh) }
                .onSuccess { sections ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sections = sections,
                            error = if (sections.isEmpty()) {
                                context.getString(R.string.games_error)
                            } else {
                                null
                            },
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, error = throwable.toUserMessage(context))
                    }
                }
        }
    }
}
