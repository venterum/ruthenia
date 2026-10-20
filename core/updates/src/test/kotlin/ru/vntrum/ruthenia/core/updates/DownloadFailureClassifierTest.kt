/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import java.io.IOException
import java.util.zip.ZipException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.vntrum.ruthenia.core.installer.InstallException
import com.vntrum.ruthenia.core.network.RuStoreException

class DownloadFailureClassifierTest {

    @Test
    fun `network and server failures are retryable`() {
        assertTrue(RuStoreException.Network(IOException()).toTaskFailure().isRetryable)
        assertTrue(RuStoreException.Http(500, "").toTaskFailure().isRetryable)
        assertTrue(RuStoreException.Http(429, "").toTaskFailure().isRetryable)
        assertTrue(RuStoreException.Http(419, "").toTaskFailure().isRetryable)
        assertTrue(IOException().toTaskFailure().isRetryable)
    }

    @Test
    fun `permanent failures are not retryable`() {
        assertFalse(RuStoreException.Http(404, "").toTaskFailure().isRetryable)
        assertFalse(InstallException.IntegrityMismatch("v", 10, 5).toTaskFailure().isRetryable)
        assertFalse(InstallException.ContainerHasNoApk("v").toTaskFailure().isRetryable)
        assertFalse(ZipException("broken").toTaskFailure().isRetryable)
    }

    @Test
    fun `corrupt packages offer a redownload`() {
        assertTrue(InstallException.IntegrityMismatch("v", 10, 5).toTaskFailure().needsRedownload)
        assertTrue(InstallException.InvalidApk("v").toTaskFailure().needsRedownload)
        assertTrue(InstallException.ContainerHasNoApk("v").toTaskFailure().needsRedownload)
        assertTrue(ZipException("broken").toTaskFailure().needsRedownload)
    }

    @Test
    fun `http codes map to specific user-facing meanings`() {
        assertEquals(
            TaskFailureCode.RATE_LIMITED,
            RuStoreException.Http(429, "").toTaskFailure().code,
        )
        assertEquals(
            TaskFailureCode.APP_UNAVAILABLE,
            RuStoreException.Http(404, "").toTaskFailure().code,
        )
        assertEquals(
            TaskFailureCode.SERVER,
            RuStoreException.Http(503, "").toTaskFailure().code,
        )
        assertEquals(
            TaskFailureCode.SESSION,
            RuStoreException.Http(419, "").toTaskFailure().code,
        )
    }

    @Test
    fun `storage failures keep a manual retry`() {
        val failure = InstallException.StorageFull(required = 100, available = 10).toTaskFailure()
        assertEquals(TaskFailureCode.STORAGE_FULL, failure.code)
        assertFalse(failure.isRetryable)
        assertFalse(failure.needsRedownload)
    }
}
