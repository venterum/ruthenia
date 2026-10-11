/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.network

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Headers

/** Headers that RuStore expects on every request. */
internal object DeviceHeaders {

    fun build(deviceId: String, signature: String? = null): Headers {
        val builder = Headers.Builder()
            .add("deviceId", deviceId)
            .add("firmwareVer", RuStoreConfig.FIRMWARE_VER)
            .add("androidSdkVer", RuStoreConfig.ANDROID_SDK_VER)
            .add("deviceManufacturerName", RuStoreConfig.DEVICE_MANUFACTURER)
            .add("deviceModelName", RuStoreConfig.DEVICE_MODEL_NAME)
            .add(
                "deviceModel",
                "${RuStoreConfig.DEVICE_MANUFACTURER} ${RuStoreConfig.DEVICE_MODEL_NAME}",
            )
            .add("firmwareLang", RuStoreConfig.FIRMWARE_LANG)
            .add("ruStoreVerCode", RuStoreConfig.RUSTORE_VER_CODE)
            .add("deviceType", RuStoreConfig.DEVICE_TYPE)
            .add("User-Agent", RuStoreConfig.USER_AGENT)

        if (signature != null) {
            builder.add("X-Client-Signature", signature)
        }
        return builder.build()
    }
}

/**
 * Real device profile that RuStore requires in the `download-link` body
 * to return config splits for apps without a universal APK.
 */
internal data class DeviceProfile(
    val supportedAbis: List<String>,
    val sdkVersion: Int,
    val screenDensity: Int,
)

@Singleton
internal class DeviceProfileProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun current(): DeviceProfile = DeviceProfile(
        supportedAbis = Build.SUPPORTED_ABIS.toList().ifEmpty { listOf(DEFAULT_ABI) },
        sdkVersion = Build.VERSION.SDK_INT,
        screenDensity = context.resources.displayMetrics.densityDpi,
    )

    private companion object {
        const val DEFAULT_ABI = "arm64-v8a"
    }
}
