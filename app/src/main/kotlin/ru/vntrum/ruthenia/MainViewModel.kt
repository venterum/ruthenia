// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    /** Package name from an external link that should be opened in navigation. */
    private val _deepLink = MutableStateFlow<String?>(null)
    val deepLink: StateFlow<String?> = _deepLink.asStateFlow()

    /** Notification asked to open the Library downloads card. */
    private val _openDownloads = MutableStateFlow(0)
    val openDownloads: StateFlow<Int> = _openDownloads.asStateFlow()

    private var foreground = false
    private var autoInstallEnabled = false

    fun openApp(packageName: String) {
        _deepLink.value = packageName
    }

    fun consumeDeepLink() {
        _deepLink.value = null
    }

    fun requestOpenDownloads() {
        _openDownloads.value += 1
    }

    /** MainActivity forwards its lifecycle; auto-install needs a foreground Activity. */
    fun setForeground(isForeground: Boolean) {
        foreground = isForeground
        if (isForeground) maybeAutoInstall()
    }

    /** Notification "Retry" action. */
    fun retryPackage(packageName: String) {
        viewModelScope.launch {
            // Do not rely on the in-memory snapshot: a notification can start
            // the process before the downloads collector has emitted.
            val task = runCatching {
                downloadManager.downloads.first().firstOrNull {
                    it.packageName == packageName
                }
            }.getOrNull()
            when {
                task == null -> Unit
                // A Ready task with an install failure must retry the install,
                // not download the package again.
                task.isReady -> startInstall(task)
                task.isPaused -> downloadManager.resume(packageName)
                else -> downloadManager.retry(task)
            }
        }
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
                    autoInstallEnabled = autoInstall
                    maybeAutoInstall()
                }
        }
    }

    private fun maybeAutoInstall() {
        if (!autoInstallEnabled || !foreground) return
        // The system install dialog cannot be opened from the background;
        // the ready notification handles that case.
        downloads.filter { it.isReady && !it.isActive }.forEach { task ->
            if (autoInstalled.add("${task.packageName}@${task.versionCode}")) {
                startInstall(task)
            }
        }
    }

    /** Install triggered by the "Install" notification action. */
    fun installPackage(packageName: String) {
        viewModelScope.launch {
            val task = runCatching {
                downloadManager.downloads.first().firstOrNull {
                    it.packageName == packageName
                }
            }.getOrNull()
            // A stale notification may point at a task that was paused,
            // removed or failed after it was posted.
            if (task != null && !task.isReady) return@launch
            runCatching {
                downloadManager.install(packageName)
            }
        }
    }

    private fun startInstall(task: DownloadTask) {
        viewModelScope.launch {
            runCatching {
                downloadManager.install(task.packageName)
            }
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
