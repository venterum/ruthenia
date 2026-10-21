/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.github

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GithubApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: GithubApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = GithubApiImpl(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true; explicitNulls = false },
            tokenProvider = GithubTokenProvider { null },
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `latest release is parsed and asset digest exposed`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("ETag", "\"etag-1\"")
                .body(RELEASE_JSON)
                .build(),
        )

        val result = api.latestRelease("owner", "repo", includePrereleases = false)
        val release = result.release!!

        assertEquals("v1.2.0", release.tagName)
        assertEquals("1.2.0", release.versionName)
        assertEquals(2_000_000L, release.apkAssets.single().size)
        assertEquals(
            "21145bc0f14217120ad9110c1cfdd72c9874cfc86806f656120b6c762feaafa5",
            release.apkAssets.single().sha256,
        )
        assertEquals("\"etag-1\"", result.etag)

        val request = server.takeRequest()
        assertEquals("/repos/owner/repo/releases/latest", request.url.encodedPath)
        assertEquals("RutheniaStore", request.headers["User-Agent"])
    }

    @Test
    fun `304 keeps the caller's etag`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(304).build())

        val result = api.latestRelease("owner", "repo", etag = "\"etag-1\"")

        assertTrue(result.notModified)
        assertEquals("\"etag-1\"", result.etag)
        assertEquals(
            "\"etag-1\"",
            server.takeRequest().headers["If-None-Match"],
        )
    }

    @Test
    fun `token is sent as a bearer header`() = runBlocking {
        api = GithubApiImpl(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true; explicitNulls = false },
            tokenProvider = GithubTokenProvider { "secret" },
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
        server.enqueue(MockResponse.Builder().code(200).body(RELEASE_JSON).build())

        api.latestRelease("owner", "repo")

        assertEquals("Bearer secret", server.takeRequest().headers["Authorization"])
    }

    @Test(expected = GithubException.RateLimited::class)
    fun `403 with exhausted quota maps to rate limit`() {
        server.enqueue(
            MockResponse.Builder()
                .code(403)
                .setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1700000000")
                .build(),
        )

        runBlocking { api.latestRelease("owner", "repo") }
    }

    private companion object {
        val RELEASE_JSON = """
            {
              "id": 42,
              "tag_name": "v1.2.0",
              "name": "1.2.0",
              "body": "Release notes",
              "draft": false,
              "prerelease": false,
              "published_at": "2026-10-20T21:17:09Z",
              "assets": [
                {
                  "id": 1,
                  "name": "app.apk",
                  "size": 2000000,
                  "content_type": "application/vnd.android.package-archive",
                  "browser_download_url": "https://github.com/owner/repo/releases/download/v1.2.0/app.apk",
                  "digest": "sha256:21145bc0f14217120ad9110c1cfdd72c9874cfc86806f656120b6c762feaafa5"
                }
              ]
            }
        """.trimIndent()
    }
}
