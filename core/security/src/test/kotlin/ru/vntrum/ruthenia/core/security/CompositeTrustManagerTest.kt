/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.security

import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import org.junit.Assert.assertEquals
import org.junit.Test

class CompositeTrustManagerTest {

    private val certificate: X509Certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(TEST_CERTIFICATE.toByteArray()))
            as X509Certificate

    @Test
    fun `accepts when at least one delegate trusts the chain`() {
        val manager = CompositeTrustManager(
            listOf(FakeTrustManager(accept = false), FakeTrustManager(accept = true)),
        )

        // Must not throw: the second delegate accepts the chain.
        manager.checkServerTrusted(arrayOf(certificate), "RSA")
    }

    @Test(expected = CertificateException::class)
    fun `rejects when every delegate rejects the chain`() {
        val manager = CompositeTrustManager(
            listOf(FakeTrustManager(accept = false), FakeTrustManager(accept = false)),
        )

        manager.checkServerTrusted(arrayOf(certificate), "RSA")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an empty chain instead of trusting it`() {
        CompositeTrustManager(listOf(FakeTrustManager(accept = true)))
            .checkServerTrusted(emptyArray(), "RSA")
    }

    @Test
    fun `merges accepted issuers of all delegates`() {
        val manager = CompositeTrustManager(
            listOf(
                FakeTrustManager(accept = true, issuers = arrayOf(certificate)),
                FakeTrustManager(accept = true, issuers = arrayOf(certificate)),
            ),
        )

        assertEquals(2, manager.acceptedIssuers.size)
    }

    private class FakeTrustManager(
        private val accept: Boolean,
        private val issuers: Array<X509Certificate> = emptyArray(),
    ) : X509TrustManager {

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (!accept) throw CertificateException("rejected by fake trust manager")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = issuers
    }

    private companion object {
        /** Self-signed certificate generated only for the test. */
        const val TEST_CERTIFICATE = """
-----BEGIN CERTIFICATE-----
MIIDETCCAfmgAwIBAgIUWTNONq988s4DGGGXvfis1CFF8KcwDQYJKoZIhvcNAQEL
BQAwGDEWMBQGA1UEAwwNcHJvc3RvcmUtdGVzdDAeFw0yNjA5MjMxMTAzNTRaFw0z
NjA5MjAxMTAzNTRaMBgxFjAUBgNVBAMMDXByb3N0b3JlLXRlc3QwggEiMA0GCSqG
SIb3DQEBAQUAA4IBDwAwggEKAoIBAQCouv2ydPP+Ghum2c8Vrun83HVqIP/SPtph
XpYFC5dcwgIqzRL3U3TPmY7TB0q1Ev8IGsgzjssMO3cQ0YPsnw3PJE/IFAien5+c
ihrzyxpb/QLPHDj1J7KFwEHxMOO3fmSUvrtu2hTjWH3DXzmS2jdMkkPHQBklU+3a
smB7WfWoXEy6C1BC+0pHKIO2vN2U2Lupgswid6mmRRQ/6v4sWYzYvdWtIuWcBbIx
N2doigl/vfFi/rMgS/ixCmOGyNKmh2rsl9JRMgTVUoYgMroB76Vwf3EvJt3yt7rH
Iz//Tv1psNVQFRgIJEuJDExprVCkKud/qE9BM80nB7QOTYb8S1qjAgMBAAGjUzBR
MB0GA1UdDgQWBBR935/bkZkDasm2PE4F47Xtct8IbjAfBgNVHSMEGDAWgBR935/b
kZkDasm2PE4F47Xtct8IbjAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUA
A4IBAQAQkC5DcpdEo7sUlJKYhAoaN8YGarYthGm5fCQDoKf3qJ3EFrIngNoAMOFv
MuOYsvia9Zn4wu3B7qcbiqV2Wj2DoalyhTL2iBU+BmfUw4bj/5+tLe/aqL7m0c8M
rThM5+yDIo6H3uuJ4jWqVvR4b6Lwto5OgUdDNsM9WBFbKdDAgabvbPbzFu5IVs1b
I9b+/1b4dkYtyeUoGAAlP43jbIgZXIef3avXTJJWp7S8X2WnMfzlZheXEsL+R5PY
8vCAL3mRvb0hvL2JyzbJfIW1+vyjDxfPP1qbBPfb2E8mbYBnfNfNc0tfeCbehrHK
zHnfNC7RN19afFPHeuoYJweJkB75
-----END CERTIFICATE-----
"""
    }
}
