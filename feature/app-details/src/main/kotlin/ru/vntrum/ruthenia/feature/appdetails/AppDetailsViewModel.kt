/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.appdetails

import android.content.Context
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
import com.vntrum.ruthenia.core.data.model.GithubLink
import com.vntrum.ruthenia.core.data.model.InstalledApp
import com.vntrum.ruthenia.core.data.model.UpdateStatus
import com.vntrum.ruthenia.core.data.model.isUpdateIgnored
import com.vntrum.ruthenia.core.data.model.toUserMessage
import com.vntrum.ruthenia.core.data.repository.CatalogRepository
import com.vntrum.ruthenia.core.data.repository.GithubRepository
import com.vntrum.ruthenia.core.data.repository.LibraryRepository
import com.vntrum.ruthenia.core.data.settings.SettingsRepository
import com.vntrum.ruthenia.core.installer.InstallException
import com.vntrum.ruthenia.core.installer.InstallResult
import com.vntrum.ruthenia.core.network.AppDetails
import com.vntrum.ruthenia.core.network.CatalogApp
import com.vntrum.ruthenia.core.github.GithubAssetSelector
import com.vntrum.ruthenia.core.updates.ApkExportManager
import com.vntrum.ruthenia.core.updates.DownloadManager
import com.vntrum.ruthenia.core.updates.DownloadRequest
import com.vntrum.ruthenia.core.updates.DownloadSource
import com.vntrum.ruthenia.core.updates.DownloadState
import com.vntrum.ruthenia.core.updates.DownloadTask
import com.vntrum.ruthenia.core.updates.InstallState
import com.vntrum.ruthenia.core.updates.isBusy
import com.vntrum.ruthenia.core.updates.needsRedownload
import com.vntrum.ruthenia.core.updates.text
import com.vntrum.ruthenia.core.updates.toTaskFailure

/** State of the installation process on the detail screen. */
sealed interface InstallUiState {
    data object Idle : InstallUiState

    data class Downloading(val fraction: Float?) : InstallUiState

    /** Files are downloaded, awaiting a tap on "Install". */
    data object Ready : InstallUiState

    data object Paused : InstallUiState

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
    /** Non-null when this app is tracked from a GitHub release. */
    val githubRepo: String? = null,
    val githubAssetName: String? = null,
    val githubAssetUrl: String? = null,
    val githubAssetSize: Long? = null,
    val githubAssetSha256: String? = null,
    val githubIncludePrereleases: Boolean = false,
    /** RuStore APK export is not available for GitHub sources. */
    val canExport: Boolean = true,
) {
    val canInstall: Boolean
        get() = details != null && details.isAggregated.not() &&
            (githubRepo == null || githubAssetUrl != null) &&
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
    private val githubRepository: GithubRepository,
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
    private var installState: InstallState = InstallState.Idle
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
                downloadState = downloadTask?.download ?: DownloadState.Idle
                installState = downloadTask?.install ?: InstallState.Idle
                publishInstallState()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isOffline = false) }
            val github = runCatching { githubRepository.getLink(packageName) }.getOrNull()
            if (github != null) {
                loadGithub(github)
                return@launch
            }

            runCatching { catalogRepository.appDetails(packageName) }
                .onSuccess { details ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            details = details,
                            error = null,
                            isOffline = false,
                            githubRepo = null,
                            canExport = true,
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

    /** Loads the GitHub repository/release and maps it onto the shared details UI. */
    private suspend fun loadGithub(link: GithubLink) {
        val repo = try {
            githubRepository.repo(link.owner, link.repo)
        } catch (_: Throwable) {
            null
        }
        val releases = try {
            githubRepository.releases(link.owner, link.repo)
        } catch (_: Throwable) {
            emptyList()
        }
        val release = releases.firstOrNull {
            link.includePrereleases || !it.prerelease
        }
        val asset = release?.let { GithubAssetSelector.select(it, link.assetName) }

        if (release != null && asset != null) {
            val installed = libraryRepository.get(packageName)
            val details = AppDetails(
                appId = 0L,
                packageName = packageName,
                name = installed?.name ?: link.displayName ?: repo?.name ?: link.repo,
                publisher = link.owner,
                versionName = release.versionName,
                versionCode = null,
                whatsNew = release.body,
                updatedAt = release.publishedAt?.let {
                    java.time.Instant.ofEpochMilli(it).toString()
                },
                // The app's real icon, when it is already known locally; the
                // repository avatar is only the last resort.
                iconUrl = installed?.iconUrl ?: downloadTask?.iconUrl ?: link.avatarUrl,
                minSdkVersion = null,
                targetSdkVersion = null,
                fileSize = asset.size,
                isAggregated = false,
                shortDescription = repo?.description,
                fullDescription = repo?.description,
                website = "https://github.com/${link.fullName}",
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    details = details,
                    error = null,
                    isOffline = false,
                    githubRepo = link.fullName,
                    githubAssetName = asset.name,
                    githubAssetUrl = asset.downloadUrl,
                    githubAssetSize = asset.size,
                    githubAssetSha256 = asset.sha256,
                    githubIncludePrereleases = link.includePrereleases,
                    canExport = false,
                )
            }
            return
        }

        val fallback = githubOfflineDetails(link)
        _uiState.update {
            it.copy(
                isLoading = false,
                details = fallback,
                error = null,
                isOffline = true,
                githubRepo = link.fullName,
                githubAssetName = link.assetName,
                githubAssetUrl = link.latestAssetUrl,
                githubAssetSize = link.latestAssetSize,
                githubAssetSha256 = link.latestAssetSha256,
                githubIncludePrereleases = link.includePrereleases,
                canExport = false,
            )
        }
    }

    private suspend fun githubOfflineDetails(link: GithubLink): AppDetails {
        val installed = libraryRepository.get(packageName)
        return AppDetails(
            appId = 0L,
            packageName = packageName,
            name = installed?.name ?: link.displayName ?: link.repo,
            publisher = link.owner,
            versionName = if (installed != null) {
                displayVersionName(installed, link)
            } else {
                link.latestVersionName
            },
            versionCode = null,
            whatsNew = null,
            updatedAt = link.latestPublishedAt?.let {
                java.time.Instant.ofEpochMilli(it).toString()
            },
            iconUrl = installed?.iconUrl ?: downloadTask?.iconUrl ?: link.avatarUrl,
            minSdkVersion = null,
            targetSdkVersion = null,
            fileSize = link.latestAssetSize,
            isAggregated = false,
            website = "https://github.com/${link.fullName}",
        )
    }

    private fun displayVersionName(installed: InstalledApp, link: GithubLink): String? =
        installed.installedVersionName ?: installed.installedVersionCode?.toString()
            ?: link.latestVersionName

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
                settingsRepository.ignoreUpdate(
                    packageName,
                    installed.latestVersionCode ?: installed.github?.latestReleaseId,
                )
            }
        }
    }

    /** Saves the APK to the folder from settings; without a folder, prompts to choose one. */
    fun exportApk() {
        val state = _uiState.value
        val details = state.details ?: return
        if (details.isAggregated || !state.canExport || state.githubRepo != null) return

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

    /** Pauses an in-flight download — partial files are kept for resume. */
    fun cancelDownload() {
        viewModelScope.launch {
            downloadManager.pause(packageName)
        }
    }

    fun resumeDownload() {
        viewModelScope.launch {
            downloadManager.resume(packageName)
        }
    }

    /**
     * A single button action: if the files are already downloaded — install,
     * otherwise queue the download in the shared manager.
     */
    fun install() {
        val state = _uiState.value
        val details = state.details ?: return
        if (!state.canInstall) return

        if (downloadState is DownloadState.Ready) {
            installDownloaded(details)
        } else {
            viewModelScope.launch {
                val githubUrl = state.githubAssetUrl
                if (downloadTask?.failure?.needsRedownload == true) {
                    downloadTask?.let { downloadManager.redownload(it) }
                } else if (githubUrl != null) {
                    downloadManager.request(
                        DownloadRequest(
                            packageName = packageName,
                            name = details.name,
                            iconUrl = details.iconUrl,
                            publisher = details.publisher,
                            versionName = details.versionName,
                            source = DownloadSource.GithubAsset(
                                url = githubUrl,
                                size = state.githubAssetSize,
                                sha256 = state.githubAssetSha256,
                            ),
                        ),
                    )
                } else {
                    downloadManager.request(
                        DownloadRequest(
                            packageName = packageName,
                            appId = details.appId,
                            versionCode = details.versionCode,
                            name = details.name,
                            iconUrl = details.iconUrl,
                            publisher = details.publisher,
                            versionName = details.versionName,
                        ),
                    )
                }
            }
        }
    }

    /** Starts installing the downloaded package via the download manager. */
    private fun installDownloaded(details: AppDetails) {
        viewModelScope.launch {
            transientInstall.value = InstallUiState.Installing
            publishInstallState()

            runCatching {
                downloadManager.install(packageName)
            }
                .onSuccess { result ->
                    transientInstall.value = when (result) {
                        InstallResult.Success -> InstallUiState.Done
                        is InstallResult.Failure ->
                            InstallUiState.Failed(result.toTaskFailure().text(context).message)
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

    fun dismissInstallState() {
        transientInstall.value = null
        publishInstallState()
    }

    /** Merges the short-lived installation state and the download state into the UI. */
    private fun publishInstallState() {
        val transient = transientInstall.value
        val currentInstall = installState
        val currentDownload = downloadState
        val install = transient ?: when {
            currentInstall is InstallState.Installing ||
                currentInstall is InstallState.AwaitingUser -> InstallUiState.Installing

            currentInstall is InstallState.Failed ->
                InstallUiState.Failed(currentInstall.failure.text(context).message)

            currentDownload is DownloadState.Downloading ->
                InstallUiState.Downloading(currentDownload.fraction)

            currentDownload is DownloadState.Paused -> InstallUiState.Paused

            currentDownload is DownloadState.Queued ||
                currentDownload is DownloadState.Verifying ->
                InstallUiState.Downloading(null)

            currentDownload is DownloadState.Ready -> InstallUiState.Ready
            currentDownload is DownloadState.Failed ->
                InstallUiState.Failed(currentDownload.failure.text(context).message)

            else -> InstallUiState.Idle
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
