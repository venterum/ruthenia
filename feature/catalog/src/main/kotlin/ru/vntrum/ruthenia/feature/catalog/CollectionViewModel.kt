/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.vntrum.ruthenia.core.data.repository.HomeRepository
import com.vntrum.ruthenia.core.data.repository.HomeSection
import com.vntrum.ruthenia.core.data.repository.SelectionRepository

/** State of the collection screen. */
data class CollectionUiState(
    val isLoading: Boolean = true,
    val section: HomeSection? = null,
    val failed: Boolean = false,
)

/**
 * The collection screen gets its section by id from the navigation arguments.
 *
 * Previously the app list was passed as a JSON string directly in the route —
 * that didn't survive process death and grew along with the section. Now the
 * route is short, and the data comes from the home feed cache.
 */
@HiltViewModel
class CollectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val homeRepository: HomeRepository,
    private val selectionRepository: SelectionRepository,
) : ViewModel() {

    private val sectionId: String = checkNotNull(savedStateHandle[ARG_SECTION_ID]) {
        "sectionId argument is required"
    }

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, failed = false) }
            runCatching {
                if (SELECTION_ID.matches(sectionId)) {
                    selectionRepository.section(sectionId)
                } else {
                    homeRepository.section(sectionId)
                }
            }
                .onSuccess { section ->
                    _uiState.update { it.copy(isLoading = false, section = section) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, failed = true) }
                }
        }
    }

    companion object {
        const val ARG_SECTION_ID = "sectionId"

        /** RuStore editorial selection id — always a UUID. */
        private val SELECTION_ID =
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}