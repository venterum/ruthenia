/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallFailureMapperTest {

    @Test
    fun `statuses map to stable codes`() {
        assertEquals(
            InstallFailureCode.USER_CANCELLED,
            InstallFailureMapper.fromStatus(PackageInstaller.STATUS_FAILURE_ABORTED, null).code,
        )
        assertEquals(
            InstallFailureCode.BLOCKED,
            InstallFailureMapper.fromStatus(PackageInstaller.STATUS_FAILURE_BLOCKED, null).code,
        )
        assertEquals(
            InstallFailureCode.CONFLICT,
            InstallFailureMapper.fromStatus(PackageInstaller.STATUS_FAILURE_CONFLICT, null).code,
        )
        assertEquals(
            InstallFailureCode.INVALID_APK,
            InstallFailureMapper.fromStatus(PackageInstaller.STATUS_FAILURE_INVALID, null).code,
        )
        assertEquals(
            InstallFailureCode.STORAGE_FULL,
            InstallFailureMapper.fromStatus(PackageInstaller.STATUS_FAILURE_STORAGE, null).code,
        )
        assertEquals(
            InstallFailureCode.INCOMPATIBLE,
            InstallFailureMapper.fromStatus(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, null).code,
        )
    }

    @Test
    fun `legacy install failed messages are classified`() {
        assertEquals(
            InstallFailureCode.CONFLICT,
            InstallFailureMapper.fromMessage("INSTALL_FAILED_UPDATE_INCOMPATIBLE"),
        )
        assertEquals(
            InstallFailureCode.INVALID_APK,
            InstallFailureMapper.fromMessage("INSTALL_FAILED_INVALID_APK"),
        )
        assertEquals(
            InstallFailureCode.INCOMPATIBLE,
            InstallFailureMapper.fromMessage("INSTALL_FAILED_OLDER_SDK"),
        )
        assertEquals(
            InstallFailureCode.STORAGE_FULL,
            InstallFailureMapper.fromMessage("INSTALL_FAILED_INSUFFICIENT_STORAGE"),
        )
        assertEquals(
            InstallFailureCode.BLOCKED,
            InstallFailureMapper.fromMessage("INSTALL_FAILED_USER_RESTRICTED"),
        )
    }

    @Test
    fun `unknown details are bounded`() {
        val result = InstallFailureMapper.fromStatus(
            PackageInstaller.STATUS_FAILURE,
            "x".repeat(1000),
        )
        assertEquals(InstallFailureCode.UNKNOWN, result.code)
        assertEquals(300, result.detail?.length)
    }
}
