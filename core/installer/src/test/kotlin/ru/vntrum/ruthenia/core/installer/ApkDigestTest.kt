/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer

import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Test

class ApkDigestTest {

    @Test
    fun `checks the source sha256 and rejects a mismatch`() {
        val file = File.createTempFile("ruthenia-digest", ".apk").apply {
            writeText("hello")
            deleteOnExit()
        }
        val valid = ApkSource(
            url = "https://example.com/app.apk",
            size = 5,
            sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
        )
        file.requireSha256(valid)

        val invalid = valid.copy(
            sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
        )
        assertThrows(InstallException.DigestMismatch::class.java) {
            file.requireSha256(invalid)
        }
    }
}
