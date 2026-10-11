/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.model

/** App status in the library. */
enum class UpdateStatus {
    /** The installed version matches the latest known one. */
    UP_TO_DATE,

    /** A newer version is available. */
    UPDATE_AVAILABLE,

    /** Versions could not be compared (data missing on one side). */
    UNKNOWN,
}

/**
 * Version comparison.
 *
 * The main path is the numeric `versionCode` from RuStore against
 * `longVersionCode` from `PackageManager`. If the code is unavailable, we
 * compare `versionName` by segments (numeric segments as numbers, otherwise
 * lexicographically).
 */
object VersionComparator {

    fun status(
        installedVersionCode: Long?,
        installedVersionName: String?,
        latestVersionCode: Long?,
        latestVersionName: String?,
    ): UpdateStatus {
        if (installedVersionCode != null && latestVersionCode != null) {
            return if (latestVersionCode > installedVersionCode) {
                UpdateStatus.UPDATE_AVAILABLE
            } else {
                UpdateStatus.UP_TO_DATE
            }
        }
        if (installedVersionName != null && latestVersionName != null) {
            return if (compareNames(latestVersionName, installedVersionName) > 0) {
                UpdateStatus.UPDATE_AVAILABLE
            } else {
                UpdateStatus.UP_TO_DATE
            }
        }
        return UpdateStatus.UNKNOWN
    }

    /** `> 0` if [left] is newer than [right]. */
    fun compareNames(left: String, right: String): Int {
        val leftParts = left.split(SEPARATORS).filter { it.isNotEmpty() }
        val rightParts = right.split(SEPARATORS).filter { it.isNotEmpty() }

        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val leftPart = leftParts.getOrNull(index) ?: return -1
            val rightPart = rightParts.getOrNull(index) ?: return 1

            val leftNumber = leftPart.toLongOrNull()
            val rightNumber = rightPart.toLongOrNull()

            val result = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                else -> leftPart.compareTo(rightPart, ignoreCase = true)
            }
            if (result != 0) return result
        }
        return 0
    }

    private val SEPARATORS = Regex("[.\\-_+]")
}
