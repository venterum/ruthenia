/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.network

import kotlinx.serialization.Serializable

/** An app in search results. */
@Serializable
data class CatalogApp(
    val appId: Long,
    val packageName: String,
    val name: String,
    val shortDescription: String?,
    val iconUrl: String?,
    val versionCode: Long?,
    val minSdkVersion: Int?,
    val averageUserRating: Double? = null,
    val totalRatings: Long? = null,
    val downloadsText: String? = null,
    val labelIds: List<Int> = emptyList(),
)

/**
 * App details.
 *
 * [isAggregated] corresponds to a non-empty `aggregatorInfo`: for such cards
 * RuStore does not host the APK, files are provided by an external source.
 */
data class AppDetails(
    val appId: Long,
    val packageName: String?,
    val name: String?,
    val publisher: String?,
    val versionName: String?,
    val versionCode: Long?,
    val whatsNew: String?,
    val updatedAt: String?,
    val iconUrl: String?,
    val minSdkVersion: Int?,
    val targetSdkVersion: Int?,
    val fileSize: Long?,
    val isAggregated: Boolean,
    val categories: List<String> = emptyList(),
    val publicCompanyId: String? = null,
    /** Short description from the catalog. */
    val shortDescription: String? = null,
    /** Full app description (may contain line breaks). */
    val fullDescription: String? = null,
    /** App website, if the developer specified one. */
    val website: String? = null,
    /** Age rating, for example "0+". */
    val ageRestriction: String? = null,
    /** Screenshots in catalog order. */
    val screenshots: List<String> = emptyList(),
    /** Download count, if RuStore provided it. */
    val downloads: Long? = null,
    /** Catalog labels (category ids), if RuStore provided them. */
    val labelIds: List<Int> = emptyList(),
    /**
     * Expected app signature from the catalog. RuStore returns it as an
     * opaque string; used only to show the fact of trust.
     */
    val expectedSignature: String? = null,
)

/**
 * RuStore editorial selection from the web catalog.
 *
 * These are not personalized recommendations but lists curated by the editors: the source is
 * the public pages `rustore.ru/catalog` and `/catalog/featuring/{id}`.
 */
data class RuStoreSelection(
    val id: String,
    val title: String,
    val subtitle: String?,
    val coverUrl: String?,
)

/** A page of apps from a single developer. */
data class DeveloperAppsPage(
    val apps: List<CatalogApp>,
    val continuation: String?,
)

/** A single download link: a universal APK or one of the config splits. */
data class DownloadUrl(
    val url: String,
    val size: Long?,
    val hash: String?,
)

/**
 * App file links.
 *
 * One URL — a universal APK (RuStore serves it next to the `.zip` container).
 * Several URLs — the base APK and config splits, installed in a single session.
 */
data class DownloadLinks(
    val appId: Long?,
    val versionCode: Long?,
    val urls: List<DownloadUrl>,
)

/** Behavioral boundary of the integration: the rest of the app knows only this interface. */
interface RuStoreApi {

    /** Search by name or package name. No signature required, but `deviceId` is needed. */
    suspend fun search(
        query: String,
        pageNumber: Int = 0,
        pageSize: Int = RuStoreConfig.DEFAULT_PAGE_SIZE,
    ): List<CatalogApp>

    /**
     * App details. Called by **package name**, not by numeric
     * `appId`: the endpoint accepts only the former and returns the latter.
     */
    suspend fun overallInfo(packageName: String): AppDetails

    /**
     * Apps from a single developer by their public id (`publicCompanyId`
     * from [AppDetails]). Unsigned request: `deviceId` is enough.
     */
    suspend fun developerApps(
        companyId: String,
        limit: Int = RuStoreConfig.DEFAULT_DEVELOPER_PAGE_SIZE,
        continuation: String? = null,
    ): DeveloperAppsPage

    /**
     * RuStore editorial selections. The data comes in the server payload
     * of the web catalog, so no signature or deviceId is required.
     */
    suspend fun selections(): List<RuStoreSelection>

    /** Apps of a selection by its id (UUID from [RuStoreSelection.id]). */
    suspend fun selectionApps(selectionId: String): List<CatalogApp>

    /**
     * APK links. Requires the **numeric** `appId` from [AppDetails.appId] —
     * with a package name the endpoint responds with HTTP 400.
     */
    suspend fun downloadLinks(
        appId: Long,
        firstInstall: Boolean = true,
        /**
         * `true` — request a single (universal) APK without config splits. RuStore
         * returns it only if the developer built one; otherwise splits are returned.
         */
        withoutSplits: Boolean = false,
    ): DownloadLinks
}

/** Typed network-layer errors — the UI shows them without parsing codes. */
sealed class RuStoreException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class Http(val code: Int, message: String) : RuStoreException(message)

    class InvalidResponse(message: String, cause: Throwable? = null) :
        RuStoreException(message, cause)

    class Network(cause: Throwable) : RuStoreException("Network request failed", cause)

    class SessionUnavailable(message: String, cause: Throwable? = null) :
        RuStoreException(message, cause)

    /** Aggregated card: the APK is hosted by an external source, RuStore has no files. */
    class AggregatedApp(val packageName: String) :
        RuStoreException("Application '$packageName' is aggregated and has no direct APK")

    /** No files for another reason. */
    class NoApkAvailable(val packageName: String) :
        RuStoreException("No APK files available for '$packageName'")
}
