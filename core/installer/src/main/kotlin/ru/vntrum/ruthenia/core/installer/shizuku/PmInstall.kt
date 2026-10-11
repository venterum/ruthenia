/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer.shizuku

/**
 * Builds `pm` command lines for a silent split install. Pure so it can be
 * unit-tested on the JVM; execution lives in [ShizukuInstallService], which
 * runs with the shell identity.
 *
 * The flow mirrors `adb install-multiple`: create a session, stream every split
 * into it, then commit.
 */
internal object PmInstall {

    /** `pm install-create -r -S <total>`: a replace session of the given size. */
    fun createSession(totalBytes: Long): List<String> =
        listOf("pm", "install-create", "-r", "-S", totalBytes.toString())

    /** `pm install-write -S <size> <session> <name> -`: the split is streamed via stdin. */
    fun writeSplit(sessionId: String, name: String, size: Long): List<String> =
        listOf("pm", "install-write", "-S", size.toString(), sessionId, name, "-")

    fun commitSession(sessionId: String): List<String> =
        listOf("pm", "install-commit", sessionId)

    fun abandonSession(sessionId: String): List<String> =
        listOf("pm", "install-abandon", sessionId)

    /** Extracts the session id from `Success: created install session [1234]`. */
    fun parseSessionId(output: String): String? =
        SESSION_ID.find(output)?.groupValues?.get(1)

    private val SESSION_ID = Regex("""created install session \[(\d+)]""")
}
