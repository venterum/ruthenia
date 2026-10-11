/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.network

import java.security.SecureRandom

/**
 * Ephemeral `deviceId` for requests to RuStore.
 *
 * The format mirrors the native client: `<8 random bytes in hex>-<suffix>`, where
 * the suffix is the result of Java-compatible hashes of manufacturer/model/hardware.
 * The identifier is NOT tied to the device's advertising ID, is NOT persisted to disk
 * and lives only within the process.
 */
object DeviceIdGenerator {

    private val random = SecureRandom()

    /** Stable suffix for a given "model" — extracted for testability. */
    fun suffixFor(manufacturer: String, modelName: String, hardware: String): String {
        val m = javaStringHashCode(manufacturer)
        val mo = javaStringHashCode(modelName)
        val h = javaStringHashCode(hardware)
        val d = javaStringHashCode(hardware)
        // Kotlin Int is already 32-bit, so overflow behaves as in Java.
        return (d + ((h + ((mo + (m * 31)) * 31)) * 31)).toString()
    }

    fun next(
        manufacturer: String = RuStoreConfig.DEVICE_MANUFACTURER,
        modelName: String = RuStoreConfig.DEVICE_MODEL_NAME,
        hardware: String = RuStoreConfig.DEVICE_HARDWARE,
    ): String {
        val bytes = ByteArray(8).also(random::nextBytes)
        val hex = buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt()
                append(HEX[(value shr 4) and 0x0F])
                append(HEX[value and 0x0F])
            }
        }
        return "$hex-${suffixFor(manufacturer, modelName, hardware)}"
    }

    private fun javaStringHashCode(value: String): Int {
        var hash = 0
        for (char in value) {
            hash = 31 * hash + char.code
        }
        return hash
    }

    private const val HEX = "0123456789abcdef"
}
