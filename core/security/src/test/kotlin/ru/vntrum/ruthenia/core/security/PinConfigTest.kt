/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.security

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinConfigTest {

    private val certificate: X509Certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(TEST_CERTIFICATE.toByteArray()))
            as X509Certificate

    @Test
    fun `bundled config covers both rustore certificate chains`() {
        val pins = PinConfigDefaults.config().hosts.getValue(PinConfigDefaults.HOST_PATTERN)

        assertEquals(3, pins.size)
        assertTrue(pins.all { it.startsWith("sha256/") })

        // Public HARICA chain (backapi/api/static/www).
        assertTrue(pins.any { it.endsWith("rk2I2etvSahZka3FPKyvABNS0NJaDCduKDeQ5Vvg/Xs=") })
        // Russian Trusted Sub CA (static-m, the CDN with APKs and icons).
        assertTrue(pins.any { it.endsWith("N7la4XONxMaWYRRHFWaf25yT562zNjQi3rYcDzrPZcE=") })
        // The second known Sub CA variant (gu-st.ru).
        assertTrue(pins.any { it.endsWith("BEeqSxjEi56NsW6RgJKG3Sfv1qULqA0whOuecLqOHco=") })
    }

    @Test(expected = SSLPeerUnverifiedException::class)
    fun `rustore host rejects a certificate that matches no pin`() {
        val pinner = PinConfigDefaults.config().toCertificatePinner()

        pinner.check("static-m.rustore.ru", listOf(certificate))
    }

    @Test
    fun `hosts outside the pin pattern are not pinned`() {
        val pinner = PinConfigDefaults.config().toCertificatePinner()

        // For a foreign host there are no pins — the check does not run and does not throw.
        pinner.check("example.com", listOf(certificate))
    }

    @Test
    fun `spki pin uses the CertificatePinner format`() {
        val pin = spkiPin(certificate)

        assertTrue(pin.startsWith("sha256/"))
        // Deterministic for the same certificate.
        assertEquals(pin, spkiPin(certificate))
    }

    @Test
    fun `merge keeps bundled pins first and appends new ones without duplicates`() {
        assertEquals(
            listOf("sha256/a", "sha256/b", "sha256/c"),
            mergePins(
                bundled = listOf("sha256/a", "sha256/b"),
                discovered = setOf("sha256/b", "sha256/c"),
            ),
        )
    }

    private companion object {
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
