/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer.shizuku

import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Shizuku user service: a separate process with the shell (UID 2000) or root
 * identity, where `pm` can install packages without the system confirmation
 * dialog.
 *
 * APKs arrive as binder file descriptors: the shell process cannot read the
 * app's private storage, and a passed descriptor works regardless of the UID.
 * The service only streams bytes into `pm` — it never looks at file paths.
 *
 * The class is instantiated by Shizuku by name, so R8 keep rules travel with
 * this module (see consumer-rules.pro).
 */
class ShizukuInstallService : IInstallService.Stub() {

    override fun destroy() {
        System.exit(0)
    }

    override fun install(apks: Array<ParcelFileDescriptor>?, packageName: String?): String {
        if (apks.isNullOrEmpty()) return "No APK files to install"
        return try {
            installViaPm(apks)
        } catch (e: Exception) {
            "Shizuku install failed: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            apks.forEach { runCatching { it.close() } }
        }
    }

    private fun installViaPm(apks: Array<ParcelFileDescriptor>): String {
        val totalBytes = apks.sumOf { it.statSize.coerceAtLeast(0L) }
        val create = run(PmInstall.createSession(totalBytes))
        if (!create.succeeded()) return create.error("pm install-create")

        val sessionId = PmInstall.parseSessionId(create.output)
            ?: return "Could not parse the install session id from: ${create.output.trim()}"

        for ((index, descriptor) in apks.withIndex()) {
            val name = if (index == 0) BASE_APK else "split_$index.apk"
            val write = run(PmInstall.writeSplit(sessionId, name, descriptor.statSize)) { stdin ->
                FileInputStream(descriptor.fileDescriptor).use { it.copyTo(stdin) }
            }
            if (!write.succeeded()) {
                runCatching { run(PmInstall.abandonSession(sessionId)) }
                return write.error("pm install-write ($name)")
            }
        }

        val commit = run(PmInstall.commitSession(sessionId))
        return if (commit.succeeded()) "" else commit.error("pm install-commit")
    }

    /** Runs a `pm` command, optionally streaming data into its stdin. */
    private fun run(
        command: List<String>,
        writeStdin: ((OutputStream) -> Unit)? = null,
    ): PmResult {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        try {
            if (writeStdin != null) {
                process.outputStream.use(writeStdin)
            } else {
                process.outputStream.close()
            }
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(COMMAND_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                return PmResult(exitCode = -1, output = "timed out after $COMMAND_TIMEOUT_MINUTES min")
            }
            return PmResult(process.exitValue(), output)
        } finally {
            process.destroy()
        }
    }

    private class PmResult(val exitCode: Int, val output: String) {
        fun succeeded(): Boolean = exitCode == 0 && !output.contains("Failure")

        fun error(step: String): String =
            "$step failed (exit $exitCode): ${output.trim().ifEmpty { "no output" }}"
    }

    private companion object {
        const val BASE_APK = "base.apk"
        const val COMMAND_TIMEOUT_MINUTES = 10L
    }
}
