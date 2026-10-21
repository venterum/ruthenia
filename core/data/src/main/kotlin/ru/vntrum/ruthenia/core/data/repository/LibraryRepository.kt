/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.vntrum.ruthenia.core.data.db.GithubAppDao
import com.vntrum.ruthenia.core.data.db.InstalledAppDao
import com.vntrum.ruthenia.core.data.db.InstalledAppEntity
import com.vntrum.ruthenia.core.data.model.AppSource
import com.vntrum.ruthenia.core.data.model.DeviceApp
import com.vntrum.ruthenia.core.data.model.InstalledApp
import com.vntrum.ruthenia.core.data.model.UpdateStatus
import com.vntrum.ruthenia.core.data.model.toLink
import com.vntrum.ruthenia.core.data.model.toModel
import com.vntrum.ruthenia.core.network.AppDetails
import com.vntrum.ruthenia.core.network.RuStoreApi
import com.vntrum.ruthenia.core.network.RuStoreException

/**
 * Library of apps installed through Ruthenia Store.
 *
 * Only those package names the user actually installed through the app are
 * tracked. The list of installed apps is read through `PackageManager` strictly
 * locally and is never transmitted anywhere.
 */
@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: RuStoreApi,
    private val installedAppDao: InstalledAppDao,
    private val githubAppDao: GithubAppDao,
    private val githubRepository: GithubRepository,
) {

    /** Limiter for concurrent catalog requests: it rate limits. */
    private val throttle = Semaphore(MAX_CONCURRENT_REQUESTS)

    fun observeLibrary(): Flow<List<InstalledApp>> =
        combine(
            installedAppDao.observeAll(),
            githubAppDao.observeAll(),
        ) { entities, links ->
            val byPackage = links.associateBy { it.packageName }
            entities.map { entity -> entity.toModel(byPackage[entity.packageName]?.toLink()) }
        }

    /** One-off library read — for the worker and manual checks. */
    suspend fun currentLibrary(): List<InstalledApp> {
        val links = githubAppDao.getAll().associateBy { it.packageName }
        return installedAppDao.getAll().map { entity ->
            entity.toModel(links[entity.packageName]?.toLink())
        }
    }

    fun observe(packageName: String): Flow<InstalledApp?> =
        combine(
            installedAppDao.observe(packageName),
            githubAppDao.observe(packageName),
        ) { entity, link -> entity?.toModel(link?.toLink()) }

    suspend fun get(packageName: String): InstalledApp? =
        installedAppDao.get(packageName)?.toModel(githubAppDao.get(packageName)?.toLink())

    /** Called after a successful install/update through Ruthenia Store. */
    suspend fun trackInstalled(
        packageName: String,
        appId: Long?,
        name: String?,
        iconUrl: String?,
        publisher: String?,
    ) {
        val existing = installedAppDao.get(packageName)
        val installed = readInstalledVersion(packageName)
        val github = githubAppDao.get(packageName)

        installedAppDao.upsert(
            InstalledAppEntity(
                packageName = packageName,
                appId = appId ?: existing?.appId,
                name = name ?: existing?.name,
                iconUrl = iconUrl ?: existing?.iconUrl,
                publisher = publisher ?: existing?.publisher,
                installedVersionCode = installed?.first ?: existing?.installedVersionCode,
                installedVersionName = installed?.second ?: existing?.installedVersionName,
                latestVersionCode = existing?.latestVersionCode,
                latestVersionName = existing?.latestVersionName,
                whatsNew = existing?.whatsNew,
                lastCheckedAt = existing?.lastCheckedAt,
                lastInstalledAt = System.currentTimeMillis(),
                source = if (github != null) AppSource.GITHUB else AppSource.RUSTORE,
                inCatalog = github == null,
            ),
        )
    }

    /** Marks an existing library row as GitHub-sourced without changing install time. */
    suspend fun markGithubSource(packageName: String) {
        val existing = installedAppDao.get(packageName) ?: return
        installedAppDao.upsert(existing.copy(source = AppSource.GITHUB, inCatalog = false))
    }

    /** Reconciles tracked apps with the actually installed ones. */
    suspend fun syncInstalledVersions() {
        installedAppDao.getAll().forEach { entity ->
            val installed = readInstalledVersion(entity.packageName)
            when {
                installed == null -> {
                    installedAppDao.delete(entity.packageName)
                    githubAppDao.delete(entity.packageName)
                }

                installed.first != entity.installedVersionCode ||
                    installed.second != entity.installedVersionName ->
                    installedAppDao.upsert(
                        entity.copy(
                            installedVersionCode = installed.first,
                            installedVersionName = installed.second,
                        ),
                    )
            }
        }
    }

    /**
     * Checks for updates. Without [packageName] it checks the whole library.
     *
     * Requests run with limited concurrency: the catalog rate limits frequent
     * calls, and one failing app must not break the check for the others.
     */
    suspend fun checkUpdates(packageName: String? = null): UpdateCheckSummary {
        val targets = if (packageName != null) {
            listOfNotNull(installedAppDao.get(packageName))
        } else {
            installedAppDao.getAll()
        }
        if (targets.isEmpty()) return UpdateCheckSummary(checked = 0, available = 0, failed = 0)

        val links = githubAppDao.getAll().associateBy { it.packageName }
        val githubTargets = targets.mapNotNull { entity ->
            links[entity.packageName]?.let { entity to it }
        }
        val ruStoreTargets = targets.filter { it.packageName !in links }

        var available = 0
        var failed = 0

        val results = coroutineScope {
            ruStoreTargets.map { entity ->
                async {
                    throttle.withPermit {
                        try {
                            Checked(
                                entity = entity,
                                details = api.overallInfo(entity.packageName),
                                installed = readInstalledVersion(entity.packageName),
                            )
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            Checked(entity = entity, details = null, installed = null)
                        }
                    }
                }
            }.awaitAll()
        }

        for (result in results) {
            val details = result.details
            if (details == null) {
                failed++
                continue
            }

            val installed = result.installed
            if (installed == null) {
                installedAppDao.delete(result.entity.packageName)
                githubAppDao.delete(result.entity.packageName)
                continue
            }

            val updated = result.entity.copy(
                appId = details.appId,
                name = details.name ?: result.entity.name,
                iconUrl = details.iconUrl ?: result.entity.iconUrl,
                publisher = details.publisher ?: result.entity.publisher,
                installedVersionCode = installed.first,
                installedVersionName = installed.second,
                latestVersionCode = details.versionCode,
                latestVersionName = details.versionName,
                whatsNew = details.whatsNew,
                lastCheckedAt = System.currentTimeMillis(),
                inCatalog = true,
            )
            installedAppDao.upsert(updated)

            if (updated.toModel().status == UpdateStatus.UPDATE_AVAILABLE) {
                available++
            }
        }

        // GitHub requests are sequential: the anonymous API has a much lower
        // rate limit than RuStore, and one failing repo must not affect others.
        for ((entity, link) in githubTargets) {
            val installed = readInstalledVersion(entity.packageName)
            if (installed == null) {
                installedAppDao.delete(entity.packageName)
                githubAppDao.delete(entity.packageName)
                continue
            }

            if (entity.installedVersionCode != installed.first ||
                entity.installedVersionName != installed.second
            ) {
                installedAppDao.upsert(
                    entity.copy(
                        installedVersionCode = installed.first,
                        installedVersionName = installed.second,
                    ),
                )
            }

            try {
                val result = githubRepository.checkForUpdate(
                    packageName = entity.packageName,
                    link = link.toLink(),
                    installedVersionName = installed.second,
                )
                if (result.status == UpdateStatus.UPDATE_AVAILABLE) available++
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failed++
            }
        }

        return UpdateCheckSummary(checked = targets.size, available = available, failed = failed)
    }

    /** The time of the last check — for throttling quiet auto-starts. */
    suspend fun lastCheckedAt(): Long? = maxOf(
        installedAppDao.getAll().mapNotNull { it.lastCheckedAt }.maxOrNull() ?: 0L,
        githubAppDao.getAll().mapNotNull { it.lastCheckedAt }.maxOrNull() ?: 0L,
    ).takeIf { it > 0L }

    /**
     * Imports already installed apps that are present in RuStore.
     *
     * We go through the apps visible to `PackageManager` and add to the library
     * only those found in the RuStore catalog: an update cannot be offered for
     * the rest, so they do not belong in the update list.
     *
     * The install source is stored separately — it shows where the app came
     * from on the device (Google Play, manual install, etc.), even though the
     * update still goes through RuStore.
     *
     * We do not verify the publisher here: an erroneous update from someone
     * else's build is filtered out by the signature check before install
     * (`ApkSignatureVerifier`).
     *
     * Progress is reported for every processed app so the screen can show the
     * progress and let the user interrupt the import.
     */
    suspend fun importInstalledApps(
        deviceApps: List<DeviceApp>,
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult {
        var scanned = 0
        var added = 0
        var updated = 0
        var notInCatalog = 0

        val existing = installedAppDao.getAll().associateBy { it.packageName }

        for (deviceApp in deviceApps) {
            ensureActive()

            scanned++
            onProgress(ImportProgress(scanned, deviceApps.size, deviceApp.label))

            val details = try {
                api.overallInfo(deviceApp.packageName)
            } catch (_: RuStoreException) {
                // The app is not in the RuStore catalog or the request failed.
                notInCatalog++
                continue
            }

            val previous = existing[deviceApp.packageName]
            if (previous == null) {
                added++
            } else {
                updated++
            }

            val github = githubAppDao.get(deviceApp.packageName)
            installedAppDao.upsert(
                InstalledAppEntity(
                    packageName = deviceApp.packageName,
                    appId = details.appId,
                    name = details.name ?: previous?.name ?: deviceApp.label,
                    iconUrl = details.iconUrl ?: previous?.iconUrl,
                    publisher = details.publisher ?: previous?.publisher,
                    installedVersionCode = deviceApp.versionCode,
                    installedVersionName = deviceApp.versionName,
                    latestVersionCode = details.versionCode,
                    latestVersionName = details.versionName,
                    whatsNew = details.whatsNew,
                    lastCheckedAt = System.currentTimeMillis(),
                    lastInstalledAt = previous?.lastInstalledAt,
                    source = if (github != null) AppSource.GITHUB else deviceApp.source,
                    inCatalog = github == null,
                ),
            )
        }

        return ImportResult(
            scanned = scanned,
            added = added,
            updated = updated,
            notInCatalog = notInCatalog,
        )
    }

    private suspend fun ensureActive() {
        currentCoroutineContext().ensureActive()
    }

    @Suppress("DEPRECATION")
    private fun readInstalledVersion(packageName: String): Pair<Long, String?>? = try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        versionCode to info.versionName
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Result of a single request: details, device version, or failure. */
    private data class Checked(
        val entity: InstalledAppEntity,
        val details: AppDetails?,
        val installed: Pair<Long, String?>?,
    )

    private companion object {
        const val MAX_CONCURRENT_REQUESTS = 4
    }
}

/** Import progress: how many apps have been checked out of the total. */
data class ImportProgress(
    val scanned: Int,
    val total: Int,
    val currentPackageLabel: String?,
) {
    val fraction: Float
        get() = if (total > 0) (scanned.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Import result: how many apps from RuStore were added and how many installed
 * apps were not found in the catalog.
 */
data class ImportResult(
    val scanned: Int,
    val added: Int,
    val updated: Int,
    val notInCatalog: Int,
) {
    val totalTracked: Int
        get() = added + updated
}

/**
 * Update check result: how many apps were checked, how many had an update, and
 * how many requests failed.
 */
data class UpdateCheckSummary(
    val checked: Int,
    val available: Int,
    val failed: Int,
) {
    /** No app could be checked — the failure looks like a network one. */
    val isTotalFailure: Boolean
        get() = checked > 0 && failed == checked
}
