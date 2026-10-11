/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IntegrityCheckTest {

    @Test
    fun `matching size passes`() {
        val file = tempFile("content".toByteArray())
        file.requireSize(expected = 7L, sourceUrl = TEST_URL)
    }

    @Test
    fun `unknown size passes`() {
        val file = tempFile("content".toByteArray())
        file.requireSize(expected = null, sourceUrl = TEST_URL)
        file.requireSize(expected = 0L, sourceUrl = TEST_URL)
    }

    @Test
    fun `truncated download is rejected`() {
        val file = tempFile("short".toByteArray())

        val error = assertThrows(InstallException.IntegrityMismatch::class.java) {
            file.requireSize(expected = 100L, sourceUrl = TEST_URL)
        }

        assertEquals(TEST_URL, error.url)
        assertEquals(100L, error.expected)
        assertEquals(5L, error.actual)
    }

    private fun tempFile(bytes: ByteArray): File =
        Files.createTempFile("ruthenia-integrity-test", ".bin").toFile().apply {
            deleteOnExit()
            writeBytes(bytes)
        }

    private companion object {
        const val TEST_URL = "https://example.test/base.zip"
    }
}