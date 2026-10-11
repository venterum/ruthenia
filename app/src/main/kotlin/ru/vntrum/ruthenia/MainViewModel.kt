// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.vntrum.ruthenia.core.data.model.UpdateStatus
import com.vntrum.ruthenia.core.data.model.isUpdateIgnored
import com.vntrum.ruthenia.core.data.model.isVisibleForCounters
import com.vntrum.ruthenia.core.data.repository.LibraryRepository
import com.vntrum.ruthenia.core.data.settings.AppSettings
import com.vntrum.ruthenia.core.data.settings.SettingsRepository
import com.vntrum.ruthenia.core.updates.DownloadManager
import com.vntrum.ruthenia.core.updates.DownloadTask

/**
 * Settings needed by the entry point (theme, dynamic colors), the updates badge
 * and auto-install of downloaded packages.
 *
 * Installation is always launched by the Activity: the system confirmation
 * dialog cannot be bypassed, so with auto-install enabled the screen receives
 * the intent from here.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    libraryRepository: LibraryRepository,
    private val downloadManager: DownloadManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppSettings(),
    )

    /**
     * Whether onboarding has been completed. `null` means settings have not yet
     * been read from DataStore; the shell waits for this value so it does not
     * flash the onboarding on every launch.
     */
    val onboardingCompleted: StateFlow<Boolean?> = settingsRepository.settings
        .map { it.onboardingCompleted }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = null,
        )

    /** Onboarding completed or skipped — never shown a second time. */
    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    /**
     * Number of available updates for the badge on the "Library" tab.
     * Counted across the whole library, regardless of the open screen.
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

    /** Intent for the system install dialog — launched by MainActivity. */
    private val _pendingConfirmation = MutableStateFlow<Intent?>(null)
    val pendingConfirmation: StateFlow<Intent?> = _pendingConfirmation.asStateFlow()

    /** Package name from an external link that should be opened in navigation. */
    private val _deepLink = MutableStateFlow<String?>(null)
    val deepLink: StateFlow<String?> = _deepLink.asStateFlow()

    fun openApp(packageName: String) {
        _deepLink.value = packageName
    }

    fun consumeDeepLink() {
        _deepLink.value = null
    }

    /** Latest downloads snapshot — used to find a task by package name. */
    private var downloads: List<DownloadTask> = emptyList()

    /** Already launched auto-installs (package@version) to avoid repeating. */
    private val autoInstalled = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            combine(
                downloadManager.downloads,
                settingsRepository.settings,
            ) { tasks, settings -> tasks to settings.autoInstall }
                .collect { (tasks, autoInstall) ->
                    downloads = tasks
                    if (!autoInstall) return@collect
                    tasks.filter { it.isReady }.forEach { task ->
                        if (autoInstalled.add("${task.packageName}@${task.versionCode}")) {
                            startInstall(task)
                        }
                    }
                }
        }
    }

    /** Install triggered by the "Install" notification action. */
    fun installPackage(packageName: String) {
        val task = downloads.firstOrNull { it.packageName == packageName }
        if (task != null) {
            startInstall(task)
            return
        }
        viewModelScope.launch {
            runCatching {
                downloadManager.install(
                    packageName = packageName,
                    appId = null,
                    name = null,
                    iconUrl = null,
                    publisher = null,
                ) { intent -> _pendingConfirmation.value = intent }
            }
        }
    }

    fun onConfirmationLaunched() {
        _pendingConfirmation.value = null
    }

    private fun startInstall(task: DownloadTask) {
        viewModelScope.launch {
            runCatching {
                downloadManager.install(
                    packageName = task.packageName,
                    appId = task.appId,
                    name = task.name,
                    iconUrl = task.iconUrl,
                    publisher = task.publisher,
                ) { intent -> _pendingConfirmation.value = intent }
            }
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
