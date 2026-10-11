/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import com.vntrum.ruthenia.core.security.RuStoreHttpClient

/**
 * Downloading of APKs and config splits.
 *
 * RuStore serves the file as a `.zip` container, with a ready `.apk` next to it
 * (verified with a probe). So we try `.apk` first, and if it is missing —
 * download the container and unpack the single APK inside.
 *
 * An interrupted download is **resumed**, not restarted: the partial file
 * stays on disk, and on retry only the missing tail is requested
 * (`Range: bytes=<offset>-`). A marker with the source URL is stored next to the file —
 * it lets us verify that we are resuming the same resource and version.
 */
@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: RuStoreHttpClient,
) {

    /**
     * Downloads all files of the package on [Dispatchers.IO]: both reading the response body
     * and writing to disk are blocking operations, and the calling code lives on the main
     * thread.
     */
    suspend fun download(
        packageName: String,
        sources: List<ApkSource>,
        onProgress: (DownloadProgress) -> Unit = {},
    ): List<File> = withContext(Dispatchers.IO) {
        require(sources.isNotEmpty()) { "No APK sources provided" }

        val directory = File(context.cacheDir, "$DOWNLOAD_DIR/$packageName").apply { mkdirs() }
        val totalBytes = sources.sumOf { it.size ?: 0L }
        // Bytes already downloaded for resumed files, so the progress does not "jump".
        var downloadedBytes = sources.mapIndexed { index, source ->
            resumedBytes(directory, index, source.url)
        }.sum()

        sources.mapIndexed { index, source ->
            downloadOne(directory, index, source) { bytes ->
                downloadedBytes += bytes
                onProgress(DownloadProgress(downloadedBytes, totalBytes))
            }
        }
    }

    /** Deletes temporary files after installation. */
    suspend fun clear(packageName: String) = withContext(Dispatchers.IO) {
        runCatching { File(context.cacheDir, "$DOWNLOAD_DIR/$packageName").deleteRecursively() }
        Unit
    }

    /**
     * Already downloaded package files in write order (base APK and config splits).
     *
     * Needed when the download happened in the background and the user starts the installation
     * later: there is no need to download the files again. An empty list means there are no files
     * (not downloaded yet or the cache was cleared by the system).
     */
    fun downloadedFiles(packageName: String): List<File> {
        val directory = File(context.cacheDir, "$DOWNLOAD_DIR/$packageName")
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(FILE_PREFIX) && it.name.endsWith(FILE_SUFFIX) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    private suspend fun downloadOne(
        directory: File,
        index: Int,
        source: ApkSource,
        onBytes: (Long) -> Unit,
    ): File {
        val apkFile = File(directory, "$FILE_PREFIX$index$FILE_SUFFIX")
        val directApkUrl = source.url.toApkUrl()

        if (directApkUrl != source.url) {
            when (downloadToFile(apkFile, directApkUrl, onBytes)) {
                null -> return apkFile
                else -> apkFile.forget()
            }
        }

        // Fallback: a .zip container with a single APK inside.
        val container = File(directory, "container_$index.zip")
        val code = downloadToFile(container, source.url, onBytes)
        if (code == null) {
            // The catalog provided an explicit size for this URL — verify it before unpacking.
            container.requireSize(source.size, source.url)
            return extractSingleApk(container, apkFile, source.url)
        }
        container.forget()
        throw InstallException.DownloadFailed(source.url, code)
    }

    /**
     * Downloads [url] to [target], resuming the tail if the file is already partially there.
     *
     * @return `null` on success, otherwise the HTTP response code.
     */
    private suspend fun downloadToFile(
        target: File,
        url: String,
        onBytes: (Long) -> Unit,
    ): Int? {
        val marker = File(target.parentFile, target.name + MARKER_SUFFIX)
        val offset = if (target.length() > 0 && marker.readTextOrNull() == url) {
            target.length()
        } else {
            target.forget()
            marker.forget()
            0L
        }

        val request = Request.Builder().url(url).apply {
            if (offset > 0) header("Range", "bytes=$offset-")
        }.build()

        return client.execute(request).use { response ->
            if (!response.isSuccessful) return@use response.code

            // 206 — resume accepted; 200 — the server sent the whole file.
            val append = offset > 0 && response.code == HTTP_PARTIAL_CONTENT
            if (!append) target.delete()
            marker.writeText(url)
            response.writeTo(target, append, onBytes)
            marker.forget()
            null
        }
    }

    /** How many bytes of the file can be skipped on re-download (when the source matches). */
    private fun resumedBytes(directory: File, index: Int, sourceUrl: String): Long {
        val apk = File(directory, "$FILE_PREFIX$index$FILE_SUFFIX")
        val direct = sourceUrl.toApkUrl()
        return if (direct != sourceUrl &&
            apk.length() > 0 &&
            File(apk.parentFile, apk.name + MARKER_SUFFIX).readTextOrNull() == direct
        ) {
            apk.length()
        } else {
            0L
        }
    }

    private fun Response.writeTo(target: File, append: Boolean, onBytes: (Long) -> Unit) {
        try {
            body.byteStream().use { input ->
                FileOutputStream(target, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        onBytes(read.toLong())
                    }
                }
            }
        } catch (e: IOException) {
            // We intentionally do not delete the file: the next attempt will resume the tail.
            throw InstallException.SessionFailed("Download interrupted: ${e.message}")
        }
    }

    private companion object {
        const val DOWNLOAD_DIR = "apk"
        const val FILE_PREFIX = "package_"
        const val FILE_SUFFIX = ".apk"
        const val MARKER_SUFFIX = ".src"
        const val HTTP_PARTIAL_CONTENT = 206
    }
}

private fun File.readTextOrNull(): String? =
    if (isFile) runCatching { readText() }.getOrNull() else null

private fun File.forget() {
    runCatching { delete() }
}

/**
 * Extracts the APK from the RuStore `.zip` container.
 *
 * The container holds exactly one APK (for example `SPLIT_<uuid>.apk`). Filtering by
 * extension is essential: if the link unexpectedly points to the APK itself, it must not be
 * "unpacked" and have `AndroidManifest.xml` pulled out as the result.
 */
internal fun extractSingleApk(container: File, target: File, url: String): File {
    ZipFile(container).use { zip ->
        val entry = zip.entries().asSequence()
            .firstOrNull { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
            ?: throw InstallException.ContainerHasNoApk(url)

        zip.getInputStream(entry).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }
    container.forget()
    return target
}

/** RuStore serves a `.zip` container, while the ready APK sits next to it with the same name. */
internal fun String.toApkUrl(): String =
    if (endsWith(".zip", ignoreCase = true)) substring(0, length - 4) + ".apk" else this

/**
 * Rejects a file whose size does not match the one declared by the catalog.
 *
 * The check is needed only where exactly the URL for which the size was received
 * is downloaded: the `.zip` container and its neighboring `.apk` have different sizes.
 */
internal fun File.requireSize(expected: Long?, sourceUrl: String) {
    if (expected == null || expected <= 0L) return
    if (length() != expected) {
        throw InstallException.IntegrityMismatch(sourceUrl, expected, length())
    }
}
