/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer

import android.content.pm.PackageInstaller

/**
 * Maps `PackageInstaller` statuses and the raw `INSTALL_FAILED_*` text returned
 * by the system into stable codes. `detail` is kept only for diagnostics and is
 * bounded in size; download URLs are never part of it.
 */
object InstallFailureMapper {

    fun fromStatus(status: Int, systemMessage: String?): InstallFailure {
        val message = systemMessage?.trim()?.takeIf { it.isNotEmpty() }
        val code = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallFailureCode.USER_CANCELLED
            PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallFailureCode.BLOCKED
            PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallFailureCode.CONFLICT
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallFailureCode.INCOMPATIBLE
            PackageInstaller.STATUS_FAILURE_INVALID -> InstallFailureCode.INVALID_APK
            PackageInstaller.STATUS_FAILURE_STORAGE -> InstallFailureCode.STORAGE_FULL
            STATUS_FAILURE_TIMEOUT_COMPAT -> InstallFailureCode.TIMEOUT
            else -> fromMessage(message)
        }
        return InstallFailure(code = code, detail = message?.take(MAX_DETAIL_LENGTH))
    }

    fun fromMessage(message: String?): InstallFailureCode {
        val text = message.orEmpty().uppercase()
        return when {
            "INSTALL_FAILED_USER_RESTRICTED" in text ||
                "INSTALL_FAILED_BLOCKED" in text ||
                "INSTALL_FAILED_POLICY" in text ||
                "INSTALL_FAILED_VERIFICATION_FAILURE" in text ||
                "INSTALL_FAILED_ABORTED" in text -> InstallFailureCode.BLOCKED

            "INSTALL_FAILED_VERIFICATION_TIMEOUT" in text -> InstallFailureCode.TIMEOUT

            "INSTALL_FAILED_INSUFFICIENT_STORAGE" in text ||
                "INSTALL_FAILED_MEDIA_UNAVAILABLE" in text ||
                "INSTALL_FAILED_CONTAINER_ERROR" in text -> InstallFailureCode.STORAGE_FULL

            "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in text ||
                "INCONSISTENT_CERTIFICATES" in text ||
                "INSTALL_FAILED_ALREADY_EXISTS" in text ||
                "INSTALL_FAILED_DUPLICATE_PACKAGE" in text ||
                "INSTALL_FAILED_UID_CHANGED" in text ||
                "INSTALL_FAILED_PACKAGE_CHANGED" in text ||
                "INSTALL_FAILED_VERSION_DOWNGRADE" in text ||
                "INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE" in text ||
                "INSTALL_FAILED_SHARED_USER" in text ||
                "INSTALL_FAILED_CONFLICTING_PROVIDER" in text -> InstallFailureCode.CONFLICT

            "INSTALL_FAILED_INVALID_APK" in text ||
                "INSTALL_FAILED_INVALID_URI" in text ||
                "INSTALL_PARSE_FAILED" in text ||
                "INSTALL_FAILED_DEXOPT" in text ||
                "INSTALL_FAILED_TEST_ONLY" in text -> InstallFailureCode.INVALID_APK

            "INSTALL_FAILED_OLDER_SDK" in text ||
                "INSTALL_FAILED_NEWER_SDK" in text ||
                "INSTALL_FAILED_CPU_ABI_INCOMPATIBLE" in text ||
                "INSTALL_FAILED_NO_MATCHING_ABIS" in text ||
                "INSTALL_FAILED_MISSING_FEATURE" in text ||
                "INSTALL_FAILED_MISSING_SHARED_LIBRARY" in text ||
                "INSTALL_FAILED_INVALID_INSTALL_LOCATION" in text -> InstallFailureCode.INCOMPATIBLE

            "INSTALL_FAILED_SESSION_INVALID" in text -> InstallFailureCode.INTERRUPTED

            else -> InstallFailureCode.UNKNOWN
        }
    }

    private const val MAX_DETAIL_LENGTH = 300

    /** Added in API 35; the numeric value is stable. Referencing the field
     * directly would crash on older devices. */
    private const val STATUS_FAILURE_TIMEOUT_COMPAT = 8
}
