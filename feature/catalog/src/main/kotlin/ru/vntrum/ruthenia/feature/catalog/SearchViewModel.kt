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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.vntrum.ruthenia.core.data.model.toUserMessage
import com.vntrum.ruthenia.core.data.repository.CatalogRepository
import com.vntrum.ruthenia.core.data.repository.HomeRepository
import com.vntrum.ruthenia.core.data.repository.HomeSection
import com.vntrum.ruthenia.core.data.repository.SelectionRepository
import com.vntrum.ruthenia.core.data.search.SearchHistoryRepository
import com.vntrum.ruthenia.core.network.CatalogApp
import com.vntrum.ruthenia.core.network.RuStoreSelection

/**
 * State of the "Search" tab: live search plus the browse content (category
 * grid and editorial selections) shown while the query is empty.
 */
data class SearchUiState(
    val query: String = "",
    val results: List<CatalogApp> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    /** The next page of results is being loaded. */
    val isLoadingMore: Boolean = false,
    /** The page failed to load — we show "Retry" instead of silent waiting. */
    val loadMoreFailed: Boolean = false,
    /** The catalog returned an empty page — there are no more results. */
    val endReached: Boolean = false,
    val categories: List<HomeSection> = emptyList(),
    val selections: List<RuStoreSelection> = emptyList(),
    val browseLoading: Boolean = true,
    val browseFailed: Boolean = false,
) {
    /** Search mode: show results instead of the browse content. */
    val isSearchMode: Boolean get() = query.isNotBlank() || hasSearched
}

/**
 * "Search" tab: search by name or package name and, before the first query,
 * catalog categories and RuStore editorial selections.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val homeRepository: HomeRepository,
    private val selectionRepository: SelectionRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Recent successful queries — shown above the categories. */
    val searchHistory: StateFlow<List<String>> = searchHistoryRepository.history.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = emptyList(),
    )

    private var searchJob: Job? = null

    /** The query the current results belong to, and the last page number. */
    private var activeQuery: String? = null
    private var searchPage = 0

    init {
        loadBrowse()
    }

    /**
     * Browse content for the empty query: catalog categories from the home
     * feed and RuStore editorial selections. Selections are non-critical — if
     * they fail to load, we show the categories alone.
     */
    fun loadBrowse() {
        viewModelScope.launch {
            _uiState.update { it.copy(browseLoading = true, browseFailed = false) }
            val categories = runCatching { homeRepository.categories() }.getOrDefault(emptyList())
            val selections = runCatching { selectionRepository.selections() }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    browseLoading = false,
                    categories = categories,
                    selections = selections,
                    browseFailed = categories.isEmpty(),
                )
            }
        }
    }

    /**
     * Live search: the query is sent with a small delay after the last
     * keystroke, and an empty field brings back the browse content.
     */
    fun onQueryChange(value: String) {
        // Don't show stale results from a different query: as soon as the text
        // no longer matches the active query, the list is cleared and the
        // skeleton takes its place.
        val staleResults = activeQuery != null && value.trim() != activeQuery
        _uiState.update {
            it.copy(
                query = value,
                results = if (staleResults) emptyList() else it.results,
                isLoading = value.isNotBlank(),
                error = null,
                loadMoreFailed = false,
            )
        }
        searchJob?.cancel()

        if (value.isBlank()) {
            activeQuery = null
            searchPage = 0
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    hasSearched = false,
                    error = null,
                    isLoading = false,
                    isLoadingMore = false,
                    loadMoreFailed = false,
                    endReached = false,
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(value.trim(), recordInHistory = false)
        }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(query, recordInHistory = true) }
    }

    /** Substitutes the suggestion and immediately performs a search. */
    fun searchSuggestion(value: String) {
        onQueryChange(value)
        this.search()
    }

    /** Clears the query history (the button next to the history). */
    fun clearSearchHistory() {
        viewModelScope.launch { searchHistoryRepository.clear() }
    }

    private suspend fun runSearch(query: String, recordInHistory: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = true,
                isLoadingMore = false,
                loadMoreFailed = false,
                endReached = false,
                error = null,
            )
        }
        runCatching { catalogRepository.search(query, page = 0) }
            .onSuccess { results ->
                activeQuery = query
                searchPage = 0
                if (recordInHistory) searchHistoryRepository.add(query)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        results = results,
                        hasSearched = true,
                        error = null,
                        endReached = results.isEmpty(),
                    )
                }
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasSearched = true,
                        error = throwable.toUserMessage(context),
                    )
                }
            }
    }

    /**
     * Loads the next page of results. Called when the last item in the list
     * becomes visible; repeated calls are cut off by the loading flag, and
     * results from a "different" query are not mixed into the new one.
     */
    fun loadMore() {
        val query = activeQuery ?: return
        val snapshot = _uiState.value
        if (snapshot.isLoading || snapshot.isLoadingMore || snapshot.endReached || !snapshot.hasSearched) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreFailed = false) }
            val nextPage = searchPage + 1
            runCatching { catalogRepository.search(query, page = nextPage) }
                .onSuccess { page ->
                    if (activeQuery != query) return@onSuccess
                    searchPage = nextPage
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            results = appendDistinctApps(it.results, page),
                            endReached = page.isEmpty(),
                        )
                    }
                }
                .onFailure {
                    if (activeQuery == query) {
                        // Show "Retry": otherwise the failure is silent and the
                        // sentinel can't restart because the list size hasn't
                        // changed.
                        _uiState.update { it.copy(isLoadingMore = false, loadMoreFailed = true) }
                    }
                }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}

/**
 * Appends a new page to the current results, skipping apps that are already in
 * the list: the catalog may repeat boundary items between pages.
 */
internal fun appendDistinctApps(
    existing: List<CatalogApp>,
    next: List<CatalogApp>,
): List<CatalogApp> {
    if (next.isEmpty()) return existing
    val seen = existing.mapTo(HashSet(existing.size)) { it.packageName }
    return existing + next.filter { seen.add(it.packageName) }
}
