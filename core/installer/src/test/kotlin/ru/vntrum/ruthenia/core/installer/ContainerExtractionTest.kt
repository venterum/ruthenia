/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContainerExtractionTest {

    @Test
    fun `extracts the apk entry from a rustore container`() {
        val directory = tempDirectory()
        val container = File(directory, "container.zip")
        val payload = "not a real apk, but the bytes must survive".toByteArray()

        ZipOutputStream(container.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("SPLIT_eef97dd7-04cb-4a78-97b5-45d92a8f991a.apk"))
            zip.write(payload)
            zip.closeEntry()
        }

        val target = File(directory, "package_0.apk")
        val result = extractSingleApk(container, target, "https://example.test/container.zip")

        assertArrayEquals(payload, result.readBytes())
        assertFalse("контейнер должен быть удалён", container.exists())
    }

    /**
     * If the link points to the APK itself, it must not be "unpacked": the first
     * entry there is AndroidManifest.xml, and previously that was exactly what would
     * end up in the installer as the APK.
     */
    @Test(expected = InstallException.ContainerHasNoApk::class)
    fun `rejects a container that has no apk entry`() {
        val directory = tempDirectory()
        val container = File(directory, "container.zip")

        ZipOutputStream(container.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("binary xml".toByteArray())
            zip.closeEntry()
        }

        extractSingleApk(
            container,
            File(directory, "package_0.apk"),
            "https://example.test/container.zip",
        )
    }

    private fun tempDirectory(): File =
        Files.createTempDirectory("ruthenia-installer-test").toFile().apply { deleteOnExit() }
}
