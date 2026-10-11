/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Verifies the signature of the downloaded APK against the signature of the installed app.
 *
 * Why: an update can only be installed over the same build. If RuStore
 * has an app with the same package name but signed with a different key
 * (for example, a version from another store), the installation will either fail or
 * replace the app. So before starting the installer we compare the signature
 * fingerprints and refuse to install if they differ.
 */
@Singleton
class ApkSignatureVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

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
        val packageManager = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        } ?: return null

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
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        }
        info?.let { PackageInfoCompat.getLongVersionCode(it) }
    } catch (_: Exception) {
        null
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
