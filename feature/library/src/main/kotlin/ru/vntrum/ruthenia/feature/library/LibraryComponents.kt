/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.core.data.model.AppSource
import com.vntrum.ruthenia.core.data.model.InstalledApp
import com.vntrum.ruthenia.core.data.model.UpdateStatus
import com.vntrum.ruthenia.core.data.repository.ImportProgress
import com.vntrum.ruthenia.core.designsystem.component.AppRow
import com.vntrum.ruthenia.core.designsystem.component.RutheniaWavyProgress

/** Installed app card — shared by "My apps". */
@Composable
internal fun LibraryCard(
    app: InstalledApp,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onUpdate: (() -> Unit)? = null,
) {
    AppRow(
        title = app.name ?: app.packageName,
        subtitle = versionLine(app),
        iconUrl = app.iconUrl,
        iconFallback = painterResource(R.drawable.ic_library),
        iconModifier = iconModifier,
        onClick = onClick,
        modifier = modifier,
        trailing = {
            if (app.status == UpdateStatus.UPDATE_AVAILABLE && onUpdate != null) {
                FilledTonalButton(onClick = onUpdate) {
                    Text(stringResource(R.string.updates_start))
                }
            } else {
                StatusIndicator(app.status)
            }
        },
    )
}

@Composable
internal fun StatusIndicator(status: UpdateStatus) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            fadeIn(animationSpec = motion.defaultEffectsSpec()) togetherWith
                fadeOut(animationSpec = motion.fastEffectsSpec())
        },
        label = "updateStatus",
    ) { current ->
        when (current) {
            UpdateStatus.UPDATE_AVAILABLE -> Badge(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Text(stringResource(R.string.update_status_available))
            }

            UpdateStatus.UP_TO_DATE -> Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = stringResource(R.string.update_status_uptodate),
                tint = MaterialTheme.colorScheme.primary,
            )

            UpdateStatus.UNKNOWN -> Text(
                text = stringResource(R.string.update_status_unknown),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Progress of importing installed apps. */
@Composable
internal fun ImportProgressCard(
    progress: ImportProgress,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (progress.total > 0) {
                    stringResource(R.string.library_import_progress, progress.scanned, progress.total)
                } else {
                    stringResource(R.string.library_import_reading)
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            RutheniaWavyProgress(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth(),
            )

            progress.currentPackageLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TextButton(onClick = onCancel) { Text(stringResource(R.string.library_import_cancel)) }
        }
    }
}

@Composable
private fun versionLine(app: InstalledApp): String {
    val installed = app.installedVersionName ?: app.installedVersionCode?.toString()
    val latest = app.latestVersionName ?: app.latestVersionCode?.toString()
    val base = when {
        installed == null -> app.packageName
        app.status == UpdateStatus.UPDATE_AVAILABLE && latest != null ->
            stringResource(R.string.update_version_arrow, installed, latest)

        else -> installed
    }
    return "$base · ${app.source.label()}"
}

/** Install source label for the list. */
@Composable
private fun AppSource.label(): String = stringResource(
    when (this) {
        AppSource.RUSTORE -> R.string.source_rustore
        AppSource.GOOGLE_PLAY -> R.string.source_gplay
        AppSource.SIDELOAD -> R.string.source_sideload
        AppSource.OTHER -> R.string.source_other
        AppSource.UNKNOWN -> R.string.source_unknown
    },
)
