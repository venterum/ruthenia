/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.security

/** Hosts to which Russian Trusted CA trust and pin hashes apply. */
object RuStoreHosts {

    const val DOMAIN = "rustore.ru"

    /** `rustore.ru` and any of its subdomains (`backapi`, `api`, `static-m`, ...). */
    fun isRuStoreHost(host: String): Boolean =
        host == DOMAIN || host.endsWith(".$DOMAIN")
}
