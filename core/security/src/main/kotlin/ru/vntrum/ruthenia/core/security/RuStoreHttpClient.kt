/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.security

import java.io.IOException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Asynchronous OkHttp call as a suspend function with cancellation support. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        },
    )
    continuation.invokeOnCancellation { cancel() }
}

/**
 * HTTP client for RuStore domains.
 *
 * Trust strategy:
 *  1. first the regular system client (public Android roots);
 *  2. if the handshake failed specifically on `*.rustore.ru` — retry through
 *     the composite client (system trust + Russian Trusted CA from the APK).
 *
 * Both clients use the same [CertificatePinner], so pinning
 * works the same regardless of which chain is chosen. A pin failure
 * (`SSLPeerUnverifiedException`) does not trigger a retry — this is a deliberate fail-closed.
 */
class RuStoreHttpClient internal constructor(
    private val systemClient: OkHttpClient,
    private val compositeClient: OkHttpClient,
) {

    /**
     * Client for loading icons/screenshots (Coil). The RuStore CDN is served by the
     * Russian Trusted chain, so we use the composite variant right away.
     */
    val imageClient: OkHttpClient get() = compositeClient

    suspend fun execute(request: Request): Response {
        return try {
            systemClient.newCall(request).await()
        } catch (e: SSLException) {
            // A pin failure is a deliberate fail-closed; it must not be retried.
            if (e is SSLPeerUnverifiedException) throw e
            if (!RuStoreHosts.isRuStoreHost(request.url.host)) throw e
            compositeClient.newCall(request).await()
        }
    }
}

/** Builds a pair of clients for the current pin config. */
object RuStoreClients {

    fun build(
        context: android.content.Context,
        pins: PinConfig,
        configure: OkHttpClient.Builder.() -> Unit = {},
    ): RuStoreHttpClient {
        val certificatePinner = pins.toCertificatePinner()

        val systemClient = OkHttpClient.Builder()
            .apply(configure)
            .certificatePinner(certificatePinner)
            .build()

        val compositeTrustManager = TrustStores.compositeTrustManager(context)
        val compositeClient = OkHttpClient.Builder()
            .apply(configure)
            .sslSocketFactory(
                compositeTrustManager.asSocketFactory(),
                compositeTrustManager,
            )
            .certificatePinner(certificatePinner)
            .build()

        return RuStoreHttpClient(systemClient, compositeClient)
    }
}
