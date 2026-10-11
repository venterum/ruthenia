/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.network

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import okhttp3.Response

private val WINDOWS_1251: Charset = Charset.forName("windows-1251")

/**
 * Decoding of the RuStore response body.
 *
 * Responses usually arrive as `application/json` without a charset and in UTF-8,
 * but cp1251 responses have historically been seen. Order: explicit charset from
 * Content-Type → strict UTF-8 → windows-1251.
 */
internal fun Response.decodeText(): String {
    val bytes = body.bytes()
    val declared = body.contentType()?.charset()
    if (declared != null) {
        return String(bytes, declared)
    }
    return decodeText(bytes)
}

internal fun decodeText(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    String(bytes, WINDOWS_1251)
}
