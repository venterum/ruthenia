/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.vntrum.ruthenia.core.data.repository.GithubRepository
import com.vntrum.ruthenia.core.github.GithubAsset
import com.vntrum.ruthenia.core.github.GithubException
import com.vntrum.ruthenia.core.github.GithubRelease
import com.vntrum.ruthenia.core.github.GithubRepo
import com.vntrum.ruthenia.core.github.GithubUrlParser
import com.vntrum.ruthenia.core.updates.GithubInstallManager
import com.vntrum.ruthenia.core.updates.GithubInstallRequest
import com.vntrum.ruthenia.core.updates.GithubInstallState

data class GithubAddUiState(
    val input: String = "",
    val isLoading: Boolean = false,
    val repo: GithubRepo? = null,
    val releases: List<GithubRelease> = emptyList(),
    val selectedRelease: GithubRelease? = null,
    val selectedAsset: GithubAsset? = null,
    val includePrereleases: Boolean = false,
    val error: String? = null,
    val hasToken: Boolean = false,
    val install: GithubInstallState = GithubInstallState.Idle,
) {
    val availableReleases: List<GithubRelease>
        get() = releases.filter { includePrereleases || !it.prerelease }

    val canInstall: Boolean
        get() = repo != null &&
            selectedRelease != null &&
            selectedAsset != null &&
            install !is GithubInstallState.Running &&
            install !is GithubInstallState.Queued
}

@HiltViewModel
class GithubAddViewModel @Inject constructor(
    private val githubRepository: GithubRepository,
    private val installManager: GithubInstallManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GithubAddUiState(hasToken = githubRepository.hasToken()))
    val uiState: StateFlow<GithubAddUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value, error = null) }
    }

    fun load() {
        val coordinates = GithubUrlParser.parse(_uiState.value.input)
        if (coordinates == null) {
            _uiState.update { it.copy(error = context.getString(R.string.github_error_invalid_url)) }
            return
        }
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    repo = null,
                    releases = emptyList(),
                    selectedRelease = null,
                    selectedAsset = null,
                    hasToken = githubRepository.hasToken(),
                )
            }
            runCatching {
                val repo = githubRepository.repo(coordinates.owner, coordinates.repo)
                val releases = githubRepository.releases(coordinates.owner, coordinates.repo)
                repo to releases
            }.onSuccess { (repo, releases) ->
                val selected = if (_uiState.value.includePrereleases) {
                    releases.firstOrNull()
                } else {
                    releases.firstOrNull { !it.prerelease }
                }
                val assets = selected?.apkAssets.orEmpty()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        repo = repo,
                        releases = releases,
                        selectedRelease = selected,
                        selectedAsset = assets.singleOrNull(),
                        error = when {
                            releases.isEmpty() -> context.getString(R.string.github_no_releases)
                            assets.isEmpty() -> context.getString(R.string.github_error_no_apk)
                            else -> null
                        },
                        install = GithubInstallState.Idle,
                    )
                }
                watchInstall(repo.fullName)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = throwable.toGithubMessage(context),
                    )
                }
            }
        }
    }

    fun selectRelease(release: GithubRelease) {
        if (_uiState.value.install is GithubInstallState.Running) return
        val assets = release.apkAssets
        _uiState.update {
            it.copy(
                selectedRelease = release,
                selectedAsset = assets.singleOrNull(),
                error = if (assets.isEmpty()) {
                    context.getString(R.string.github_error_no_apk)
                } else {
                    null
                },
                install = if (it.install.isBusy()) it.install else GithubInstallState.Idle,
            )
        }
    }

    fun selectAsset(asset: GithubAsset) {
        _uiState.update {
            it.copy(
                selectedAsset = asset,
                error = null,
                install = if (it.install.isBusy()) it.install else GithubInstallState.Idle,
            )
        }
    }

    fun setIncludePrereleases(enabled: Boolean) {
        _uiState.update { state ->
            val releases = state.releases.filter { enabled || !it.prerelease }
            val selected = state.selectedRelease?.takeIf { enabled || !it.prerelease }
                ?: releases.firstOrNull()
            state.copy(
                includePrereleases = enabled,
                selectedRelease = selected,
                selectedAsset = selected?.apkAssets?.singleOrNull(),
            )
        }
    }

    fun startInstall() {
        val state = _uiState.value
        val repo = state.repo ?: return
        val release = state.selectedRelease ?: return
        val asset = state.selectedAsset ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(install = GithubInstallState.Queued) }
            runCatching {
                installManager.enqueue(
                    GithubInstallRequest(
                        owner = repo.owner,
                        repo = repo.name,
                        repoId = repo.id,
                        displayName = repo.name,
                        avatarUrl = repo.avatarUrl,
                        releaseId = release.id,
                        releaseTag = release.tagName,
                        releaseVersionName = release.versionName,
                        assetName = asset.name,
                        assetUrl = asset.downloadUrl,
                        assetSize = asset.size,
                        assetSha256 = asset.sha256,
                        includePrereleases = state.includePrereleases,
                    ),
                )
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        install = GithubInstallState.Failed(throwable.message),
                        error = context.getString(R.string.github_error_generic),
                    )
                }
            }
        }
    }

    fun cancelInstall() {
        _uiState.value.repo?.let { installManager.cancel(it.fullName) }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refreshToken() {
        _uiState.update { it.copy(hasToken = githubRepository.hasToken()) }
    }

    private fun watchInstall(repoFullName: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            installManager.observe(repoFullName).collect { install ->
                _uiState.update { it.copy(install = install) }
            }
        }
    }
}

private fun GithubInstallState.isBusy(): Boolean =
    this is GithubInstallState.Running || this is GithubInstallState.Queued

private fun Throwable.toGithubMessage(context: Context): String = when (this) {
    is GithubException.Unauthorized -> context.getString(R.string.github_error_auth)
    is GithubException.RateLimited -> context.getString(R.string.github_error_rate_limit)
    is GithubException.NotFound -> context.getString(R.string.github_error_not_found)
    is GithubException.Network -> context.getString(R.string.github_error_network)
    else -> context.getString(R.string.github_error_generic)
}
