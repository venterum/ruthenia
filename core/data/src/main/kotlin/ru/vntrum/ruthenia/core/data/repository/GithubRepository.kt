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
import com.vntrum.ruthenia.core.data.db.GithubAppDao
import com.vntrum.ruthenia.core.data.db.GithubAppEntity
import com.vntrum.ruthenia.core.data.model.GithubLink
import com.vntrum.ruthenia.core.data.model.UpdateStatus
import com.vntrum.ruthenia.core.data.model.toLink
import com.vntrum.ruthenia.core.github.GithubApi
import com.vntrum.ruthenia.core.github.GithubAsset
import com.vntrum.ruthenia.core.github.GithubAssetSelector
import com.vntrum.ruthenia.core.github.GithubRelease
import com.vntrum.ruthenia.core.github.GithubRepo
import com.vntrum.ruthenia.core.github.GithubRepoCoordinates
import com.vntrum.ruthenia.core.github.GithubTokenStore
import com.vntrum.ruthenia.core.github.GithubUser

/** Result of checking one GitHub-linked app. */
data class GithubCheckResult(
    val link: GithubLink,
    val status: UpdateStatus,
    val release: GithubRelease?,
    val asset: GithubAsset?,
)

/**
 * GitHub source storage and API access.
 *
 * All state that survives a restart lives in `github_apps`; the API is only
 * called by explicit user actions and the existing background update check.
 */
@Singleton
class GithubRepository @Inject constructor(
    private val api: GithubApi,
    private val dao: GithubAppDao,
    private val tokenStore: GithubTokenStore,
) {

    fun observeLinks(): Flow<List<GithubLink>> =
        dao.observeAll().map { rows -> rows.map { it.toLink() } }

    suspend fun getLink(packageName: String): GithubLink? =
        dao.get(packageName)?.toLink()

    suspend fun allLinks(): List<GithubLink> = dao.getAll().map { it.toLink() }

    suspend fun repo(owner: String, repo: String): GithubRepo = api.repo(owner, repo)

    suspend fun releases(owner: String, repo: String): List<GithubRelease> =
        api.releases(owner, repo)

    suspend fun latestRelease(
        owner: String,
        repo: String,
        includePrereleases: Boolean,
        etag: String? = null,
    ): GithubRelease? = api.latestRelease(owner, repo, includePrereleases, etag).release

    /**
     * Refreshes the cached latest release for [link].
     *
     * The API may return 304, in which case the cached fields remain valid.
     */
    suspend fun checkForUpdate(
        packageName: String,
        link: GithubLink,
        installedVersionName: String?,
    ): GithubCheckResult {
        val result = api.latestRelease(
            owner = link.owner,
            repo = link.repo,
            includePrereleases = link.includePrereleases,
            etag = link.etag,
        )
        if (result.notModified) {
            val refreshed = link.copy(
                etag = result.etag ?: link.etag,
                lastCheckedAt = System.currentTimeMillis(),
            )
            dao.upsert(refreshed.toEntity(packageName))
            return GithubCheckResult(
                link = refreshed,
                status = refreshed.status(installedVersionName),
                release = null,
                asset = null,
            )
        }

        val release = result.release
            ?: return GithubCheckResult(
                link = link,
                status = UpdateStatus.UNKNOWN,
                release = null,
                asset = null,
            )
        val asset = GithubAssetSelector.select(release, link.assetName)
        val updated = link.copy(
            etag = result.etag ?: link.etag,
            latestReleaseId = release.id,
            latestTag = release.tagName,
            latestVersionName = release.versionName,
            latestPublishedAt = release.publishedAt,
            latestAssetUrl = asset?.downloadUrl,
            latestAssetSize = asset?.size,
            latestAssetDigest = asset?.digest,
            lastCheckedAt = System.currentTimeMillis(),
        )
        dao.upsert(updated.toEntity(packageName))
        return GithubCheckResult(
            link = updated,
            status = updated.status(installedVersionName),
            release = release,
            asset = asset,
        )
    }

    /**
     * Links a repository to a package after its first APK has been resolved.
     * The release becomes both the installed and the latest one.
     */
    suspend fun linkInstalled(
        packageName: String,
        repo: GithubRepo,
        release: GithubRelease,
        asset: GithubAsset,
        includePrereleases: Boolean,
    ) {
        val now = System.currentTimeMillis()
        val entity = GithubAppEntity(
            packageName = packageName,
            owner = repo.owner,
            repo = repo.name,
            repoId = repo.id,
            displayName = repo.name,
            avatarUrl = repo.avatarUrl,
            assetName = asset.name,
            includePrereleases = includePrereleases,
            etag = null,
            installedReleaseId = release.id,
            installedTag = release.tagName,
            latestReleaseId = release.id,
            latestTag = release.tagName,
            latestVersionName = release.versionName,
            latestPublishedAt = release.publishedAt,
            latestAssetUrl = asset.downloadUrl,
            latestAssetSize = asset.size,
            latestAssetDigest = asset.digest,
            lastCheckedAt = now,
        )
        dao.upsert(entity)
    }

    /** Changes the selected asset or prerelease preference for an existing link. */
    suspend fun updatePreferences(
        packageName: String,
        assetName: String?,
        includePrereleases: Boolean,
    ) {
        val entity = dao.get(packageName) ?: return
        dao.upsert(
            entity.copy(
                assetName = assetName,
                includePrereleases = includePrereleases,
            ),
        )
    }

    /** Removes the GitHub link but leaves the installed app in the library. */
    suspend fun unlink(packageName: String) = dao.delete(packageName)

    suspend fun clear() = dao.clear()

    suspend fun replaceAll(links: List<GithubAppEntity>) = dao.replaceAll(links)

    fun hasToken(): Boolean = tokenStore.hasToken()

    fun setToken(token: String?) = tokenStore.setToken(token)

    suspend fun validateToken(): GithubUser = api.currentUser()

    companion object {
        fun parseCoordinates(input: String): GithubRepoCoordinates? =
            com.vntrum.ruthenia.core.github.GithubUrlParser.parse(input)
    }
}

private fun GithubLink.toEntity(packageName: String): GithubAppEntity = GithubAppEntity(
    packageName = packageName,
    owner = owner,
    repo = repo,
    repoId = repoId,
    displayName = displayName,
    avatarUrl = avatarUrl,
    assetName = assetName,
    includePrereleases = includePrereleases,
    etag = etag,
    installedReleaseId = installedReleaseId,
    installedTag = installedTag,
    latestReleaseId = latestReleaseId,
    latestTag = latestTag,
    latestVersionName = latestVersionName,
    latestPublishedAt = latestPublishedAt,
    latestAssetUrl = latestAssetUrl,
    latestAssetSize = latestAssetSize,
    latestAssetDigest = latestAssetDigest,
    lastCheckedAt = lastCheckedAt,
)
