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

/**
 * Typed reasons why an install could not be completed.
 *
 * The codes are persisted and translated in the UI layer: a raw
 * `PackageInstaller` message is only kept as sanitized [InstallFailure.detail].
 */
enum class InstallFailureCode {
    /** The user dismissed the system install dialog. */
    USER_CANCELLED,

    /** Installing apps from this source is not allowed in system settings. */
    PERMISSION_REQUIRED,

    /** Device policy, Play Protect or another system restriction blocked it. */
    BLOCKED,

    /** The installed package conflicts with the downloaded one (signature/version). */
    CONFLICT,

    /** The downloaded package is corrupt or not a valid APK. */
    INVALID_APK,

    /** Not enough free space to install. */
    STORAGE_FULL,

    /** The package requires a newer Android version, ABI or feature. */
    INCOMPATIBLE,

    /** The installer did not report a result in time. */
    TIMEOUT,

    /** Shizuku was requested but is not available; the system installer should be used. */
    SHIZUKU_UNAVAILABLE,

    /** The app process or install session was interrupted. */
    INTERRUPTED,

    /** Creating/writing the PackageInstaller session failed. */
    SESSION_FAILED,

    /** Anything not covered above. */
    UNKNOWN,
}

/** A typed install failure. [detail] never contains signed download URLs. */
data class InstallFailure(
    val code: InstallFailureCode,
    val detail: String? = null,
)

/** Final result of the install pipeline. */
sealed interface InstallResult {

    /** Installation completed. */
    data object Success : InstallResult

    data class Failure(val failure: InstallFailure) : InstallResult
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

    /** The downloaded file is not a readable APK/ZIP archive. */
    class InvalidApk(val fileName: String) :
        InstallException("Invalid APK file: $fileName")

    /** The catalog returned a package for a different application. */
    class PackageMismatch(val expected: String, val actual: String?) :
        InstallException("Expected package $expected, got ${actual ?: "unreadable"}")

    /** The downloaded version is older than the requested one. */
    class VersionMismatch(val expected: Long?, val actual: Long?) :
        InstallException("Expected version $expected, got ${actual ?: "unreadable"}")

    /** The app cannot run on this device. */
    class Incompatible(val reason: String) : InstallException(reason)

    /** The download needs more free space than is available. */
    class StorageFull(val required: Long, val available: Long) :
        InstallException("Not enough storage: need $required bytes, have $available")
}
