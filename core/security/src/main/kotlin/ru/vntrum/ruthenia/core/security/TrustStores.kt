/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.security

import android.content.Context
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Composite [X509TrustManager]: a chain is considered trusted if it is accepted by
 * ANY of the delegates. This allows trusting both the system roots and the
 * Russian Trusted Root/Sub CA without requiring the user to manually install
 * the certificate into the system.
 */
class CompositeTrustManager(
    private val delegates: List<X509TrustManager>,
) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // The app works anonymously and never presents a client certificate.
        throw CertificateException("Client certificate authentication is not supported")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw IllegalArgumentException("Server certificate chain is empty")
        }
        var lastFailure: CertificateException? = null
        for (delegate in delegates) {
            try {
                delegate.checkServerTrusted(chain, authType)
                return
            } catch (e: CertificateException) {
                lastFailure = e
            }
        }
        throw lastFailure
            ?: CertificateException("No delegate trust manager accepted the chain")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        delegates.flatMap { it.acceptedIssuers.asList() }.toTypedArray()
}

/** Trust manager factories: system and one built on bundled CAs. */
object TrustStores {

    private const val KEYSTORE_TYPE = "PKCS12"
    private const val CERTIFICATE_TYPE = "X.509"

    /** Public certificates of the Mintsifry CA, shipped as app resources. */
    private val RUSSIAN_TRUSTED_CA_RES = intArrayOf(
        R.raw.russian_trusted_root_ca,
        R.raw.russian_trusted_sub_ca,
    )

    /** System trust: standard public Android roots. */
    fun systemTrustManager(): X509TrustManager = trustManagerFrom(null)

    /** Trust in the Russian Trusted Root/Sub CA from APK resources. */
    fun russianTrustedTrustManager(context: Context): X509TrustManager {
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null, null) }
        val certificateFactory = CertificateFactory.getInstance(CERTIFICATE_TYPE)
        RUSSIAN_TRUSTED_CA_RES.forEachIndexed { index, resId ->
            context.resources.openRawResource(resId).use { input ->
                val certificate = certificateFactory.generateCertificate(input) as X509Certificate
                keyStore.setCertificateEntry("russian_trusted_ca_$index", certificate)
            }
        }
        return trustManagerFrom(keyStore)
    }

    /** System trust + Russian Trusted CA. */
    fun compositeTrustManager(context: Context): CompositeTrustManager =
        CompositeTrustManager(
            listOf(
                systemTrustManager(),
                russianTrustedTrustManager(context),
            ),
        )

    private fun trustManagerFrom(keyStore: KeyStore?): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            ?: error("Platform returned no X509TrustManager")
    }
}

/** [SSLSocketFactory] using exactly the passed trust manager. */
fun X509TrustManager.asSocketFactory(): SSLSocketFactory {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf(this), null)
    return sslContext.socketFactory
}
