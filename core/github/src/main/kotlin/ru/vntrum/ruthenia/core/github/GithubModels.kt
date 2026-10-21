/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.github

import kotlinx.serialization.Serializable

/** A public (or token-visible) GitHub repository. */
data class GithubRepo(
    val id: Long,
    val owner: String,
    val name: String,
    val description: String?,
    val htmlUrl: String,
    val avatarUrl: String?,
    val archived: Boolean,
) {
    val fullName: String get() = "$owner/$name"
}

/** A GitHub release. Only the fields needed by the app are kept. */
data class GithubRelease(
    val id: Long,
    val tagName: String,
    val name: String?,
    val body: String?,
    val prerelease: Boolean,
    val publishedAt: Long?,
    val assets: List<GithubAsset>,
) {
    /** Normalized version for comparison: `v1.2.3` becomes `1.2.3`. */
    val versionName: String get() = GithubVersion.normalize(tagName)

    val apkAssets: List<GithubAsset>
        get() = assets.filter { it.name.endsWith(APK_EXTENSION, ignoreCase = true) && it.size > 0 }
}

/** A downloadable release asset. */
data class GithubAsset(
    val id: Long,
    val name: String,
    val size: Long,
    val contentType: String?,
    val downloadUrl: String,
    /** GitHub's `sha256:<hex>` digest, when present. */
    val digest: String?,
) {
    /** The digest without the `sha256:` prefix, or null. */
    val sha256: String?
        get() = digest
            ?.removePrefix("sha256:")
            ?.takeIf { digest ->
                digest.length == SHA256_HEX_LENGTH &&
                    digest.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }
            }
}

/** Result of a conditional latest-release request. */
data class GithubReleaseResult(
    val release: GithubRelease?,
    val notModified: Boolean = false,
    val etag: String? = null,
)

data class GithubUser(val login: String)

/** Typed GitHub API errors; the UI translates these without parsing messages. */
sealed class GithubException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class NotFound(val what: String) : GithubException("GitHub resource not found: $what")

    class Unauthorized : GithubException("GitHub token is missing or invalid")

    class RateLimited(val resetAtEpochSeconds: Long?) :
        GithubException("GitHub API rate limit exceeded")

    class Http(val code: Int, val detail: String?) :
        GithubException("GitHub API request failed with HTTP $code")

    class InvalidResponse(message: String, cause: Throwable? = null) :
        GithubException(message, cause)

    class Network(cause: Throwable) : GithubException("GitHub network request failed", cause)
}

internal const val APK_EXTENSION = ".apk"
internal const val SHA256_HEX_LENGTH = 64

@Serializable
internal data class GithubOwnerDto(
    val login: String,
    val avatar_url: String? = null,
)

@Serializable
internal data class GithubRepoDto(
    val id: Long,
    val name: String,
    val full_name: String,
    val description: String? = null,
    val html_url: String,
    val owner: GithubOwnerDto,
    val archived: Boolean = false,
    val visibility: String? = null,
)

@Serializable
internal data class GithubReleaseDto(
    val id: Long,
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val published_at: String? = null,
    val assets: List<GithubAssetDto> = emptyList(),
)

@Serializable
internal data class GithubAssetDto(
    val id: Long,
    val name: String,
    val size: Long = 0,
    val content_type: String? = null,
    val browser_download_url: String,
    val digest: String? = null,
)

@Serializable
internal data class GithubUserDto(val login: String)

@Serializable
internal data class GithubErrorDto(val message: String? = null)

internal fun GithubRepoDto.toModel(): GithubRepo = GithubRepo(
    id = id,
    owner = owner.login,
    name = name,
    description = description,
    htmlUrl = html_url,
    avatarUrl = owner.avatar_url,
    archived = archived,
)

internal fun GithubAssetDto.toModel(): GithubAsset = GithubAsset(
    id = id,
    name = name,
    size = size,
    contentType = content_type,
    downloadUrl = browser_download_url,
    digest = digest,
)

internal fun GithubReleaseDto.toModel(): GithubRelease = GithubRelease(
    id = id,
    tagName = tag_name,
    name = name,
    body = body,
    prerelease = prerelease,
    publishedAt = published_at?.let(GithubDates::parseMillis),
    assets = assets.map { it.toModel() },
)
