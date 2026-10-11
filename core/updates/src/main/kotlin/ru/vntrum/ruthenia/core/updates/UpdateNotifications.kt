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
import com.vntrum.ruthenia.core.data.model.InstalledApp

/**
 * Update notifications.
 *
 * Instead of a separate notification for each app — one group: a summary plus a
 * limited number of child notifications, so as not to flood the shade.
 */
@Singleton
class UpdateNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun showUpdatesAvailable(apps: List<InstalledApp>) {
        if (apps.isEmpty()) return
        ensureChannel()

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        // On Android 13+ notifications need the POST_NOTIFICATIONS runtime
        // permission: we check explicitly rather than relying only on
        // areNotificationsEnabled().
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val names = apps.map { it.name ?: it.packageName }
        val children = apps.take(MAX_CHILD_NOTIFICATIONS)

        children.forEachIndexed { index, app ->
            manager.notify(
                CHILD_NOTIFICATION_ID_BASE + index,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_update)
                    .setContentTitle(app.name ?: app.packageName)
                    .setContentText(
                        versionTransition(
                            installed = app.installedVersionName,
                            latest = app.latestVersionName,
                        ),
                    )
                    .setGroup(GROUP_KEY)
                    .setContentIntent(contentIntent())
                    .setAutoCancel(true)
                    .build(),
            )
        }

        manager.notify(
            SUMMARY_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_update)
                .setContentTitle(
                    context.resources.getQuantityString(
                        R.plurals.updates_available_title,
                        apps.size,
                        apps.size,
                    ),
                )
                .setContentText(summary(names))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(summary(names)),
                )
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setContentIntent(contentIntent())
                .setAutoCancel(true)
                .build(),
        )
    }

    fun clear() {
        val manager = NotificationManagerCompat.from(context)
        runCatching {
            (0 until MAX_CHILD_NOTIFICATIONS).forEach(manager::cancel)
            manager.cancel(SUMMARY_NOTIFICATION_ID)
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.updates_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.updates_channel_description)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun summary(names: List<String>): String =
        UpdateNotificationText.summary(names, context.getString(R.string.notification_update_more))

    private fun versionTransition(installed: String?, latest: String?): String =
        UpdateNotificationText.versionTransition(
            installed = installed,
            latest = latest,
            arrowTemplate = context.getString(R.string.notification_update_arrow),
            newVersionTemplate = context.getString(R.string.notification_update_new_version),
            availableText = context.getString(R.string.notification_update_available),
        )

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent().setPackage(context.packageName)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val CHANNEL_ID = "updates"
        const val GROUP_KEY = "com.vntrum.ruthenia.UPDATES"
        const val SUMMARY_NOTIFICATION_ID = 1000
        const val CHILD_NOTIFICATION_ID_BASE = 1001
        const val MAX_CHILD_NOTIFICATIONS = 5
    }
}

/**
 * The notification text logic is separated out for tests; localized templates
 * are passed in from outside (from resources).
 */
object UpdateNotificationText {

    fun summary(names: List<String>, moreTemplate: String): String = when {
        names.isEmpty() -> ""
        names.size <= MAX_NAMES_IN_SUMMARY -> names.joinToString(", ")
        else -> {
            val shown = names.take(MAX_NAMES_IN_SUMMARY).joinToString(", ")
            val more = String.format(moreTemplate, names.size - MAX_NAMES_IN_SUMMARY)
            "$shown $more"
        }
    }

    fun versionTransition(
        installed: String?,
        latest: String?,
        arrowTemplate: String,
        newVersionTemplate: String,
        availableText: String,
    ): String = when {
        installed != null && latest != null -> String.format(arrowTemplate, installed, latest)
        latest != null -> String.format(newVersionTemplate, latest)
        else -> availableText
    }

    private const val MAX_NAMES_IN_SUMMARY = 3
}
