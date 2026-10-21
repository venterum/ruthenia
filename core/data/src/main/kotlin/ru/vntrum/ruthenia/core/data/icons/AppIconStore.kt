/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.icons

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local store of app icons.
 *
 * GitHub releases have no app icon — only the repository owner's avatar, which
 * is not the app's brand. The real launcher icon is read from the downloaded
 * APK (before install) or from the installed package, saved as a PNG and
 * handed out as a `file://` URI that Coil loads like any other image.
 *
 * The version code in the file name busts the image cache when the icon
 * changes with an update; stale files of the same package are removed.
 */
@Singleton
class AppIconStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dir: File by lazy { File(context.filesDir, DIRECTORY) }

    /** Saves the icon of an installed app; null when it cannot be read. */
    suspend fun fromInstalledApp(packageName: String, versionCode: Long): String? =
        withContext(Dispatchers.IO) {
            val drawable = runCatching {
                context.packageManager.getApplicationIcon(packageName)
            }.getOrNull() ?: return@withContext null
            save(drawable, packageName, versionCode)
        }

    /**
     * Saves the launcher icon read from a downloaded APK; null when it cannot
     * be read. Only the base archive carries the icon, so split APKs are
     * skipped (a split's manifest has no application resources of its own).
     */
    suspend fun fromApk(apkFiles: List<File>, packageName: String, versionCode: Long): String? =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val parsed = apkFiles.mapNotNull { file ->
                runCatching { archiveInfo(packageManager, file) }
                    .getOrNull()
                    ?.let { info -> file to info }
            }
            val (apkFile, info) = parsed.firstOrNull { (_, info) ->
                info.splitNames.isNullOrEmpty()
            } ?: parsed.firstOrNull() ?: return@withContext null

            val applicationInfo = info.applicationInfo ?: return@withContext null
            applicationInfo.sourceDir = apkFile.absolutePath
            applicationInfo.publicSourceDir = apkFile.absolutePath
            val drawable = runCatching {
                packageManager.getApplicationIcon(applicationInfo)
            }.getOrNull() ?: return@withContext null
            save(drawable, packageName, versionCode)
        }

    /** Whether [iconUrl] points at an existing local icon file. */
    fun isLocal(iconUrl: String?): Boolean {
        if (iconUrl == null || !iconUrl.startsWith(FILE_SCHEME)) return false
        val path = runCatching { Uri.parse(iconUrl).path }.getOrNull() ?: return false
        return File(path).exists()
    }

    private fun archiveInfo(packageManager: PackageManager, file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(file.absolutePath, PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        }

    private fun save(drawable: Drawable, packageName: String, versionCode: Long): String? =
        runCatching {
            dir.mkdirs()
            val prefix = "$packageName-"
            dir.listFiles { file -> file.name.startsWith(prefix) }
                ?.forEach { it.delete() }
            val target = File(dir, "$prefix$versionCode.png")
            target.outputStream().use { out ->
                drawable.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Uri.fromFile(target).toString()
        }.getOrNull()

    private fun Drawable.toBitmap(): Bitmap {
        val width = intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = intrinsicHeight.takeIf { it > 0 } ?: 1
        val scale = minOf(1f, MAX_SIZE_PX.toFloat() / maxOf(width, height))
        val targetWidth = maxOf(1, (width * scale).roundToInt())
        val targetHeight = maxOf(1, (height * scale).roundToInt())
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, targetWidth, targetHeight)
        draw(canvas)
        return bitmap
    }

    private companion object {
        const val DIRECTORY = "app-icons"
        const val MAX_SIZE_PX = 192
        const val FILE_SCHEME = "file://"
    }
}
