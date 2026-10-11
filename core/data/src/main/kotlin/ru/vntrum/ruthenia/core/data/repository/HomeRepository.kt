/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.vntrum.ruthenia.core.data.db.CatalogAppDao
import com.vntrum.ruthenia.core.data.db.HomeEntryEntity
import com.vntrum.ruthenia.core.data.db.HomeFeedDao
import com.vntrum.ruthenia.core.network.CatalogApp
import com.vntrum.ruthenia.core.network.RuStoreApi
import com.vntrum.ruthenia.core.network.RuStoreException

/**
 * Home feed: sections with apps from the RuStore catalog.
 *
 * The content is curated and NOT personal: "Recommended" is a fixed list of
 * package names, sections are predefined search queries. Requests are limited
 * by a semaphore to avoid hitting the rate limit, and cached in Room.
 */
@Singleton
class HomeRepository @Inject constructor(
    private val api: RuStoreApi,
    private val homeFeedDao: HomeFeedDao,
    private val catalogAppDao: CatalogAppDao,
) {

    private val throttle = Semaphore(MAX_CONCURRENT_REQUESTS)

    /**
     * Returns the feed: fresh from cache or rebuilt from the network.
     *
     * If the network did not respond and there is nothing to build, returns an
     * empty list; the UI shows an error state with retry.
     */
    suspend fun homeFeed(forceRefresh: Boolean = false): List<HomeSection> {
        if (!forceRefresh) {
            val cached = cachedSections(games = false)
            if (cached.isNotEmpty()) return cached
        }

        val sections = buildFeed()
        if (sections.isNotEmpty()) {
            persist(sections, games = false)
        }
        return sections
    }

    /**
     * The curated games feed: a "Popular games" section and genre rails.
     *
     * It shares the table with the home feed but lives under the `game_` id
     * prefix, so refreshing one feed never wipes the other.
     */
    suspend fun gamesFeed(forceRefresh: Boolean = false): List<HomeSection> {
        if (!forceRefresh) {
            val cached = cachedSections(games = true)
            if (cached.isNotEmpty()) return cached
        }

        val sections = buildGamesFeed()
        if (sections.isNotEmpty()) {
            persist(sections, games = true)
        }
        return sections
    }

    /** A feed section by its id — for the collection screen (app and game feeds). */
    suspend fun section(sectionId: String): HomeSection? =
        if (HomeConfig.isGameSection(sectionId)) {
            gamesFeed().firstOrNull { it.id == sectionId }
        } else {
            homeFeed().firstOrNull { it.id == sectionId }
        }

    /** Catalog categories: rail sections without the special "Recommended" selection. */
    suspend fun categories(forceRefresh: Boolean = false): List<HomeSection> =
        homeFeed(forceRefresh).filter { !it.featured }

    /** Cached sections of one feed: app (`games = false`) or games. */
    private suspend fun cachedSections(games: Boolean): List<HomeSection> =
        homeFeedDao.entriesSince(System.currentTimeMillis() - CACHE_TTL_MS)
            .filter { HomeConfig.isGameSection(it.sectionId) == games }
            .toSections()

    private suspend fun buildGamesFeed(): List<HomeSection> = coroutineScope {
        val popular = async {
            loadCategory(
                HomeConfig.HomeCategory(
                    id = HomeConfig.GAMES_POPULAR_SECTION_ID,
                    title = HomeConfig.GAMES_POPULAR_SECTION_TITLE,
                    query = HomeConfig.GAMES_POPULAR_QUERY,
                ),
            )
        }
        val genres = HomeConfig.gameGenres.map { genre -> async { loadCategory(genre) } }

        val readyPopular = popular.await()
        val readyGenres = genres.awaitAll().filterNotNull()

        buildList {
            readyPopular?.let(::add)
            addAll(readyGenres)
        }
    }

    private suspend fun buildFeed(): List<HomeSection> = coroutineScope {
        val recommended = async { loadCurated(HomeConfig.recommended) }
        val categories = HomeConfig.categories.map { category -> async { loadCategory(category) } }

        val readyCategories = categories.awaitAll().filterNotNull()
        val readyRecommended = recommended.await()

        buildList {
            if (readyRecommended.isNotEmpty()) {
                add(
                    HomeSection(
                        id = HomeConfig.RECOMMENDED_SECTION_ID,
                        title = HomeConfig.RECOMMENDED_SECTION_TITLE,
                        apps = readyRecommended,
                        featured = true,
                    ),
                )
            }
            addAll(readyCategories)
        }
    }

    private suspend fun loadCategory(category: HomeConfig.HomeCategory): HomeSection? =
        throttled {
            runCatching { api.search(category.query, pageSize = HomeConfig.SECTION_PAGE_SIZE) }
                .getOrDefault(emptyList())
        }.takeIf { it.isNotEmpty() }
            ?.let { HomeSection(id = category.id, title = category.title, apps = it) }

    /** Resolves the curated package name list with an exact catalog search. */
    private suspend fun loadCurated(items: List<HomeConfig.RecommendedApp>): List<CatalogApp> =
        coroutineScope {
            items
                .map { item ->
                async {
                    throttled {
                        runCatching {
                            api.search(item.query, pageSize = HomeConfig.RECOMMENDED_SEARCH_SIZE)
                                .firstOrNull { it.packageName == item.packageName }
                        }.getOrNull()
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    private suspend fun <T> throttled(block: suspend () -> T): T =
        throttle.withPermit { withRetry(block) }

    /** One retry on 429 with a linear delay — the catalog likes to rate limit. */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (error: RuStoreException.Http) {
                if (error.code == HTTP_TOO_MANY_REQUESTS && attempt < MAX_RETRIES) {
                    delay(RETRY_BASE_DELAY_MS * (attempt + 1))
                    attempt++
                } else {
                    throw error
                }
            }
        }
    }

    private suspend fun persist(sections: List<HomeSection>, games: Boolean) {
        val now = System.currentTimeMillis()
        val entries = sections.flatMapIndexed { sectionPosition, section ->
            section.apps.mapIndexed { itemPosition, app ->
                app.toEntry(section, sectionPosition, itemPosition, now)
            }
        }
        if (games) {
            homeFeedDao.clearGameSections()
        } else {
            homeFeedDao.clearAppSections()
        }
        homeFeedDao.upsertAll(entries)
        catalogAppDao.upsertAll(sections.flatMap { it.apps }.map { it.toEntity(now) })
    }

    private fun CatalogApp.toEntry(
        section: HomeSection,
        sectionPosition: Int,
        itemPosition: Int,
        cachedAt: Long,
    ): HomeEntryEntity = HomeEntryEntity(
        sectionId = section.id,
        sectionTitle = section.title,
        sectionPosition = sectionPosition,
        itemPosition = itemPosition,
        packageName = packageName,
        appId = appId,
        name = name,
        shortDescription = shortDescription,
        iconUrl = iconUrl,
        versionCode = versionCode,
        minSdkVersion = minSdkVersion,
        averageUserRating = averageUserRating,
        totalRatings = totalRatings,
        downloadsText = downloadsText,
        cachedAt = cachedAt,
    )

    private fun List<HomeEntryEntity>.toSections(): List<HomeSection> =
        groupBy { it.sectionId }
            .values
            .sortedBy { entries -> entries.first().sectionPosition }
            .map { entries ->
                val ordered = entries.sortedBy { it.itemPosition }
                HomeSection(
                    id = ordered.first().sectionId,
                    title = ordered.first().sectionTitle,
                    apps = ordered.map { it.toCatalogApp() },
                    featured = ordered.first().sectionId == HomeConfig.RECOMMENDED_SECTION_ID,
                )
            }

    private fun HomeEntryEntity.toCatalogApp(): CatalogApp = CatalogApp(
        appId = appId,
        packageName = packageName,
        name = name,
        shortDescription = shortDescription,
        iconUrl = iconUrl,
        versionCode = versionCode,
        minSdkVersion = minSdkVersion,
        averageUserRating = averageUserRating,
        totalRatings = totalRatings,
        downloadsText = downloadsText,
    )

    private companion object {
        const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        const val MAX_CONCURRENT_REQUESTS = 4
        const val MAX_RETRIES = 1
        const val RETRY_BASE_DELAY_MS = 800L
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
