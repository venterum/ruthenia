/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.library

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.vntrum.ruthenia.core.data.device.DeviceAppReader
import com.vntrum.ruthenia.core.data.model.AppSource
import com.vntrum.ruthenia.core.data.model.InstalledApp
import com.vntrum.ruthenia.core.data.model.UpdateStatus
import com.vntrum.ruthenia.core.data.model.isUpdateIgnored
import com.vntrum.ruthenia.core.data.model.toUserMessage
import com.vntrum.ruthenia.core.data.repository.ImportProgress
import com.vntrum.ruthenia.core.data.repository.ImportResult
import com.vntrum.ruthenia.core.data.repository.LibraryRepository
import com.vntrum.ruthenia.core.data.settings.AppSettings
import com.vntrum.ruthenia.core.data.settings.SettingsRepository
import com.vntrum.ruthenia.core.installer.InstallResult
import com.vntrum.ruthenia.core.updates.DownloadManager
import com.vntrum.ruthenia.core.updates.DownloadRequest
import com.vntrum.ruthenia.core.updates.DownloadState
import com.vntrum.ruthenia.core.updates.DownloadTask
import com.vntrum.ruthenia.core.updates.isActionable

/** Updates section row: an app and the state of its background download. */
data class UpdateRow(
    val app: InstalledApp,
    val download: DownloadState,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val deviceAppReader: DeviceAppReader,
    private val downloadManager: DownloadManager,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val library: StateFlow<List<InstalledApp>> = combine(
        libraryRepository.observeLibrary(),
        settingsRepository.settings,
    ) { apps, settings ->
        apps.filter { it.isVisibleInLibrary(settings) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = emptyList(),
    )

    /**
     * Apps with an available update for the "Updates" section inside the
     * library. The source filter is the same as for the library list: Google
     * Play apps hidden in settings / manual apps don't appear here.
     */
    val updateRows: StateFlow<List<UpdateRow>> = combine(
        libraryRepository.observeLibrary(),
        settingsRepository.settings,
        downloadManager.downloads,
    ) { apps, settings, tasks ->
        val byPackage = tasks.associateBy { it.packageName }
        apps
            .filter {
                it.status == UpdateStatus.UPDATE_AVAILABLE &&
                    it.isVisibleInLibrary(settings) &&
                    !it.isUpdateIgnored(settings.ignoredUpdates)
            }
            .map { app -> UpdateRow(app = app, download = byPackage[app.packageName]?.state.forApp(app)) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = emptyList(),
    )

    /** Hidden updates: to hint to the user where they went. */
    val hiddenUpdateCount: StateFlow<Int> = combine(
        libraryRepository.observeLibrary(),
        settingsRepository.settings,
    ) { apps, settings ->
        apps.count {
            it.status == UpdateStatus.UPDATE_AVAILABLE &&
                it.isVisibleInLibrary(settings) &&
                it.isUpdateIgnored(settings.ignoredUpdates)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = 0,
    )

    /**
     * Download manager rows: active, ready to install, and failed. Completed and
     * canceled records don't linger in the list.
     */
    val downloadTasks: StateFlow<List<DownloadTask>> = downloadManager.downloads
        .map { tasks -> tasks.filter { it.isActive || it.isReady || it.isFailed } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    /** Package names of updates selected for a batch action. */
    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Progress of importing installed apps; `null` when no import is running. */
    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    private val _installingPackage = MutableStateFlow<String?>(null)
    val installingPackage: StateFlow<String?> = _installingPackage.asStateFlow()

    /** Intent of the system install dialog — launched from the Activity. */
    private val _pendingConfirmation = MutableStateFlow<Intent?>(null)
    val pendingConfirmation: StateFlow<Intent?> = _pendingConfirmation.asStateFlow()

    private var importJob: Job? = null
    private var autoCheckDone = false

    /**
     * Whether the library's first emission from Room has happened. Before that,
     * an empty list means "still loading", not "nothing there"; the hub shows a
     * skeleton, not the empty state.
     */
    private val _libraryLoaded = MutableStateFlow(false)
    val libraryLoaded: StateFlow<Boolean> = _libraryLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.observeLibrary().collect {
                _libraryLoaded.value = true
            }
        }
    }

    val isImporting: Boolean
        get() = importJob?.isActive == true

    /**
     * Manual update check across the whole library.
     *
     * @param silent don't show the result message (for the quiet auto-run).
     */
    fun checkUpdates(silent: Boolean = false) {
        if (_isChecking.value) return
        viewModelScope.launch {
            _isChecking.value = true
            runCatching { libraryRepository.checkUpdates() }
                .onSuccess { summary ->
                    if (!silent) {
                        _message.value = when {
                            summary.available == 0 && summary.failed > 0 ->
                                context.getString(R.string.message_updates_check_failed)

                            summary.available == 0 ->
                                context.getString(R.string.message_updates_none)

                            summary.available == 1 ->
                                context.getString(R.string.message_updates_one)

                            else -> context.getString(
                                R.string.message_updates_many,
                                summary.available,
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    if (!silent) _message.value = throwable.toUserMessage(context)
                }
            _isChecking.value = false
        }
    }

    /**
     * One quiet run per session so the updates section isn't empty. If the
     * catalog was checked less than [AUTO_CHECK_MIN_INTERVAL_MS] ago, the run is
     * skipped — otherwise every app launch would hammer the catalog with requests.
     */
    fun autoCheckOnce() {
        if (autoCheckDone) return
        autoCheckDone = true
        viewModelScope.launch {
            val lastCheck = libraryRepository.lastCheckedAt()
            val fresh = lastCheck != null &&
                System.currentTimeMillis() - lastCheck < AUTO_CHECK_MIN_INTERVAL_MS
            if (!fresh) checkUpdates(silent = true)
        }
    }

    /** Hides an update: don't offer it until the next catalog version. */
    fun hideUpdate(app: InstalledApp) {
        viewModelScope.launch {
            settingsRepository.ignoreUpdate(app.packageName, app.latestVersionCode)
            _selectedPackages.update { it - app.packageName }
        }
    }

    /** Returns a hidden update to the list. */
    fun restoreUpdate(packageName: String) {
        viewModelScope.launch {
            settingsRepository.restoreUpdateVisibility(packageName)
        }
    }

    /** Queues a single update for background download. */
    fun update(app: InstalledApp) {
        viewModelScope.launch {
            downloadManager.request(app.toDownloadRequest())
        }
    }

    /** Queues all updates that haven't been downloaded yet. */
    fun updateAll() {
        val targets = updateRows.value.filter { it.download.isActionable }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            targets.forEach { row -> downloadManager.request(row.app.toDownloadRequest()) }
            _message.value = context.getString(R.string.message_updates_downloading)
        }
    }

    /** Toggles a single update in selection mode. */
    fun toggleSelected(packageName: String) {
        _selectedPackages.update { selected ->
            if (packageName in selected) selected - packageName else selected + packageName
        }
    }

    /** Selects all updates that can currently be installed. */
    fun selectAllActionable() {
        _selectedPackages.value = updateRows.value
            .filter { it.download.isActionable }
            .mapTo(mutableSetOf()) { it.app.packageName }
    }

    fun clearSelection() {
        _selectedPackages.value = emptySet()
    }

    /** Queues only the selected updates and exits selection mode. */
    fun updateSelected() {
        val selected = _selectedPackages.value
        val targets = updateRows.value.filter {
            it.app.packageName in selected && it.download.isActionable
        }
        if (targets.isEmpty()) {
            _selectedPackages.value = emptySet()
            return
        }
        viewModelScope.launch {
            targets.forEach { row -> downloadManager.request(row.app.toDownloadRequest()) }
            _selectedPackages.value = emptySet()
            _message.value = context.getString(R.string.message_updates_downloading)
        }
    }

    /** Retries a failed download from the manager. */
    fun retry(task: DownloadTask) {
        viewModelScope.launch {
            downloadManager.request(task.toDownloadRequest())
        }
    }

    fun cancelDownload(app: InstalledApp) {
        downloadManager.cancel(app.packageName)
    }

    fun cancelDownload(task: DownloadTask) {
        downloadManager.cancel(task.packageName)
    }

    /** Removes a ready/failed record from the download manager. */
    fun forget(task: DownloadTask) {
        downloadManager.forget(task.packageName)
    }

    fun install(app: InstalledApp) = install(
        packageName = app.packageName,
        appId = app.appId,
        name = app.name,
        iconUrl = app.iconUrl,
        publisher = app.publisher,
    )

    fun install(task: DownloadTask) = install(
        packageName = task.packageName,
        appId = task.appId,
        name = task.name,
        iconUrl = task.iconUrl,
        publisher = task.publisher,
    )

    /**
     * Starts installing an already-downloaded update. The system confirmation
     * dialog is opened from the Activity; the worker doesn't bypass it.
     */
    private fun install(
        packageName: String,
        appId: Long?,
        name: String?,
        iconUrl: String?,
        publisher: String?,
    ) {
        if (_installingPackage.value != null) return

        viewModelScope.launch {
            _installingPackage.value = packageName
            runCatching {
                downloadManager.install(
                    packageName = packageName,
                    appId = appId,
                    name = name,
                    iconUrl = iconUrl,
                    publisher = publisher,
                ) { intent -> _pendingConfirmation.value = intent }
            }
                .onSuccess { result ->
                    when (result) {
                        InstallResult.Success -> _message.value =
                            context.getString(R.string.message_installed, name ?: packageName)

                        is InstallResult.Failure -> _message.value = result.message
                    }
                }
                .onFailure { throwable -> _message.value = throwable.toUserMessage(context) }

            _installingPackage.value = null
        }
    }

    fun onConfirmationLaunched() {
        _pendingConfirmation.value = null
    }

    /**
     * Imports already-installed apps that are in RuStore.
     *
     * The list of installed apps is read locally; only the package names of the
     * apps the user chose to check go over the network. The import can be interrupted.
     */
    fun importInstalledApps() {
        if (importJob?.isActive == true) return

        importJob = viewModelScope.launch {
            _importProgress.value = ImportProgress(scanned = 0, total = 0, currentPackageLabel = null)

            runCatching {
                val deviceApps = deviceAppReader.installedApps()
                libraryRepository.importInstalledApps(deviceApps) { progress ->
                    _importProgress.value = progress
                }
            }
                .onSuccess { result ->
                    _message.value = buildImportMessage(result)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) {
                        _message.value = context.getString(R.string.message_import_cancelled)
                    } else {
                        _message.value = throwable.toUserMessage(context)
                    }
                }

            _importProgress.value = null
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _importProgress.value = null
    }

    private fun buildImportMessage(result: ImportResult): String = buildString {
        if (result.totalTracked == 0) {
            append(context.getString(R.string.message_import_none))
        } else {
            append(context.getString(R.string.message_import_result, result.added, result.updated))
        }
        if (result.notInCatalog > 0) {
            append(" ")
            append(context.getString(R.string.message_import_checked, result.scanned))
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        /** Minimum interval between quiet catalog checks. */
        const val AUTO_CHECK_MIN_INTERVAL_MS = 60 * 60 * 1000L
    }
}

/** An app's visibility in the library is determined by the source settings. */
private fun InstalledApp.isVisibleInLibrary(settings: AppSettings): Boolean = when (source) {
    AppSource.GOOGLE_PLAY -> settings.showGooglePlayApps
    AppSource.SIDELOAD -> settings.showSideloadedApps
    else -> true
}

/** Update request: the target is always the latest version from the catalog. */
private fun InstalledApp.toDownloadRequest(): DownloadRequest = DownloadRequest(
    packageName = packageName,
    appId = appId,
    versionCode = latestVersionCode,
    name = name,
    iconUrl = iconUrl,
    publisher = publisher,
)

/** Request to retry a failed download — with the same metadata. */
private fun DownloadTask.toDownloadRequest(): DownloadRequest = DownloadRequest(
    packageName = packageName,
    appId = appId,
    versionCode = versionCode,
    name = name,
    iconUrl = iconUrl,
    publisher = publisher,
)

/** Readiness to install becomes stale if the catalog already has a different version. */
internal fun DownloadState?.forApp(app: InstalledApp): DownloadState = when {
    this == null -> DownloadState.Idle
    this is DownloadState.Ready &&
        versionCode != null &&
        app.latestVersionCode != null &&
        versionCode != app.latestVersionCode -> DownloadState.Idle

    else -> this
}
