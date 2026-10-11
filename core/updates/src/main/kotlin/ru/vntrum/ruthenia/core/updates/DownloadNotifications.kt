/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifications for background update downloads.
 *
 * One notification per app: first it shows progress (important to be silent —
 * hence the low-importance channel), then it turns into "ready to install".
 * The install is still started by the user.
 */
@Singleton
class DownloadNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun showProgress(packageName: String, appName: String, fraction: Float?) {
        if (!canNotify()) return
        ensureChannel()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_update)
            .setContentTitle(appName)
            .setContentText(progressText(fraction))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .setProgress(
                MAX_PROGRESS,
                fraction?.let { (it * MAX_PROGRESS).toInt() } ?: 0,
                fraction == null,
            )

        notify(packageName, builder.build())
    }

    fun showReady(packageName: String, appName: String) {
        if (!canNotify()) return
        ensureChannel()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_update)
            .setContentTitle(appName)
            .setContentText(context.getString(R.string.notification_download_ready))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_download_ready)),
            )
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .addAction(
                0,
                context.getString(R.string.notification_install_action),
                installIntent(packageName),
            )

        notify(packageName, builder.build())
    }

    /** Done: the APK was saved to the selected folder. */
    fun showExportDone(packageName: String, appName: String) {
        if (!canNotify()) return
        ensureChannel()

        val text = context.getString(R.string.notification_export_done)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_update)
            .setContentTitle(appName)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())

        notify(packageName, builder.build())
    }

    fun clear(packageName: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(idFor(packageName)) }
    }

    private fun notify(packageName: String, notification: android.app.Notification) {
        runCatching {
            NotificationManagerCompat.from(context).notify(idFor(packageName), notification)
        }
    }

    private fun canNotify(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        // Android 13+: notifications require the POST_NOTIFICATIONS runtime permission.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.downloads_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.downloads_channel_description)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent().setPackage(context.packageName)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Intent for the "Install" action: opens the app, which starts the install
     * of the downloaded package right away based on [EXTRA_INSTALL_PACKAGE].
     */
    private fun installIntent(packageName: String): PendingIntent = PendingIntent.getActivity(
        context,
        packageName.hashCode(),
        Intent(ACTION_INSTALL_READY)
            .setPackage(context.packageName)
            .putExtra(EXTRA_INSTALL_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun progressText(fraction: Float?): String = DownloadNotificationText.progress(
        fraction = fraction,
        downloading = context.getString(R.string.notification_download_downloading),
        downloadedTemplate = context.getString(R.string.notification_download_downloaded),
    )

    private fun idFor(packageName: String): Int =
        NOTIFICATION_ID_BASE + (packageName.hashCode() and 0x7FFFFFFF) % ID_RANGE

    companion object {
        /** The "Install" action from the ready-to-install notification. */
        const val ACTION_INSTALL_READY = "com.vntrum.ruthenia.action.INSTALL_READY"

        /** Extra with the package name for which the install starts right away. */
        const val EXTRA_INSTALL_PACKAGE = "com.vntrum.ruthenia.extra.INSTALL_PACKAGE"

        private const val CHANNEL_ID = "downloads"
        private const val MAX_PROGRESS = 100
        private const val NOTIFICATION_ID_BASE = 10_000
        private const val ID_RANGE = 100_000
    }
}

/**
 * The progress text logic is separated out so it is easy to test.
 * Localized templates are passed in from outside (from resources).
 */
object DownloadNotificationText {

    fun progress(
        fraction: Float?,
        downloading: String,
        downloadedTemplate: String,
    ): String = when {
        fraction == null -> downloading
        else -> String.format(downloadedTemplate, (fraction * 100).toInt())
    }
}
