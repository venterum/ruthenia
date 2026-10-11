/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.security

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner

/**
 * A set of SPKI pins grouped by host.
 *
 * The format intentionally matches what [CertificatePinner] accepts: the key is a
 * host pattern (`*.rustore.ru`), the value is a list of pins of the form
 * `sha256/<base64 SPKI>`.
 *
 * [updatedAt] is the epoch-millis of the last manual refresh, or `0` when the
 * bundled set is in use.
 */
@Serializable
data class PinConfig(
    val version: Int = 1,
    val hosts: Map<String, List<String>> = emptyMap(),
    val updatedAt: Long = 0L,
) {

    fun toCertificatePinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()
        hosts.forEach { (pattern, pins) ->
            if (pins.isNotEmpty()) {
                builder.add(pattern, *pins.toTypedArray())
            }
        }
        return builder.build()
    }
}

/**
 * Pins hardcoded into the APK.
 *
 * We pin only intermediate certificates: they live for years, whereas the leaf
 * is reissued every few months, and the root of the Russian Trusted CA
 * is not even served by the server in the `static-m.rustore.ru` chain.
 *
 * Important: under `*.rustore.ru` there are TWO chains at once — the public HARICA
 * (backapi/api/static/www) and Russian Trusted (static-m, the CDN with APKs and icons),
 * so a single set must contain pins for both. The Sub CA has already rotated, so
 * we keep both known variants.
 */
object PinConfigDefaults {

    const val HOST_PATTERN = "*.rustore.ru"

    /** SHA-256 SPKI (base64) of intermediate certificates. */
    val INTERMEDIATE_PINS: List<String> = listOf(
        // HARICA DV TLS RSA — public backapi/api/static/www chain (until 2036).
        "sha256/rk2I2etvSahZka3FPKyvABNS0NJaDCduKDeQ5Vvg/Xs=",
        // Russian Trusted Sub CA — static-m.rustore.ru chain (until 2029).
        "sha256/N7la4XONxMaWYRRHFWaf25yT562zNjQi3rYcDzrPZcE=",
        // Russian Trusted Sub CA as issued by gu-st.ru (until 2027).
        "sha256/BEeqSxjEi56NsW6RgJKG3Sfv1qULqA0whOuecLqOHco=",
    )

    /**
     * Official Mintsifry (НУЦ) certificates. `gu-st.ru` is served over a public
     * GlobalSign certificate, so these can be fetched with ordinary system trust —
     * there is no chicken-and-egg with the Russian Trusted CA they carry.
     */
    const val OFFICIAL_ROOT_URL =
        "https://gu-st.ru/content/lending/russian_trusted_root_ca_pem.crt"
    const val OFFICIAL_SUB_URL =
        "https://gu-st.ru/content/lending/russian_trusted_sub_ca_pem.crt"

    /**
     * Hosts whose live TLS chains are inspected on a manual refresh. Between them
     * they cover both certificate chains used by RuStore.
     */
    val PROBE_HOSTS: List<String> = listOf(
        "static-m.rustore.ru",
        "backapi.rustore.ru",
        "api.rustore.ru",
        "static.rustore.ru",
        "www.rustore.ru",
    )

    fun config(): PinConfig = PinConfig(
        version = 1,
        hosts = mapOf(HOST_PATTERN to INTERMEDIATE_PINS),
        updatedAt = 0L,
    )
}

/** Local pin-config cache that survives an app restart. */
class PinConfigStore(private val file: File, private val json: Json) {

    fun read(): PinConfig? = runCatching {
        if (!file.exists()) return@runCatching null
        json.decodeFromString<PinConfig>(file.readText())
    }.getOrNull()

    fun write(config: PinConfig) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(config))
        }
    }

    /** Forgets the refreshed set, so the bundled pins apply again. */
    fun clear() {
        runCatching { file.delete() }
    }
}
