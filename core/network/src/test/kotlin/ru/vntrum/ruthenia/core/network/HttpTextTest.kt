/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.network

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpTextTest {

    @Test
    fun `decodes plain utf8`() {
        val text = "СберБанк Онлайн — обновление"
        assertEquals(text, decodeText(text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `falls back to windows-1251 when bytes are not valid utf8`() {
        val text = "СберБанк Онлайн — обновление"
        val cp1251 = text.toByteArray(Charset.forName("windows-1251"))

        // Cyrillic in cp1251 is not valid UTF-8, so the fallback
        // should kick in.
        assertEquals(text, decodeText(cp1251))
    }

    @Test
    fun `empty body decodes to empty string`() {
        assertEquals("", decodeText(ByteArray(0)))
    }
}
