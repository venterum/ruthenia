/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vntrum.ruthenia.core.data.backup.BackupManager
import com.vntrum.ruthenia.core.data.launcher.LauncherIconController
import com.vntrum.ruthenia.core.data.repository.FeedBuildProgress
import com.vntrum.ruthenia.core.data.repository.GithubRepository
import com.vntrum.ruthenia.core.data.repository.HomeRepository
import com.vntrum.ruthenia.core.data.settings.AccentPalette
import com.vntrum.ruthenia.core.data.settings.AppIconShape
import com.vntrum.ruthenia.core.data.settings.AppIconVariant
import com.vntrum.ruthenia.core.data.settings.AppLanguage
import com.vntrum.ruthenia.core.data.settings.AppSettings
import com.vntrum.ruthenia.core.data.settings.InstallMethod
import com.vntrum.ruthenia.core.data.settings.NavBarStyle
import com.vntrum.ruthenia.core.data.settings.SettingsRepository
import com.vntrum.ruthenia.core.data.settings.ThemeMode
import com.vntrum.ruthenia.core.github.GithubException
import com.vntrum.ruthenia.core.installer.shizuku.ShizukuInstaller
import com.vntrum.ruthenia.core.installer.shizuku.ShizukuStatus
import com.vntrum.ruthenia.core.security.CertificateRefresher
import com.vntrum.ruthenia.core.security.CertificateStatus
import com.vntrum.ruthenia.core.security.PinSource

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
    private val shizukuInstaller: ShizukuInstaller,
    private val certificateRefresher: CertificateRefresher,
    private val launcherIconController: LauncherIconController,
    private val homeRepository: HomeRepository,
    private val githubRepository: GithubRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppSettings(),
    )

    /** Availability of the optional silent install via Shizuku/Sui. */
    val shizukuStatus: StateFlow<ShizukuStatus> = shizukuInstaller.status

    // --- Optional GitHub token (encrypted with Android Keystore) ---

    private val _hasGithubToken = MutableStateFlow(githubRepository.hasToken())
    val hasGithubToken: StateFlow<Boolean> = _hasGithubToken.asStateFlow()

    private val _githubTokenBusy = MutableStateFlow(false)
    val githubTokenBusy: StateFlow<Boolean> = _githubTokenBusy.asStateFlow()

    fun saveGithubToken(token: String) = viewModelScope.launch {
        if (_githubTokenBusy.value) return@launch
        if (token.isBlank()) {
            githubRepository.setToken(null)
            _hasGithubToken.value = false
            _message.value = context.getString(R.string.settings_github_token_cleared)
            return@launch
        }

        _githubTokenBusy.value = true
        runCatching {
            githubRepository.setToken(token.trim())
            githubRepository.validateToken()
        }.onSuccess {
            _hasGithubToken.value = true
            _message.value = context.getString(R.string.settings_github_token_saved)
        }.onFailure { error ->
            if (error is GithubException.Unauthorized) {
                githubRepository.setToken(null)
                _hasGithubToken.value = false
                _message.value = context.getString(R.string.settings_github_token_invalid)
            } else {
                // A network/rate-limit failure does not mean the token is bad.
                _hasGithubToken.value = true
                _message.value = context.getString(R.string.settings_github_token_saved)
            }
        }
        _githubTokenBusy.value = false
    }

    fun clearGithubToken() = viewModelScope.launch {
        githubRepository.setToken(null)
        _hasGithubToken.value = false
        _message.value = context.getString(R.string.settings_github_token_cleared)
    }

    // --- RuStore certificate pins (manual refresh) ---

    private val _certificateStatus = MutableStateFlow(
        CertificateStatus(source = PinSource.BUNDLED, updatedAt = 0L, pinCount = 0),
    )
    val certificateStatus: StateFlow<CertificateStatus> = _certificateStatus.asStateFlow()

    private val _certificateBusy = MutableStateFlow(false)
    val certificateBusy: StateFlow<Boolean> = _certificateBusy.asStateFlow()

    init {
        viewModelScope.launch {
            _certificateStatus.value = withContext(Dispatchers.IO) { certificateRefresher.status() }
        }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsRepository.setThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDynamicColor(enabled)
    }

    fun setAccent(accent: AccentPalette) = viewModelScope.launch {
        settingsRepository.setAccent(accent)
    }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        settingsRepository.setLanguage(language)
    }

    fun setNavBarStyle(style: NavBarStyle) = viewModelScope.launch {
        settingsRepository.setNavBarStyle(style)
    }

    fun setAppIconShape(shape: AppIconShape) = viewModelScope.launch {
        settingsRepository.setAppIconShape(shape)
    }

    /** Selects the launcher-icon colour variant and applies it immediately. */
    fun setAppIconVariant(variant: AppIconVariant) = viewModelScope.launch {
        settingsRepository.setAppIconVariant(variant)
        launcherIconController.apply(variant)
    }

    fun setGamesEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setGamesEnabled(enabled)
    }

    // --- Curated collections (manual reload) ---

    /** Whether the curated collections are being re-resolved right now. */
    private val _collectionsBusy = MutableStateFlow(false)
    val collectionsBusy: StateFlow<Boolean> = _collectionsBusy.asStateFlow()

    /** How far the reload is, in percent — shown next to the reload row. */
    val collectionsProgress: StateFlow<FeedBuildProgress> = homeRepository.buildProgress

    /**
     * Re-resolves every curated category from the catalog. Resolution is cached
     * per app, so this only fetches what is missing or stale.
     */
    fun reloadCollections() = viewModelScope.launch {
        if (_collectionsBusy.value) return@launch
        _collectionsBusy.value = true
        val sections = runCatching { homeRepository.homeFeed(forceRefresh = true) }.getOrNull()
        _collectionsBusy.value = false
        _message.value = context.getString(
            if (sections != null && homeRepository.isComplete(sections)) {
                R.string.settings_collections_loaded
            } else {
                R.string.settings_collections_partial
            },
        )
    }

    fun setBackgroundChecks(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBackgroundChecksEnabled(enabled)
    }

    fun setIntervalHours(hours: Int) = viewModelScope.launch {
        settingsRepository.setUpdateCheckIntervalHours(hours)
    }

    fun setUnmeteredOnly(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setUnmeteredOnly(enabled)
    }

    fun setDiagnostics(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDiagnosticsEnabled(enabled)
    }

    fun setShowGooglePlayApps(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setShowGooglePlayApps(enabled)
    }

    fun setShowSideloadedApps(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setShowSideloadedApps(enabled)
    }

    fun setApkExportTreeUri(treeUri: String?) = viewModelScope.launch {
        settingsRepository.setApkExportTreeUri(treeUri)
    }

    fun setVerifySignatures(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setVerifySignatures(enabled)
    }

    fun setAutoInstall(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoInstall(enabled)
    }

    /** Switches between the system install dialog and silent Shizuku install. */
    fun setInstallMethod(method: InstallMethod) = viewModelScope.launch {
        settingsRepository.setInstallMethod(method)
    }

    /** Asks Shizuku for the API permission; the status flow reports the result. */
    fun requestShizukuPermission() {
        shizukuInstaller.requestPermission()
    }

    // --- RuStore certificate pins (manual refresh) ---

    /**
     * Refreshes the SPKI pins from the live RuStore chains and the official
     * Mintsifry CA. Runs only on an explicit tap; the new set is applied on the
     * next launch (the HTTP clients are built once).
     */
    fun refreshCertificates() = viewModelScope.launch {
        if (_certificateBusy.value) return@launch
        _certificateBusy.value = true
        runCatching { certificateRefresher.refresh() }
            .onSuccess {
                _certificateStatus.value = withContext(Dispatchers.IO) { certificateRefresher.status() }
                _message.value = context.getString(R.string.settings_certs_updated)
            }
            .onFailure {
                _message.value = context.getString(R.string.settings_certs_failed)
            }
        _certificateBusy.value = false
    }

    /** Drops the refreshed set and returns to the pins bundled in the APK. */
    fun resetCertificates() = viewModelScope.launch {
        withContext(Dispatchers.IO) { certificateRefresher.reset() }
        _certificateStatus.value = withContext(Dispatchers.IO) { certificateRefresher.status() }
        _message.value = context.getString(R.string.settings_certs_reset_done)
    }

    // --- Library and settings backup ---

    /** One-off message about the export/import result. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        runCatching {
            val payload = backupManager.export()
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(payload.toByteArray())
                } ?: error("Cannot open output stream for $uri")
            }
        }
            .onSuccess { _message.value = context.getString(R.string.settings_backup_exported) }
            .onFailure { _message.value = context.getString(R.string.settings_backup_failed) }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        runCatching {
            val payload = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().decodeToString()
                } ?: error("Cannot open input stream for $uri")
            }
            backupManager.import(payload)
        }
            .onSuccess { _message.value = context.getString(R.string.settings_backup_imported) }
            .onFailure { _message.value = context.getString(R.string.settings_backup_failed) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
