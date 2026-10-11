/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.network

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.vntrum.ruthenia.core.security.RuStoreHttpClient

/** A one-shot pair of `deviceId` + signature used to sign protected requests. */
internal data class SecureSession(
    val deviceId: String,
    val signature: String,
)

/**
 * RuStore secure session.
 *
 * `deviceId` is created once per process, the signature is regenerated from a nonce
 * and reused until the first HTTP 419.
 */
@Singleton
internal class SecureSessionProvider @Inject constructor(
    private val client: RuStoreHttpClient,
    private val json: Json,
) {

    private val mutex = Mutex()

    @Volatile
    private var cached: SecureSession? = null

    private val deviceId: String by lazy { DeviceIdGenerator.next() }

    /** `deviceId` is also needed by unsigned requests (search). */
    val currentDeviceId: String get() = deviceId

    suspend fun current(): SecureSession = cached ?: mutex.withLock {
        cached ?: generate().also { cached = it }
    }

    /** Called on HTTP 419: the session was rejected and a new one is needed. */
    suspend fun regenerate(): SecureSession = mutex.withLock {
        generate().also { cached = it }
    }

    private suspend fun generate(): SecureSession {
        val nonce = fetchNonce()
        return SecureSession(deviceId = deviceId, signature = sign(nonce))
    }

    private suspend fun fetchNonce(): ByteArray {
        val request = Request.Builder()
            .url(RuStoreConfig.NONCE_URL)
            .headers(DeviceHeaders.build(deviceId))
            .post(EMPTY_BODY)
            .build()

        return try {
            client.execute(request).use { response ->
                if (!response.isSuccessful) {
                    throw RuStoreException.SessionUnavailable(
                        "nonce request returned HTTP ${response.code}",
                    )
                }
                val nonce = json.decodeFromString<NonceResponse>(response.decodeText()).nonce
                Base64.getDecoder().decode(nonce)
            }
        } catch (e: RuStoreException) {
            throw e
        } catch (e: Exception) {
            throw RuStoreException.SessionUnavailable("failed to obtain nonce", e)
        }
    }

    private fun sign(nonce: ByteArray): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(RuStoreConfig.HMAC_KEY, HMAC_ALGORITHM))
        mac.update(nonce)
        mac.update(RuStoreConfig.APK_CERT_SHA256)
        return Base64.getEncoder().encodeToString(mac.doFinal())
    }

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
        val EMPTY_BODY = ByteArray(0).toRequestBody("application/json".toMediaType())
    }
}
