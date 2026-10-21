/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of the full pre-install APK check. */
sealed interface ApkVerification {

    /** The package is structurally valid, matches the request and can be installed. */
    data object Valid : ApkVerification

    data class Failed(
        val code: ApkVerificationFailure,
        val detail: String? = null,
    ) : ApkVerification
}

/** Why a downloaded package cannot be handed to the installer. */
enum class ApkVerificationFailure {
    INVALID_APK,
    PACKAGE_MISMATCH,
    VERSION_MISMATCH,
    INCOMPATIBLE,
    SIGNATURE_MISMATCH,
}

/**
 * Verifies the downloaded APK against the request and the installed app.
 *
 * Why: an update can only be installed over the same build. If RuStore
 * has an app with the same package name but signed with a different key
 * (for example, a version from another store), the installation will either fail or
 * replace the app. We also catch corrupt archives, wrong packages, older
 * versions and incompatible minSdk before the user sees the installer.
 */
@Singleton
class ApkSignatureVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Full validation before the download is marked ready.
     *
     * @param expectedVersionCode target version from the download link/request.
     */
    suspend fun verifyPackage(
        packageName: String,
        expectedVersionCode: Long?,
        apkFiles: List<File>,
        verifySignature: Boolean = true,
    ): ApkVerification = withContext(Dispatchers.IO) {
        if (apkFiles.isEmpty()) {
            return@withContext ApkVerification.Failed(ApkVerificationFailure.INVALID_APK)
        }

        val base = baseArchive(apkFiles)
            ?: return@withContext ApkVerification.Failed(ApkVerificationFailure.INVALID_APK)

        if (base.packageName != packageName) {
            return@withContext ApkVerification.Failed(
                code = ApkVerificationFailure.PACKAGE_MISMATCH,
                detail = base.packageName,
            )
        }

        val versionCode = PackageInfoCompat.getLongVersionCode(base)
        if (expectedVersionCode != null && versionCode < expectedVersionCode) {
            return@withContext ApkVerification.Failed(
                code = ApkVerificationFailure.VERSION_MISMATCH,
                detail = "$versionCode < $expectedVersionCode",
            )
        }

        val minSdk = base.applicationInfo?.minSdkVersion ?: 0
        if (minSdk > Build.VERSION.SDK_INT) {
            return@withContext ApkVerification.Failed(
                code = ApkVerificationFailure.INCOMPATIBLE,
                detail = "minSdk $minSdk",
            )
        }

        // Every installable APK must carry a signing certificate. An unsigned
        // archive would only fail later inside PackageInstaller.
        if (apkFiles.none { apkSignatures(it).orEmpty().isNotEmpty() }) {
            return@withContext ApkVerification.Failed(ApkVerificationFailure.INVALID_APK)
        }

        if (!verifySignature) return@withContext ApkVerification.Valid

        when (val signature = verify(packageName, apkFiles)) {
            SignatureCheck.Match, is SignatureCheck.Unknown -> ApkVerification.Valid
            SignatureCheck.Mismatch -> ApkVerification.Failed(
                ApkVerificationFailure.SIGNATURE_MISMATCH,
            )
        }
    }

    /**
     * @return [SignatureCheck.Match] if the signatures match;
     *         [SignatureCheck.Mismatch] if these are different builds;
     *         [SignatureCheck.Unknown] if the comparison could not be made (for example,
     *         the app is not installed yet — then there is nothing to compare against).
     */
    suspend fun verify(
        packageName: String,
        apkFiles: List<File>,
    ): SignatureCheck = withContext(Dispatchers.IO) {
        val installed = installedSignatures(packageName)
            ?: return@withContext SignatureCheck.Unknown("Приложение ещё не установлено")

        val downloaded = apkFiles
            .mapNotNull { apkSignatures(it) }
            .flatten()

        if (downloaded.isEmpty()) {
            return@withContext SignatureCheck.Unknown("Не удалось прочитать подпись файла")
        }

        val match = installed.any { installedSignature ->
            downloaded.any { it.contentEquals(installedSignature) }
        }

        if (match) {
            SignatureCheck.Match
        } else {
            SignatureCheck.Mismatch
        }
    }

    private fun baseArchive(files: List<File>): PackageInfo? {
        val parsed = files.mapNotNull { file -> archiveInfo(file)?.let { file to it } }
        return parsed.firstOrNull { (_, info) -> info.splitNames.isNullOrEmpty() }?.second
            ?: parsed.firstOrNull()?.second
    }

    private fun archiveInfo(file: File): PackageInfo? = try {
        val packageManager = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        }
    } catch (_: Exception) {
        null
    }

    /** SHA-256 fingerprints of the installed app's signatures. */
    private fun installedSignatures(packageName: String): List<ByteArray>? = try {
        val packageManager = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }

        val signingInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo
        } else {
            null
        }

        if (signingInfo != null) {
            val signatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signatures.map { sha256(it.toByteArray()) }
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.map { sha256(it.toByteArray()) }
        }
    } catch (_: NameNotFoundException) {
        null
    } catch (_: SecurityException) {
        // GET_SIGNING_CERTIFICATES may be unavailable — in that case we do not block.
        null
    }

    /** SHA-256 fingerprints of the APK file's signatures. */
    private fun apkSignatures(file: File): List<ByteArray>? = try {
        val info = archiveInfo(file) ?: return null
        val signingInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo
        } else {
            null
        }

        if (signingInfo != null) {
            val signatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signatures.map { sha256(it.toByteArray()) }
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.map { sha256(it.toByteArray()) }
        }
    } catch (_: Exception) {
        null
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    /** Comparison of the downloaded APK and installed versions — for the error message. */
    fun downloadedVersionCode(file: File): Long? = try {
        archiveInfo(file)?.let { PackageInfoCompat.getLongVersionCode(it) }
    } catch (_: Exception) {
        null
    }

    /** Package identity, version and label read from an APK archive. */
    fun metadata(file: File): ApkMetadata? {
        return try {
            val info = archiveInfo(file) ?: return null
            val label = info.applicationInfo?.let { applicationInfo ->
                runCatching {
                    context.packageManager.getApplicationLabel(applicationInfo).toString()
                }.getOrNull()
            }
            ApkMetadata(
                packageName = info.packageName,
                versionCode = PackageInfoCompat.getLongVersionCode(info),
                versionName = info.versionName,
                label = label,
            )
        } catch (_: Exception) {
            null
        }
    }
}

/** Result of the signature comparison. */
sealed interface SignatureCheck {

    /** Signatures match: the update can be installed. */
    data object Match : SignatureCheck

    /** Signatures differ: this is a different build, installation is forbidden. */
    data object Mismatch : SignatureCheck

    /** Comparison failed — the reason is in [reason]. */
    data class Unknown(val reason: String) : SignatureCheck
}

/** Whether to install the package with this comparison result. */
val SignatureCheck.allowsInstall: Boolean
    get() = this !is SignatureCheck.Mismatch
