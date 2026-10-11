/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer

/** A single file link for installation. */
data class ApkSource(
    val url: String,
    val size: Long?,
)

/** Final result of the `PackageInstaller` install session. */
sealed interface InstallResult {

    /** Installation completed. */
    data object Success : InstallResult

    data class Failure(val message: String) : InstallResult
}

/** Package download progress. */
data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
) {
    /** `null` if the total size is unknown — then the indicator is indeterminate. */
    val fraction: Float?
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

sealed class InstallException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class DownloadFailed(val url: String, val code: Int) :
        InstallException("Failed to download $url (HTTP $code)")

    class ContainerHasNoApk(val url: String) :
        InstallException("Archive $url contains no APK")

    class SessionFailed(message: String) : InstallException(message)

    /**
     * The size of the downloaded file does not match the size from the catalog: the CDN
     * response was truncated or tampered with. Such a file is not allowed before installation.
     */
    class IntegrityMismatch(val url: String, val expected: Long, val actual: Long) :
        InstallException("Size mismatch for $url: expected $expected bytes, got $actual")
}
