/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.github

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import com.vntrum.ruthenia.core.security.await

/** Minimal GitHub REST API used by the app. */
interface GithubApi {

    suspend fun repo(owner: String, repo: String): GithubRepo

    /** Releases for the add/details screen, newest first, without drafts. */
    suspend fun releases(owner: String, repo: String, limit: Int = 10): List<GithubRelease>

    /**
     * Latest release for an update check. Stable by default; with
     * [includePrereleases] the newest non-draft release wins. [etag] enables a
     * conditional request and a 304 response.
     */
    suspend fun latestRelease(
        owner: String,
        repo: String,
        includePrereleases: Boolean = false,
        etag: String? = null,
    ): GithubReleaseResult

    /** Validates the configured token and returns the authenticated account. */
    suspend fun currentUser(): GithubUser
}

class GithubApiImpl(
    private val client: OkHttpClient,
    private val json: Json,
    private val tokenProvider: GithubTokenProvider,
    private val baseUrl: String,
) : GithubApi {

    override suspend fun repo(owner: String, repo: String): GithubRepo =
        getObject("/repos/$owner/$repo", GithubRepoDto.serializer()) { it.toModel() }

    override suspend fun releases(owner: String, repo: String, limit: Int): List<GithubRelease> {
        val response = execute(
            request("/repos/$owner/$repo/releases?per_page=${limit.coerceIn(1, 30)}"),
        )
        val body = responseBody(response)
        return json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(GithubReleaseDto.serializer()),
            body,
        )
            .filterNot { it.draft }
            .map { it.toModel() }
    }

    override suspend fun latestRelease(
        owner: String,
        repo: String,
        includePrereleases: Boolean,
        etag: String?,
    ): GithubReleaseResult {
        if (!includePrereleases) {
            val response = execute(
                request("/repos/$owner/$repo/releases/latest", etag = etag),
            )
            val responseEtag = response.header("ETag") ?: etag
            if (response.code == HTTP_NOT_MODIFIED) {
                response.close()
                return GithubReleaseResult(
                    release = null,
                    notModified = true,
                    etag = responseEtag,
                )
            }
            val body = responseBody(response)
            val dto = json.decodeFromString(GithubReleaseDto.serializer(), body)
            return GithubReleaseResult(dto.toModel(), etag = responseEtag)
        }

        val response = execute(
            request("/repos/$owner/$repo/releases?per_page=10", etag = etag),
        )
        val responseEtag = response.header("ETag") ?: etag
        if (response.code == HTTP_NOT_MODIFIED) {
            response.close()
            return GithubReleaseResult(
                release = null,
                notModified = true,
                etag = responseEtag,
            )
        }
        val body = responseBody(response)
        val releases = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(GithubReleaseDto.serializer()),
            body,
        )
        val latest = releases.firstOrNull { !it.draft }?.toModel()
        return GithubReleaseResult(latest, etag = responseEtag)
    }

    override suspend fun currentUser(): GithubUser {
        val body = responseBody(execute(request("/user")))
        val dto = json.decodeFromString(GithubUserDto.serializer(), body)
        return GithubUser(dto.login)
    }

    private suspend fun <T> getObject(
        path: String,
        serializer: kotlinx.serialization.DeserializationStrategy<T>,
        map: (T) -> GithubRepo,
    ): GithubRepo {
        val body = responseBody(execute(request(path)))
        return map(json.decodeFromString(serializer, body))
    }

    private fun request(path: String, etag: String? = null): Request {
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", USER_AGENT)
        etag?.let { builder.header("If-None-Match", it) }
        tokenProvider.token()?.let { builder.header("Authorization", "Bearer $it") }
        return builder.build()
    }

    private suspend fun execute(request: Request): Response = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).await()
        } catch (error: IOException) {
            throw GithubException.Network(error)
        }
        if (response.isSuccessful || response.code == HTTP_NOT_MODIFIED) return@withContext response

        val code = response.code
        val remaining = response.header("X-RateLimit-Remaining")
        val reset = rateLimitReset(response)
        val body = runCatching { response.body.string() }.getOrNull()
        response.close()

        when {
            code == HTTP_UNAUTHORIZED -> throw GithubException.Unauthorized()

            code == HTTP_NOT_FOUND -> throw GithubException.NotFound(request.url.encodedPath)

            code == HTTP_FORBIDDEN || code == HTTP_TOO_MANY_REQUESTS -> {
                if (code == HTTP_TOO_MANY_REQUESTS || remaining == "0") {
                    throw GithubException.RateLimited(reset)
                }
                throw GithubException.Http(code, errorMessage(body))
            }

            else -> throw GithubException.Http(code, errorMessage(body))
        }
    }

    private suspend fun responseBody(response: Response): String = withContext(Dispatchers.IO) {
        response.use { it.body.string() }
    }

    private fun rateLimitReset(response: Response): Long? {
        response.header("Retry-After")?.toLongOrNull()?.let {
            return System.currentTimeMillis() / 1000 + it
        }
        return response.header("X-RateLimit-Reset")?.toLongOrNull()
    }

    private fun errorMessage(body: String?): String? = body
        ?.let { runCatching { json.decodeFromString(GithubErrorDto.serializer(), it).message }.getOrNull() }
        ?.take(300)

    private companion object {
        const val USER_AGENT = "RutheniaStore"
        const val HTTP_NOT_MODIFIED = 304
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
