/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.security

import android.content.Context
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SNIHostName
import javax.net.ssl.SNIServerName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where the active pin set comes from. */
enum class PinSource {
    /** Pins hardcoded into the APK. */
    BUNDLED,

    /** Pins refreshed by the user from live RuStore chains and the Mintsifry CA. */
    REFRESHED,
}

/** Current state of the certificate pins, for the settings UI. */
data class CertificateStatus(
    val source: PinSource,
    val updatedAt: Long,
    val pinCount: Int,
)

/** Result of a successful manual refresh. */
data class CertificateRefreshSummary(
    val pinCount: Int,
    val probedHosts: Int,
    val officialAnchorMatched: Boolean,
    val updatedAt: Long,
)

/**
 * Manual refresh of the RuStore SPKI pins.
 *
 * Deliberately **not** automatic: it only runs when the user taps "Update
 * certificates" in settings, so the app makes no surprise network requests.
 * Two sources are combined:
 *
 *  1. the live TLS chains of the RuStore hosts, validated against system trust
 *     plus the bundled Russian Trusted Root CA;
 *  2. the official Mintsifry (НУЦ) Root/Sub CA from `gu-st.ru`, used as an
 *     authoritative cross-check and to pin the published Sub CA ahead of a
 *     rotation.
 *
 * The bundled [PinConfigDefaults.INTERMEDIATE_PINS] are always merged in as a
 * fallback; [reset] returns to them.
 */
class CertificateRefresher(
    private val context: Context,
    private val store: PinConfigStore,
    private val discoveryClient: OkHttpClient,
) {

    /** The pin set the HTTP clients are built from. */
    fun current(): PinConfig =
        store.read()?.takeIf { it.hosts.isNotEmpty() } ?: PinConfigDefaults.config()

    fun status(): CertificateStatus {
        val stored = store.read()
        return if (stored != null && stored.hosts.isNotEmpty()) {
            CertificateStatus(
                source = PinSource.REFRESHED,
                updatedAt = stored.updatedAt,
                pinCount = stored.hosts.values.sumOf { it.size },
            )
        } else {
            CertificateStatus(
                source = PinSource.BUNDLED,
                updatedAt = 0L,
                pinCount = PinConfigDefaults.INTERMEDIATE_PINS.size,
            )
        }
    }

    /** Forgets the refreshed set; the bundled pins apply again. */
    fun reset() {
        store.clear()
    }

    suspend fun refresh(): CertificateRefreshSummary = withContext(Dispatchers.IO) {
        val trustManager = TrustStores.compositeTrustManager(context)
        val socketFactory = trustManager.asSocketFactory()

        // 1. Inspect the live chains. The handshake itself validates them against
        //    system trust plus the bundled Russian Trusted Root CA.
        val liveChains = LinkedHashMap<String, List<X509Certificate>>()
        PinConfigDefaults.PROBE_HOSTS.forEach { host ->
            runCatching { probe(host, socketFactory) }
                .onSuccess { liveChains[host] = it }
        }
        require(liveChains.isNotEmpty()) { "Could not reach any RuStore host" }

        // 2. Authoritative Mintsifry anchors. Best-effort: a failure here does not
        //    invalidate the live discovery.
        val official = runCatching { fetchOfficial() }.getOrNull()
        val officialAnchorMatched = official?.let { (root, _) ->
            liveChains.values.flatten().any { certificateVerifiesAgainst(it, root) }
        } ?: false

        val discovered = buildSet {
            liveChains.values.forEach { chain ->
                // Pin every certificate except the leaf: leaves are reissued often.
                chain.drop(1).forEach { add(spkiPin(it)) }
            }
            // The published Sub CA may not be served yet — pin it ahead of time.
            official?.let { add(spkiPin(it.second)) }
        }
        require(discovered.isNotEmpty()) { "No pins discovered" }

        val merged = mergePins(PinConfigDefaults.INTERMEDIATE_PINS, discovered)
        val updatedAt = System.currentTimeMillis()
        store.write(
            PinConfig(
                version = CURRENT_VERSION,
                hosts = mapOf(PinConfigDefaults.HOST_PATTERN to merged),
                updatedAt = updatedAt,
            ),
        )

        CertificateRefreshSummary(
            pinCount = merged.size,
            probedHosts = liveChains.size,
            officialAnchorMatched = officialAnchorMatched,
            updatedAt = updatedAt,
        )
    }

    /** Opens a TLS connection to [host] and returns the certificate chain it presents. */
    private fun probe(host: String, factory: SSLSocketFactory): List<X509Certificate> {
        val socket = factory.createSocket() as SSLSocket
        return try {
            socket.soTimeout = TIMEOUT_MS
            socket.connect(InetSocketAddress(host, HTTPS_PORT), TIMEOUT_MS)
            // SNI is set explicitly: the socket is connected by address, not by name.
            val params = socket.sslParameters
            params.serverNames = listOf<SNIServerName>(SNIHostName(host))
            socket.sslParameters = params
            socket.startHandshake()
            socket.session.peerCertificates.filterIsInstance<X509Certificate>()
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun fetchOfficial(): Pair<X509Certificate, X509Certificate> {
        val root = fetchCertificate(PinConfigDefaults.OFFICIAL_ROOT_URL)
        val sub = fetchCertificate(PinConfigDefaults.OFFICIAL_SUB_URL)
        // The published Sub CA must actually be signed by the published Root CA.
        check(certificateVerifiesAgainst(sub, root)) { "Official Sub CA is not signed by the Root CA" }
        return root to sub
    }

    private fun fetchCertificate(url: String): X509Certificate {
        val request = Request.Builder().url(url).build()
        return discoveryClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} for $url" }
            CertificateFactory.getInstance(CERTIFICATE_TYPE)
                .generateCertificate(response.body.byteStream()) as X509Certificate
        }
    }

    private companion object {
        const val HTTPS_PORT = 443
        const val TIMEOUT_MS = 15_000
        const val CERTIFICATE_TYPE = "X.509"
        const val CURRENT_VERSION = 2
    }
}

/** Bundled pins first, then the freshly discovered ones, deduplicated in order. */
internal fun mergePins(bundled: List<String>, discovered: Set<String>): List<String> =
    LinkedHashSet<String>().apply {
        addAll(bundled)
        addAll(discovered)
    }.toList()

/** SPKI pin in the exact format [okhttp3.CertificatePinner] expects. */
internal fun spkiPin(certificate: X509Certificate): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
    return "sha256/" + Base64.getEncoder().encodeToString(digest)
}

/** Whether [certificate] is currently valid and signed by [issuer]. */
private fun certificateVerifiesAgainst(
    certificate: X509Certificate,
    issuer: X509Certificate,
): Boolean = runCatching {
    certificate.checkValidity()
    certificate.verify(issuer.publicKey)
    true
}.getOrDefault(false)
