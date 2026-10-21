/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType
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

        // A replacement worker may still be finishing a blocking write while
        // WorkManager has already started the next one. Serialize per package.
        packageLock(packageName).withLock {
            val directory = File(context.cacheDir, "$DOWNLOAD_DIR/$packageName").apply { mkdirs() }
            val totalBytes = sources.sumOf { it.size ?: 0L }
            // Bytes already downloaded for resumed files, so the progress does not "jump".
            var downloadedBytes = sources.mapIndexed { index, source ->
                resumedBytes(directory, index, source.url)
            }.sum()

            requireStorage(totalBytes = totalBytes, alreadyDownloaded = downloadedBytes)

            sources.mapIndexed { index, source ->
                downloadOne(directory, index, source) { bytes ->
                    downloadedBytes += bytes
                    onProgress(DownloadProgress(downloadedBytes, totalBytes))
                }
            }
        }
    }

    /** Total bytes occupied by cached APK files. */
    fun cacheSizeBytes(): Long {
        val root = File(context.cacheDir, DOWNLOAD_DIR)
        if (!root.isDirectory) return 0L
        return root.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /** Cached bytes for one package. */
    fun cacheSizeBytes(packageName: String): Long {
        val directory = File(context.cacheDir, "$DOWNLOAD_DIR/$packageName")
        if (!directory.isDirectory) return 0L
        return directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Whether a complete download is still present on disk. */
    fun hasFiles(packageName: String): Boolean = downloadedFiles(packageName).isNotEmpty()

    /** Removes cache directories that do not belong to a live task. */
    suspend fun cleanupOrphans(knownPackages: Set<String>) = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir, DOWNLOAD_DIR)
        if (!root.isDirectory) return@withContext
        root.listFiles()?.forEach { directory ->
            if (directory.isDirectory && directory.name !in knownPackages) {
                runCatching { directory.deleteRecursively() }
            }
        }
    }

    /**
     * Moves a completed download from a temporary source key to the real
     * package directory. Used by the GitHub first-install resolver, where the
     * package name is unknown until the APK has been inspected.
     */
    suspend fun rekey(fromKey: String, toPackageName: String) = withContext(Dispatchers.IO) {
        if (fromKey == toPackageName) return@withContext
        val first = if (fromKey < toPackageName) fromKey else toPackageName
        val second = if (fromKey < toPackageName) toPackageName else fromKey
        packageLock(first).withLock {
            packageLock(second).withLock {
                val from = File(context.cacheDir, "$DOWNLOAD_DIR/$fromKey")
                if (!from.isDirectory) return@withLock
                val to = File(context.cacheDir, "$DOWNLOAD_DIR/$toPackageName")
                to.deleteRecursively()
                to.parentFile?.mkdirs()
                if (!from.renameTo(to)) {
                    from.copyRecursively(to, overwrite = true)
                    from.deleteRecursively()
                }
            }
        }
        Unit
    }

    /** Deletes temporary files after installation. */
    suspend fun clear(packageName: String) = withContext(Dispatchers.IO) {
        packageLock(packageName).withLock {
            runCatching { File(context.cacheDir, "$DOWNLOAD_DIR/$packageName").deleteRecursively() }
        }
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

        if (!source.url.endsWith(".zip", ignoreCase = true)) {
            // The link already points at an APK.
            val code = downloadToFile(apkFile, source.url, source.headers, onBytes)
            if (code != null) {
                apkFile.forget()
                throw InstallException.DownloadFailed(source.url, code)
            }
            apkFile.requireSize(source.size, source.url)
            apkFile.requireZipHeader(source.url)
            apkFile.requireSha256(source)
            return apkFile
        }

        // RuStore usually serves a .zip container with a ready .apk next to it.
        val directApkUrl = source.url.toApkUrl()
        val directValid = try {
            val directCode = downloadToFile(apkFile, directApkUrl, source.headers, onBytes)
            directCode == null && runCatching { apkFile.requireZipHeader(directApkUrl) }.isSuccess
        } catch (_: InstallException.InvalidApk) {
            // An HTML error page can be served with HTTP 200; try the container.
            false
        }
        if (directValid) return apkFile
        apkFile.forget()

        // Fallback: download the container and unpack its single APK.
        val container = File(directory, "container_$index.zip")
        val code = downloadToFile(container, source.url, source.headers, onBytes)
        if (code == null) {
            // The source provided an explicit size/digest for this URL — verify it before unpacking.
            container.requireSize(source.size, source.url)
            container.requireZipHeader(source.url)
            container.requireSha256(source)
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
        headers: Map<String, String>,
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
            headers.forEach { (name, value) -> header(name, value) }
            if (offset > 0) header("Range", "bytes=$offset-")
        }.build()

        return client.execute(request).use { response ->
            if (response.code == HTTP_RANGE_NOT_SATISFIABLE && offset > 0) {
                // The partial file no longer matches the resource. Start over on
                // the next attempt rather than retrying the same bad range.
                target.forget()
                marker.forget()
                throw InstallException.SessionFailed("Range not satisfiable; restart required")
            }
            if (!response.isSuccessful) return@use response.code

            val partial = response.code == HTTP_PARTIAL_CONTENT
            if (partial && offset > 0) {
                val start = response.header("Content-Range")
                    ?.substringAfter("bytes ", "")
                    ?.substringBefore('-')
                    ?.trim()
                    ?.toLongOrNull()
                if (start != offset) {
                    target.forget()
                    marker.forget()
                    throw InstallException.SessionFailed("Unexpected Content-Range")
                }
            }

            if (response.isHtml()) {
                throw InstallException.InvalidApk(url)
            }

            // 206 — resume accepted; 200 — the server sent the whole file.
            val append = offset > 0 && partial
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
        val expectedUrl = if (sourceUrl.endsWith(".zip", ignoreCase = true)) {
            sourceUrl.toApkUrl()
        } else {
            sourceUrl
        }
        return if (apk.length() > 0 &&
            File(apk.parentFile, apk.name + MARKER_SUFFIX).readTextOrNull() == expectedUrl
        ) {
            apk.length()
        } else {
            0L
        }
    }

    private fun requireStorage(totalBytes: Long, alreadyDownloaded: Long) {
        if (totalBytes <= 0L) return
        val remaining = (totalBytes - alreadyDownloaded).coerceAtLeast(0L)
        val available = StatFs(context.cacheDir.path).availableBytes
        if (available < remaining + CACHE_MARGIN_BYTES) {
            throw InstallException.StorageFull(
                required = remaining + CACHE_MARGIN_BYTES,
                available = available,
            )
        }
    }

    private suspend fun Response.writeTo(target: File, append: Boolean, onBytes: (Long) -> Unit) {
        try {
            body.byteStream().use { input ->
                FileOutputStream(target, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        // WorkManager cancels the coroutine when the user pauses
                        // or replaces the download; stop at the next chunk so the
                        // per-package lock is released promptly.
                        currentCoroutineContext().ensureActive()
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

    private fun Response.isHtml(): Boolean {
        val type: MediaType? = body.contentType()
        val subtype = type?.subtype?.lowercase()
        return subtype == "html" || subtype == "xml"
    }

    private fun packageLock(packageName: String): Mutex =
        locks.getOrPut(packageName) { Mutex() }

    private companion object {
        val locks = ConcurrentHashMap<String, Mutex>()
        const val DOWNLOAD_DIR = "apk"
        const val FILE_PREFIX = "package_"
        const val FILE_SUFFIX = ".apk"
        const val MARKER_SUFFIX = ".src"
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val CACHE_MARGIN_BYTES = 32L * 1024 * 1024
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

/**
 * Checks the ZIP local-file header before handing a file to the installer.
 * A CDN error page served with HTTP 200 does not look like an APK at all.
 */
/**
 * Verifies an optional SHA-256 digest from the source (GitHub provides one).
 * The digest is computed in chunks so large APKs are not loaded into memory.
 */
internal fun File.requireSha256(source: ApkSource) {
    val expected = source.sha256?.lowercase() ?: return
    val actual = runCatching {
        java.security.MessageDigest.getInstance("SHA-256").apply {
            inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    update(buffer, 0, read)
                }
            }
        }.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    if (actual != expected) {
        throw InstallException.DigestMismatch(source.url, expected)
    }
}

internal fun File.requireZipHeader(sourceUrl: String) {
    val header = ByteArray(2)
    val read = runCatching {
        inputStream().use { input -> input.read(header) }
    }.getOrDefault(-1)
    if (read < 2 || header[0] != 'P'.code.toByte() || header[1] != 'K'.code.toByte()) {
        throw InstallException.InvalidApk(sourceUrl)
    }
}
