/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.appdetails

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.vntrum.ruthenia.core.data.model.InstalledApp
import com.vntrum.ruthenia.core.data.model.UpdateStatus
import com.vntrum.ruthenia.core.data.model.isUpdateIgnored
import com.vntrum.ruthenia.core.data.model.toUserMessage
import com.vntrum.ruthenia.core.data.repository.CatalogRepository
import com.vntrum.ruthenia.core.data.repository.LibraryRepository
import com.vntrum.ruthenia.core.data.settings.SettingsRepository
import com.vntrum.ruthenia.core.installer.InstallException
import com.vntrum.ruthenia.core.installer.InstallResult
import com.vntrum.ruthenia.core.network.AppDetails
import com.vntrum.ruthenia.core.network.CatalogApp
import com.vntrum.ruthenia.core.updates.ApkExportManager
import com.vntrum.ruthenia.core.updates.DownloadManager
import com.vntrum.ruthenia.core.updates.DownloadRequest
import com.vntrum.ruthenia.core.updates.DownloadState
import com.vntrum.ruthenia.core.updates.DownloadTask
import com.vntrum.ruthenia.core.updates.isBusy

/** State of the installation process on the detail screen. */
sealed interface InstallUiState {
    data object Idle : InstallUiState

    data class Downloading(val fraction: Float?) : InstallUiState

    /** Files are downloaded, awaiting a tap on "Install". */
    data object Ready : InstallUiState

    data object Installing : InstallUiState

    data object Done : InstallUiState

    data class Failed(val message: String) : InstallUiState
}

data class AppDetailsUiState(
    val isLoading: Boolean = true,
    val details: AppDetails? = null,
    val installed: InstalledApp? = null,
    val error: String? = null,
    val install: InstallUiState = InstallUiState.Idle,
    val developerApps: List<CatalogApp> = emptyList(),
    /** Rating from the catalog, if it could be fetched. */
    val rating: Double? = null,
    val ratingsCount: Long? = null,
    /** Saved data is shown: the network didn't respond. */
    val isOffline: Boolean = false,
    /** The app update is hidden by the user. */
    val updateHidden: Boolean = false,
) {
    val canInstall: Boolean
        get() = details != null && details.isAggregated.not() &&
            install !is InstallUiState.Downloading && install !is InstallUiState.Installing

    val updateAvailable: Boolean
        get() = installed?.status == UpdateStatus.UPDATE_AVAILABLE
}

/**
 * App detail screen. Downloading goes through the shared download manager: the
 * button queues a download, and progress, readiness, and installation reflect
 * its state. The system confirmation dialog is opened from the Activity.
 */
@HiltViewModel
class AppDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
    private val libraryRepository: LibraryRepository,
    private val downloadManager: DownloadManager,
    private val settingsRepository: SettingsRepository,
    private val exportManager: ApkExportManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val packageName: String = requireNotNull(savedStateHandle[ARG_PACKAGE_NAME]) {
        "packageName argument is required"
    }

    private val _uiState = MutableStateFlow(AppDetailsUiState())
    val uiState: StateFlow<AppDetailsUiState> = _uiState.asStateFlow()

    /** Intent of the system confirmation dialog — launched from the Activity. */
    private val _pendingConfirmation = MutableStateFlow<Intent?>(null)
    val pendingConfirmation: StateFlow<Intent?> = _pendingConfirmation.asStateFlow()

    /** One-off hint messages (for example, about the APK folder). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Short-lived installation state (installing, done, install error). While
     * it's set, it takes precedence over the download state from the manager.
     */
    private val transientInstall = MutableStateFlow<InstallUiState?>(null)

    /** Last known download state from the manager. */
    private var downloadState: DownloadState = DownloadState.Idle
    private var downloadTask: DownloadTask? = null

    init {
        refresh()
        viewModelScope.launch {
            combine(
                libraryRepository.observe(packageName),
                settingsRepository.settings,
            ) { installed, settings ->
                installed to (installed?.isUpdateIgnored(settings.ignoredUpdates) == true)
            }.collect { (installed, hidden) ->
                _uiState.update { it.copy(installed = installed, updateHidden = hidden) }
            }
        }
        viewModelScope.launch {
            downloadManager.downloads.collect { tasks ->
                downloadTask = tasks.firstOrNull { it.packageName == packageName }
                downloadState = downloadTask?.state ?: DownloadState.Idle
                publishInstallState()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isOffline = false) }
            runCatching { catalogRepository.appDetails(packageName) }
                .onSuccess { details ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            details = details,
                            error = null,
                            isOffline = false,
                        )
                    }
                    details.publicCompanyId?.let(::loadDeveloperApps)
                    loadRating()
                }
                .onFailure { throwable ->
                    // The network didn't respond — show what we already know:
                    // the screen opens offline too, albeit without a description
                    // or screenshots.
                    val fallback = offlineDetails()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            details = fallback ?: it.details,
                            error = if (fallback == null) throwable.toUserMessage(context) else null,
                            isOffline = fallback != null,
                        )
                    }
                }
        }
    }

    /** Partial detail screen from the catalog and library caches. */
    private suspend fun offlineDetails(): AppDetails? {
        val cached = catalogRepository.cachedApp(packageName)
        val installed = libraryRepository.get(packageName)
        if (cached == null && installed == null) return null

        return AppDetails(
            appId = cached?.appId ?: installed?.appId ?: 0L,
            packageName = packageName,
            name = cached?.name ?: installed?.name ?: packageName,
            publisher = installed?.publisher,
            versionName = installed?.latestVersionName ?: installed?.installedVersionName,
            versionCode = installed?.latestVersionCode
                ?: installed?.installedVersionCode
                ?: cached?.versionCode,
            whatsNew = installed?.whatsNew,
            updatedAt = null,
            iconUrl = cached?.iconUrl ?: installed?.iconUrl,
            minSdkVersion = cached?.minSdkVersion,
            targetSdkVersion = null,
            fileSize = null,
            isAggregated = false,
            shortDescription = cached?.shortDescription,
        )
    }

    /**
     * Rating on the detail screen. We first take the saved catalog record, and
     * only if it's missing do we make a search query.
     */
    private fun loadRating() {
        viewModelScope.launch {
            val cached = catalogRepository.cachedApp(packageName)
            val match = if (cached?.averageUserRating != null) {
                cached
            } else {
                runCatching { catalogRepository.search(packageName) }
                    .getOrDefault(emptyList())
                    .firstOrNull { it.packageName.equals(packageName, ignoreCase = true) }
            } ?: return@launch

            _uiState.update {
                it.copy(rating = match.averageUserRating, ratingsCount = match.totalRatings)
            }
        }
    }

    /** Hides the update or returns it to the updates list. */
    fun toggleUpdateVisibility() {
        val installed = _uiState.value.installed ?: return
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (installed.isUpdateIgnored(settings.ignoredUpdates)) {
                settingsRepository.restoreUpdateVisibility(packageName)
            } else {
                settingsRepository.ignoreUpdate(packageName, installed.latestVersionCode)
            }
        }
    }

    /** Saves the APK to the folder from settings; without a folder, prompts to choose one. */
    fun exportApk() {
        val details = _uiState.value.details ?: return
        if (details.isAggregated) return

        viewModelScope.launch {
            val treeUri = settingsRepository.settings.first().apkExportTreeUri
            if (treeUri.isNullOrBlank()) {
                _message.value = context.getString(R.string.app_details_export_no_folder)
            } else {
                exportManager.request(
                    packageName = packageName,
                    appId = details.appId,
                    name = details.name,
                    versionName = details.versionName,
                    treeUri = treeUri,
                )
                _message.value = context.getString(R.string.app_details_export_started)
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Other apps by the same developer — an optional section. */
    private fun loadDeveloperApps(companyId: String) {
        viewModelScope.launch {
            val apps = runCatching { catalogRepository.developerApps(companyId) }
                .getOrDefault(emptyList())
                .filter { it.packageName != packageName }
            _uiState.update { it.copy(developerApps = apps) }
        }
    }

    /** Cancels an in-flight download — the action bar offers it while busy. */
    fun cancelDownload() {
        downloadManager.cancel(packageName)
    }

    /**
     * A single button action: if the files are already downloaded — install,
     * otherwise queue the download in the shared manager.
     */
    fun install() {
        val details = _uiState.value.details ?: return
        if (!_uiState.value.canInstall) return

        if (downloadState is DownloadState.Ready) {
            installDownloaded(details)
        } else {
            viewModelScope.launch {
                downloadManager.request(
                    DownloadRequest(
                        packageName = packageName,
                        appId = details.appId,
                        versionCode = details.versionCode,
                        name = details.name,
                        iconUrl = details.iconUrl,
                        publisher = details.publisher,
                    ),
                )
            }
        }
    }

    /** Starts installing the downloaded package via the download manager. */
    private fun installDownloaded(details: AppDetails) {
        viewModelScope.launch {
            transientInstall.value = InstallUiState.Installing
            publishInstallState()

            runCatching {
                downloadManager.install(
                    packageName = packageName,
                    appId = details.appId,
                    name = details.name,
                    iconUrl = details.iconUrl,
                    publisher = details.publisher,
                ) { intent -> _pendingConfirmation.value = intent }
            }
                .onSuccess { result ->
                    transientInstall.value = when (result) {
                        InstallResult.Success -> InstallUiState.Done
                        is InstallResult.Failure -> InstallUiState.Failed(result.message)
                    }
                    publishInstallState()
                    if (result is InstallResult.Success) refresh()
                }
                .onFailure { throwable ->
                    transientInstall.value = InstallUiState.Failed(throwable.toInstallMessage())
                    publishInstallState()
                }
        }
    }

    fun onConfirmationLaunched() {
        _pendingConfirmation.value = null
    }

    fun dismissInstallState() {
        transientInstall.value = null
        publishInstallState()
    }

    /** Merges the short-lived installation state and the download state into the UI. */
    private fun publishInstallState() {
        val transient = transientInstall.value
        val install = transient ?: when (val state = downloadState) {
            is DownloadState.Downloading -> InstallUiState.Downloading(state.fraction)
            DownloadState.Queued,
            DownloadState.Verifying,
            -> InstallUiState.Downloading(null)

            is DownloadState.Ready -> InstallUiState.Ready
            is DownloadState.Failed -> InstallUiState.Failed(state.message)
            else -> if (state.isBusy) InstallUiState.Downloading(null) else InstallUiState.Idle
        }
        _uiState.update { it.copy(install = install) }
    }

    private fun Throwable.toInstallMessage(): String = when (this) {
        is InstallException.DownloadFailed -> context.getString(R.string.app_details_error_download, code)
        is InstallException.ContainerHasNoApk -> context.getString(R.string.app_details_error_container)
        is InstallException.IntegrityMismatch -> context.getString(R.string.app_details_error_integrity)
        is InstallException.SessionFailed ->
            context.getString(R.string.app_details_error_session, message.orEmpty())

        else -> toUserMessage(context)
    }

    companion object {
        const val ARG_PACKAGE_NAME = "packageName"
    }
}
