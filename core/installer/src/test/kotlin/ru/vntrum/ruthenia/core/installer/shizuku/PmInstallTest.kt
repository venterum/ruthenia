/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PmInstallTest {

    @Test
    fun `create session replaces the app and preallocates the size`() {
        assertEquals(
            listOf("pm", "install-create", "-r", "-S", "12345"),
            PmInstall.createSession(12345),
        )
    }

    @Test
    fun `splits are streamed through stdin`() {
        assertEquals(
            listOf("pm", "install-write", "-S", "42", "777", "base.apk", "-"),
            PmInstall.writeSplit(sessionId = "777", name = "base.apk", size = 42),
        )
    }

    @Test
    fun `commit and abandon target the session`() {
        assertEquals(listOf("pm", "install-commit", "777"), PmInstall.commitSession("777"))
        assertEquals(listOf("pm", "install-abandon", "777"), PmInstall.abandonSession("777"))
    }

    @Test
    fun `session id is parsed from pm output`() {
        assertEquals(
            "1234567890",
            PmInstall.parseSessionId("Success: created install session [1234567890]"),
        )
    }

    @Test
    fun `session id is null when pm fails`() {
        assertNull(PmInstall.parseSessionId("Failure [INSTALL_FAILED_ALREADY_EXISTS]"))
    }
}
