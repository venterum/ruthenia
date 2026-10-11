/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.repository

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import com.vntrum.ruthenia.core.data.db.CatalogAppDao
import com.vntrum.ruthenia.core.data.db.CatalogAppEntity
import com.vntrum.ruthenia.core.data.db.HomeEntryEntity
import com.vntrum.ruthenia.core.data.db.HomeFeedDao
import com.vntrum.ruthenia.core.data.db.UnresolvedAppDao
import com.vntrum.ruthenia.core.data.db.UnresolvedAppEntity
import com.vntrum.ruthenia.core.network.AppDetails
import com.vntrum.ruthenia.core.network.CatalogApp
import com.vntrum.ruthenia.core.network.RuStoreApi
import com.vntrum.ruthenia.core.network.RuStoreException

/**
 * How far an app-feed build is: [done] packages out of [total] curated ones
 * (resolved or already known to be unresolved). The loading UI shows [percent].
 */
data class FeedBuildProgress(val done: Int, val total: Int) {
    val percent: Int get() = if (total <= 0) 100 else (done * 100 / total).coerceIn(0, 100)
}

/**
 * Home feed: sections with apps from the RuStore catalog.
 *
 * The content is curated and NOT personal: "Recommended" and the categories are
 * fixed, hand-picked package lists resolved by exact package name. Requests are
 * limited by a semaphore to avoid hitting the rate limit, and every resolved app
 * is remembered in Room, so a rebuild only looks up what is still missing.
 */
@Singleton
class HomeRepository @Inject constructor(
    private val api: RuStoreApi,
    private val homeFeedDao: HomeFeedDao,
    private val catalogAppDao: CatalogAppDao,
    private val unresolvedAppDao: UnresolvedAppDao,
) {

    private val throttle = Semaphore(MAX_CONCURRENT_REQUESTS)

    /** Serializes builds so the Apps and Search tabs don't resolve in parallel. */
    private val buildMutex = Mutex()

    /** Progress of the current app-feed build; the loading UI shows the percent. */
    private val _buildProgress = MutableStateFlow(
        FeedBuildProgress(done = 0, total = HomeConfig.curatedPackages.size),
    )
    val buildProgress: StateFlow<FeedBuildProgress> = _buildProgress.asStateFlow()

    /** Packages handled during the current build: resolved or skipped as failed. */
    private val builtPackages = ConcurrentHashMap.newKeySet<String>()

    /** Negative cache: unresolved packages with the time of the last attempt. */
    private val failedPackages = ConcurrentHashMap<String, Long>()

    @Volatile
    private var failuresLoaded = false
    private val failuresMutex = Mutex()

    /**
     * Returns the feed: fresh from cache or rebuilt from the network.
     *
     * A cached feed is served as soon as there is nothing new to resolve:
     * a fully resolved feed, or one whose missing packages already failed
     * recently (the negative cache). Resolution is cached per app, so a
     * rebuild only searches the packages that are still missing.
     */
    suspend fun homeFeed(forceRefresh: Boolean = false): List<HomeSection> {
        if (!forceRefresh) {
            val cached = cachedSections(games = false)
            if (cached.isNotEmpty()) return cached
        }
        return buildMutex.withLock {
            // Another caller may have finished the build while we waited.
            if (!forceRefresh) {
                val cached = cachedSections(games = false)
                if (cached.isNotEmpty()) return@withLock cached
            }
            val sections = buildAppFeed(retryFailed = forceRefresh)
            if (sections.isNotEmpty()) {
                persist(sections, games = false)
                return@withLock sections
            }
            // Nothing came back from the network: show whatever we have, even if
            // the previous attempt was incomplete.
            cachedSections(games = false, requireComplete = false)
        }
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
        return buildMutex.withLock {
            if (!forceRefresh) {
                val cached = cachedSections(games = true)
                if (cached.isNotEmpty()) return@withLock cached
            }
            val sections = buildGamesFeed()
            if (sections.isNotEmpty()) persist(sections, games = true)
            sections
        }
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

    /** Whether the feed resolved every curated package. */
    fun isComplete(sections: List<HomeSection>): Boolean =
        sections.flatMapTo(HashSet()) { section -> section.apps.map { it.packageName } }
            .containsAll(HomeConfig.curatedPackages)

    /** Cached sections of one feed: app (`games = false`) or games. */
    private suspend fun cachedSections(
        games: Boolean,
        requireComplete: Boolean = true,
    ): List<HomeSection> {
        val entries = homeFeedDao.entriesSince(System.currentTimeMillis() - CACHE_TTL_MS)
            .filter { HomeConfig.isGameSection(it.sectionId) == games }
        // Discard a cache written by an older content config: section ids that
        // are no longer known force a rebuild instead of showing stale rows.
        val known = entries.all { entry ->
            if (games) HomeConfig.isGameSection(entry.sectionId)
            else HomeConfig.isAppSection(entry.sectionId)
        }
        if (!known) return emptyList()
        val sections = entries.toSections()
        if (requireComplete && !games) {
            ensureFailuresLoaded()
            val resolved = sections.flatMapTo(HashSet()) { section ->
                section.apps.map { it.packageName }
            }
            // Rebuild only when there is something new to resolve. Packages
            // that already failed recently are not worth a blocking rebuild:
            // they are retried on an explicit reload or once the failure ages
            // out — not on every feed access.
            val missing = HomeConfig.curatedPackages.filterNot {
                it in resolved || isRecentlyFailed(it)
            }
            if (missing.isNotEmpty()) return emptyList()
        }
        return sections
    }

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

    /**
     * Builds the app feed, retrying the packages that did not resolve yet.
     *
     * Resolution is cached per app, so a retry pass only searches what is still
     * missing — it converges once the catalog stops rate limiting. [retryFailed]
     * (an explicit reload) also retries the packages that failed earlier;
     * a scheduled rebuild skips them.
     */
    private suspend fun buildAppFeed(retryFailed: Boolean): List<HomeSection> {
        ensureFailuresLoaded()
        if (retryFailed) {
            failedPackages.clear()
            unresolvedAppDao.clear()
        }
        builtPackages.clear()
        publishProgress()

        var sections = buildFeedOnce()
        var pass = 0
        while (!isComplete(sections) && pass < MAX_COMPLETION_PASSES) {
            delay(COMPLETION_RETRY_DELAY_MS * (pass + 1))
            sections = buildFeedOnce()
            pass++
        }

        // Remember what is still missing, so the next access serves the cached
        // feed instead of resolving the same packages over the network again.
        val resolved = sections.flatMapTo(HashSet()) { it.apps.map { app -> app.packageName } }
        recordUnresolved(HomeConfig.curatedPackages - resolved)
        return sections
    }

    private suspend fun buildFeedOnce(): List<HomeSection> = coroutineScope {
        val featured = async { loadCurated(HomeConfig.featured) }
        val categories = HomeConfig.categories.map { category ->
            async { loadCuratedCategory(category) }
        }

        val readyCategories = categories.awaitAll().filterNotNull()
        val readyFeatured = featured.await()

        buildList {
            if (readyFeatured.isNotEmpty()) {
                add(
                    HomeSection(
                        id = HomeConfig.RECOMMENDED_SECTION_ID,
                        title = HomeConfig.RECOMMENDED_SECTION_TITLE,
                        apps = readyFeatured,
                        featured = true,
                        description = HomeConfig.RECOMMENDED_DESCRIPTION,
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

    /** A curated category: resolve its fixed app list and wrap it into a section. */
    private suspend fun loadCuratedCategory(category: CuratedCategory): HomeSection? {
        val apps = loadCurated(category.apps)
        if (apps.isEmpty()) return null
        return HomeSection(
            id = category.id,
            title = category.title,
            apps = apps,
            description = category.description,
        )
    }

    /**
     * Resolves a curated package list into catalog apps.
     *
     * Already-resolved apps come from the Room cache; only the missing ones hit
     * the network, so retries and later rebuilds are cheap. Packages from the
     * negative cache are skipped until they age out.
     */
    private suspend fun loadCurated(items: List<CuratedApp>): List<CatalogApp> {
        if (items.isEmpty()) return emptyList()
        val (skipped, pending) = items.partition { isRecentlyFailed(it.packageName) }
        skipped.forEach { markBuilt(it.packageName) }
        if (pending.isEmpty()) return emptyList()
        val freshAfter = System.currentTimeMillis() - RESOLUTION_TTL_MS
        val cached = catalogAppDao.getByPackages(pending.map { it.packageName })
            .filter { it.cachedAt >= freshAfter }
            .associateBy { it.packageName }
        return coroutineScope {
            pending
                .map { item ->
                    async {
                        val app = cached[item.packageName]?.toCatalogApp()
                            ?: throttled { resolveCurated(item) }
                        if (app != null) markBuilt(item.packageName)
                        app
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    /** Marks a package as handled in the current build and republishes progress. */
    private fun markBuilt(packageName: String) {
        if (builtPackages.add(packageName)) publishProgress()
    }

    private fun publishProgress() {
        _buildProgress.update { it.copy(done = builtPackages.size.coerceAtMost(it.total)) }
    }

    /** Loads the negative cache once per process. */
    private suspend fun ensureFailuresLoaded() {
        if (failuresLoaded) return
        failuresMutex.withLock {
            if (!failuresLoaded) {
                unresolvedAppDao.getAll().forEach { failedPackages[it.packageName] = it.attemptedAt }
                failuresLoaded = true
            }
        }
    }

    /** Whether a package failed recently and should not be retried yet. */
    private fun isRecentlyFailed(packageName: String): Boolean =
        System.currentTimeMillis() - (failedPackages[packageName] ?: 0L) < FAILURE_TTL_MS

    /**
     * Persists the packages that are still unresolved. An entry that was
     * already in the negative cache keeps its original attempt time — only a
     * real retry refreshes it.
     */
    private suspend fun recordUnresolved(missing: Set<String>) {
        if (missing.isEmpty()) return
        val now = System.currentTimeMillis()
        missing.forEach { failedPackages.putIfAbsent(it, now) }
        unresolvedAppDao.replaceAll(
            missing.map { UnresolvedAppEntity(it, failedPackages.getValue(it)) },
        )
        builtPackages.addAll(missing)
        publishProgress()
    }

    /**
     * Resolves one curated app: by display name first (the catalog indexes names
     * better than package names), then exactly by package through `overallInfo`.
     */
    private suspend fun resolveCurated(item: CuratedApp): CatalogApp? {
        searchExact(item.name, item.packageName)?.let { return it }
        return overallInfoApp(item.packageName)
    }

    private suspend fun searchExact(query: String, packageName: String): CatalogApp? =
        runCatching {
            api.search(query, pageSize = HomeConfig.RECOMMENDED_SEARCH_SIZE)
                .firstOrNull { it.packageName == packageName }
        }.getOrNull()

    /** Exact fallback: `overallInfo` accepts a package name and needs no search. */
    private suspend fun overallInfoApp(packageName: String): CatalogApp? =
        runCatching { api.overallInfo(packageName).toCatalogApp() }.getOrNull()

    private suspend fun <T> throttled(block: suspend () -> T): T =
        throttle.withPermit { withRetry(block) }

    /** Retries on 429 with a linear delay — the catalog likes to rate limit. */
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

    private fun CatalogAppEntity.toCatalogApp(): CatalogApp = CatalogApp(
        appId = appId,
        packageName = packageName,
        name = name,
        shortDescription = shortDescription,
        iconUrl = iconUrl,
        versionCode = versionCode,
        minSdkVersion = null,
        averageUserRating = averageUserRating,
        totalRatings = totalRatings,
        downloadsText = downloadsText,
    )

    private fun AppDetails.toCatalogApp(): CatalogApp? {
        val resolved = packageName ?: return null
        return CatalogApp(
            appId = appId,
            packageName = resolved,
            name = name ?: resolved,
            shortDescription = shortDescription,
            iconUrl = iconUrl,
            versionCode = versionCode,
            minSdkVersion = minSdkVersion,
        )
    }

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
                    description = HomeConfig.descriptionFor(ordered.first().sectionId),
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

        /** How long a resolved app may be reused before it is looked up again. */
        const val RESOLUTION_TTL_MS = 24 * 60 * 60 * 1000L

        /**
         * How long an unresolved package stays in the negative cache. After it
         * ages out, a scheduled rebuild retries the package once more.
         */
        const val FAILURE_TTL_MS = RESOLUTION_TTL_MS

        /**
         * Four is the sweet spot: the search endpoint starts returning 429 above
         * that, which makes the whole load slower, not faster.
         */
        const val MAX_CONCURRENT_REQUESTS = 4

        /** Extra passes to resolve the packages that failed on the first try. */
        const val MAX_COMPLETION_PASSES = 2
        const val COMPLETION_RETRY_DELAY_MS = 1_200L

        const val MAX_RETRIES = 3
        const val RETRY_BASE_DELAY_MS = 800L
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
