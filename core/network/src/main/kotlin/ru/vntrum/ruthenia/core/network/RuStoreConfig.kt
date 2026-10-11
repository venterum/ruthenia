/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.network

import java.util.Base64

/**
 * All mutable integration configuration in one place.
 *
 * Endpoints and crypto constants are extracted from the native RuStore and, when invalidated,
 * are edited ONLY here — the business logic knows nothing about them.
 */
object RuStoreConfig {

    // --- Endpoints ---
    const val SEARCH_URL = "https://backapi.rustore.ru/applicationData/apps"
    const val OVERALL_INFO_URL = "https://backapi.rustore.ru/applicationData/overallInfo"
    const val DEV_APPS_URL = "https://backapi.rustore.ru/applicationData/devs"
    const val DOWNLOAD_LINK_URL = "https://backapi.rustore.ru/v3/showcase/apps/download-link"
    const val NONCE_URL = "https://api.rustore.ru/v1/secure/nonce"

    /**
     * Public web catalog. Serves editorial selections in the server payload
     * (Next.js RSC); no signature or deviceId is needed.
     */
    const val WEB_CATALOG_URL = "https://www.rustore.ru"
    const val WEB_CATALOG_PATH = "/catalog"
    const val WEB_FEATURING_PATH = "/catalog/featuring"

    // --- Secure session signature ---
    // signature = base64(HMAC-SHA256(KEY, base64decode(nonce) || certSha256))
    val HMAC_KEY: ByteArray =
        Base64.getDecoder().decode("K+eeiCbnVFnZ71KEVal0g5siHaX6v6drh8upeLgEPoU=")

    val APK_CERT_SHA256: ByteArray =
        Base64.getDecoder().decode("Zh8ggo73gN4LebxZ8mowhkMWNV8w5Pkc+hSiB5GDmRQ=")

    // --- Device identity in headers ---
    // Values are intentionally static: RuStore expects a plausible device,
    // while deviceId remains ephemeral and random (see DeviceIdGenerator).
    const val DEVICE_MANUFACTURER = "Google"
    const val DEVICE_MODEL_NAME = "Pixel 8 Pro"
    const val DEVICE_HARDWARE = "husky"
    const val FIRMWARE_VER = "16"
    const val ANDROID_SDK_VER = "36"
    const val FIRMWARE_LANG = "ru"
    const val RUSTORE_VER_CODE = "1105002"
    const val RUSTORE_VERSION_NAME = "1.105.0.2"
    const val DEVICE_TYPE = "mobile"

    const val USER_AGENT =
        "RuStore/$RUSTORE_VERSION_NAME (Android $FIRMWARE_VER; SDK $ANDROID_SDK_VER; " +
            "arm64-v8a; $DEVICE_MANUFACTURER $DEVICE_MODEL_NAME; $FIRMWARE_LANG)"

    /** Session rejected or missing — must be regenerated and retried once. */
    const val HTTP_SESSION_EXPIRED = 419

    const val DEFAULT_PAGE_SIZE = 20

    /** How many developer apps to fetch per request on the card. */
    const val DEFAULT_DEVELOPER_PAGE_SIZE = 20
}
