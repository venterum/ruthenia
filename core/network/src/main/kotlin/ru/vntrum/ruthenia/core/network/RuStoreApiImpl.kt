/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.network

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import com.vntrum.ruthenia.core.security.RuStoreHttpClient

@Singleton
internal class RuStoreApiImpl @Inject constructor(
    private val client: RuStoreHttpClient,
    private val sessions: SecureSessionProvider,
    private val deviceProfile: DeviceProfileProvider,
    private val json: Json,
) : RuStoreApi {

    override suspend fun search(
        query: String,
        pageNumber: Int,
        pageSize: Int,
    ): List<CatalogApp> {
        val url = RuStoreConfig.SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("pageNumber", pageNumber.toString())
            .addQueryParameter("pageSize", pageSize.toString())
            .build()

        // Search is not signed, but still requires deviceId.
        val request = Request.Builder()
            .url(url)
            .headers(DeviceHeaders.build(sessions.currentDeviceId))
            .build()

        return execute(request) { text ->
            json.decodeFromString<SearchEnvelope>(text)
                .body
                ?.content
                .orEmpty()
                .map(SearchItemDto::toModel)
        }
    }

    override suspend fun overallInfo(packageName: String): AppDetails {
        // The endpoint accepts a package name and returns a numeric appId.
        val url = "${RuStoreConfig.OVERALL_INFO_URL}/$packageName".toHttpUrl()
        return executeSigned(url, body = null) { text ->
            val body = json.decodeFromString<AppInfoEnvelope>(text).body
                ?: throw RuStoreException.InvalidResponse("overallInfo returned an empty body")
            body.toModel()
        }
    }

    override suspend fun developerApps(
        companyId: String,
        limit: Int,
        continuation: String?,
    ): DeveloperAppsPage {
        val builder = "${RuStoreConfig.DEV_APPS_URL}/$companyId/apps".toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
        if (continuation != null) {
            builder.addQueryParameter("continuation", continuation)
        }
        val request = Request.Builder()
            .url(builder.build())
            .headers(DeviceHeaders.build(sessions.currentDeviceId))
            .build()

        return execute(request) { text ->
            val body = json.decodeFromString<DevAppsEnvelope>(text).body
            DeveloperAppsPage(
                apps = body?.elements.orEmpty().map(SearchItemDto::toModel),
                continuation = body?.continuation,
            )
        }
    }

    override suspend fun selections(): List<RuStoreSelection> {
        val url = (RuStoreConfig.WEB_CATALOG_URL + RuStoreConfig.WEB_CATALOG_PATH).toHttpUrl()
        return execute(webCatalogRequest(url)) { text ->
            RuStoreWebSelectionsParser.parseSelections(text, json)
        }
    }

    override suspend fun selectionApps(selectionId: String): List<CatalogApp> {
        val url = (
            RuStoreConfig.WEB_CATALOG_URL +
                RuStoreConfig.WEB_FEATURING_PATH +
                "/" +
                selectionId
            ).toHttpUrl()
        return execute(webCatalogRequest(url)) { text ->
            RuStoreWebSelectionsParser.parseApps(text, json)
        }
    }

    /**
     * Request to the RuStore web catalog. The `RSC: 1` header asks Next.js to return
     * a streaming data payload instead of HTML — it is more compact and more stable.
     */
    private fun webCatalogRequest(url: HttpUrl): Request = Request.Builder()
        .url(url)
        .header("RSC", "1")
        .header("Accept-Language", RuStoreConfig.FIRMWARE_LANG)
        .build()

    override suspend fun downloadLinks(
        appId: Long,
        firstInstall: Boolean,
        withoutSplits: Boolean,
    ): DownloadLinks {
        val profile = deviceProfile.current()
        val payload = DownloadLinkRequest(
            appId = appId,
            firstInstall = firstInstall,
            withoutSplits = withoutSplits,
            supportedAbis = profile.supportedAbis,
            sdkVersion = profile.sdkVersion,
            screenDensity = profile.screenDensity,
        )
        val body = json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE)

        return executeSigned(RuStoreConfig.DOWNLOAD_LINK_URL.toHttpUrl(), body) { text ->
            val response = json.decodeFromString<DownloadLinkResponse>(text)
            if (response.downloadUrls.isEmpty()) {
                throw RuStoreException.NoApkAvailable("appId=$appId")
            }
            DownloadLinks(
                appId = response.appId,
                versionCode = response.versionCode,
                urls = response.downloadUrls.map { DownloadUrl(it.url, it.size, it.hash) },
            )
        }
    }

    /**
     * Unsigned request.
     *
     * The response body is read on [Dispatchers.IO]: OkHttp performs the request itself
     * asynchronously, but reading `ResponseBody` is a blocking operation, and the calling
     * code (ViewModel) lives on the main thread.
     */
    private suspend fun <T> execute(request: Request, decode: (String) -> T): T =
        withContext(Dispatchers.IO) {
            try {
                client.execute(request).use { response ->
                    ensureSuccess(response)
                    decodeBody(response, decode)
                }
            } catch (e: IOException) {
                throw RuStoreException.Network(e)
            }
        }

    /**
     * Signed request. On HTTP 419 the session is regenerated, and the request
     * is retried exactly once.
     */
    private suspend fun <T> executeSigned(
        url: HttpUrl,
        body: RequestBody?,
        decode: (String) -> T,
    ): T = withContext(Dispatchers.IO) {
        val first = runSigned(url, body)
        if (first.code != RuStoreConfig.HTTP_SESSION_EXPIRED) {
            return@withContext first.use { response ->
                ensureSuccess(response)
                decodeBody(response, decode)
            }
        }

        first.close()
        sessions.regenerate()

        runSigned(url, body).use { response ->
            ensureSuccess(response)
            decodeBody(response, decode)
        }
    }

    private suspend fun runSigned(url: HttpUrl, body: RequestBody?): Response {
        val session = sessions.current()
        val builder = Request.Builder()
            .url(url)
            .headers(DeviceHeaders.build(session.deviceId, session.signature))
        if (body != null) {
            builder.post(body)
        }
        return try {
            client.execute(builder.build())
        } catch (e: IOException) {
            throw RuStoreException.Network(e)
        }
    }

    private fun ensureSuccess(response: Response) {
        if (!response.isSuccessful) {
            throw RuStoreException.Http(response.code, "RuStore returned HTTP ${response.code}")
        }
    }

    private fun <T> decodeBody(response: Response, decode: (String) -> T): T = try {
        decode(response.decodeText())
    } catch (e: RuStoreException) {
        throw e
    } catch (e: Exception) {
        throw RuStoreException.InvalidResponse("Failed to parse RuStore response", e)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
