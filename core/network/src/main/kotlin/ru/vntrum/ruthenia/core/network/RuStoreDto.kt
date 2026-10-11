/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// --- Search: { code, message, body: { content: [...] } } ---

@Serializable
internal data class SearchEnvelope(val body: SearchBody? = null)

@Serializable
internal data class SearchBody(val content: List<SearchItemDto> = emptyList())

@Serializable
internal data class SearchItemDto(
    val appId: Long,
    val packageName: String,
    val appName: String,
    val shortDescription: String? = null,
    val iconUrl: String? = null,
    val versionCode: Long? = null,
    val minSdkVersion: Int? = null,
    val averageUserRating: Double? = null,
    val totalRatings: Long? = null,
    val roundedDownloadsText: String? = null,
    val labelIds: List<Int> = emptyList(),
)

// --- Developer apps: { code, message, body: { elements, continuation } } ---

@Serializable
internal data class DevAppsEnvelope(val body: DevAppsBody? = null)

@Serializable
internal data class DevAppsBody(
    val elements: List<SearchItemDto> = emptyList(),
    val continuation: String? = null,
    val elementsCount: Int = 0,
)

// --- overallInfo: { code, message, body: {...} } ---

@Serializable
internal data class AppInfoEnvelope(val body: AppInfoDto? = null)

@Serializable
internal data class AppInfoDto(
    val appId: Long,
    val packageName: String? = null,
    val appName: String? = null,
    val companyName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val whatsNew: String? = null,
    val appVerUpdatedAt: String? = null,
    val iconUrl: String? = null,
    val minSdkVersion: Int? = null,
    val targetSdkVersion: Int? = null,
    val fileSize: Long? = null,
    val aggregatorInfo: JsonElement? = null,
    val categories: List<String> = emptyList(),
    val publicCompanyId: String? = null,
    val shortDescription: String? = null,
    val fullDescription: String? = null,
    val website: String? = null,
    val ageRestriction: AgeRestrictionDto? = null,
    val fileUrls: List<AppFileDto> = emptyList(),
    val downloads: Long? = null,
    val labelIds: List<Int> = emptyList(),
    val signature: String? = null,
)

/** App age rating from the catalog response. */
@Serializable
internal data class AgeRestrictionDto(
    val category: String? = null,
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
)

/**
 * A single app file: cover, screenshot, video, etc. We are interested in
 * resources with [type] == `SCREENSHOT`; [ordinal] defines the display order.
 */
@Serializable
internal data class AppFileDto(
    val fileUrl: String,
    val ordinal: Int = 0,
    val type: String? = null,
    val orientation: String? = null,
)

// --- download-link: flat response without an envelope ---

@Serializable
internal data class DownloadLinkResponse(
    val appId: Long? = null,
    val versionCode: Long? = null,
    val downloadUrls: List<DownloadUrlDto> = emptyList(),
)

@Serializable
internal data class DownloadUrlDto(
    val url: String,
    val size: Long? = null,
    val hash: String? = null,
)

// --- download-link request body ---

@Serializable
internal data class DownloadLinkRequest(
    val appId: Long,
    val firstInstall: Boolean,
    val withoutSplits: Boolean = false,
    val supportedAbis: List<String>,
    val sdkVersion: Int,
    val screenDensity: Int,
)

// --- nonce ---

@Serializable
internal data class NonceResponse(val nonce: String)

// --- DTO -> model mapping ---

internal fun SearchItemDto.toModel(): CatalogApp = CatalogApp(
    appId = appId,
    packageName = packageName,
    name = appName,
    shortDescription = shortDescription,
    iconUrl = iconUrl,
    versionCode = versionCode,
    minSdkVersion = minSdkVersion,
    averageUserRating = averageUserRating,
    totalRatings = totalRatings,
    downloadsText = roundedDownloadsText,
    labelIds = labelIds,
)

internal fun AppInfoDto.toModel(): AppDetails = AppDetails(
    appId = appId,
    packageName = packageName,
    name = appName,
    publisher = companyName,
    versionName = versionName,
    versionCode = versionCode,
    whatsNew = whatsNew,
    updatedAt = appVerUpdatedAt,
    iconUrl = iconUrl,
    minSdkVersion = minSdkVersion,
    targetSdkVersion = targetSdkVersion,
    fileSize = fileSize,
    isAggregated = aggregatorInfo != null,
    categories = categories,
    publicCompanyId = publicCompanyId,
    shortDescription = shortDescription,
    fullDescription = fullDescription,
    website = website,
    ageRestriction = ageRestriction?.category,
    screenshots = fileUrls
        .filter { it.type.equals(SCREENSHOT_TYPE, ignoreCase = true) }
        .sortedBy { it.ordinal }
        .map { it.fileUrl },
    downloads = downloads,
    labelIds = labelIds,
    expectedSignature = signature,
)

private const val SCREENSHOT_TYPE = "SCREENSHOT"
