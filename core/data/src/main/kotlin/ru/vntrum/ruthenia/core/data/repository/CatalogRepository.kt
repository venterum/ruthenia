/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.vntrum.ruthenia.core.data.db.CatalogAppDao
import com.vntrum.ruthenia.core.network.AppDetails
import com.vntrum.ruthenia.core.network.CatalogApp
import com.vntrum.ruthenia.core.network.RuStoreApi

/**
 * Catalog search and cache.
 *
 * Search results are stored in Room, so already found apps are available
 * offline — the network is only needed for new queries.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val api: RuStoreApi,
    private val catalogAppDao: CatalogAppDao,
) {

    fun observeCached(): Flow<List<CatalogApp>> =
        catalogAppDao.observeAll().map { entities -> entities.map { it.toModel() } }

    suspend fun appDetails(packageName: String): AppDetails = api.overallInfo(packageName)

    /** Other apps by the same developer — for the app details screen. */
    suspend fun developerApps(companyId: String): List<CatalogApp> =
        api.developerApps(companyId).apps

    /** Deletes cache older than [maxAgeMillis] so the database does not grow forever. */
    suspend fun evictCacheOlderThan(maxAgeMillis: Long) {
        catalogAppDao.deleteOlderThan(System.currentTimeMillis() - maxAgeMillis)
    }

    /**
     * A search page. [page] starts at zero; an empty page means the results are
     * exhausted.
     */
    suspend fun search(query: String, page: Int = 0): List<CatalogApp> {
        val results = api.search(query.trim(), pageNumber = page)
        val cachedAt = System.currentTimeMillis()
        catalogAppDao.upsertAll(results.map { it.toEntity(cachedAt) })
        return results
    }

    /** Cached catalog card — an offline fallback for the app details screen. */
    suspend fun cachedApp(packageName: String): CatalogApp? =
        catalogAppDao.get(packageName)?.toModel()
}
