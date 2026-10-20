/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.library

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.core.data.model.InstalledApp
import com.vntrum.ruthenia.core.designsystem.component.ExpressiveCard
import com.vntrum.ruthenia.core.designsystem.component.RutheniaAppIcon
import com.vntrum.ruthenia.core.designsystem.component.RutheniaLoadingIndicator
import com.vntrum.ruthenia.core.designsystem.component.RutheniaWavyProgress
import com.vntrum.ruthenia.core.designsystem.component.progressSemantics
import com.vntrum.ruthenia.core.designsystem.haptics.rememberRutheniaHaptics
import com.vntrum.ruthenia.core.designsystem.text.appCountLabel
import com.vntrum.ruthenia.core.updates.DownloadState
import com.vntrum.ruthenia.core.updates.InstallState
import com.vntrum.ruthenia.core.updates.isBusy
import com.vntrum.ruthenia.core.updates.text

/**
 * App card with an available update: version, background download progress, and
 * an action button ("Update" / "Install" / cancel).
 */
@Composable
internal fun UpdateCard(
    row: UpdateRow,
    installing: Boolean,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
    onClick: () -> Unit,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onHide: () -> Unit,
) {
    val app = row.app
    val state = row.download
    val failure = (state as? DownloadState.Failed)?.failure
        ?: (row.install as? InstallState.Failed)?.failure
    var menuExpanded by remember { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme
    val context = LocalContext.current

    ExpressiveCard(
        onClick = { if (selectionMode) onSelectedChange(!selected) else onClick() },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = motion.defaultSpatialSpec<IntSize>())
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = onSelectedChange,
                    )
                }
                RutheniaAppIcon(
                    iconUrl = app.iconUrl,
                    fallbackIcon = painterResource(R.drawable.ic_library),
                    modifier = iconModifier,
                    size = 48.dp,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = app.name ?: app.packageName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = updateVersionLine(app),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    failure?.let {
                        val copy = it.text(context)
                        Text(
                            text = copy.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (copy.isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = copy.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                DownloadActionButton(
                    state = state,
                    install = row.install,
                    installing = installing,
                    startLabel = stringResource(R.string.updates_start),
                    onStart = onUpdate,
                    onCancel = onCancel,
                    onInstall = onInstall,
                )

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = stringResource(R.string.updates_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.updates_hide)) },
                            onClick = {
                                menuExpanded = false
                                onHide()
                            },
                        )
                    }
                }
            }

            DownloadProgressContent(state = state)
        }
    }
}

/**
 * Download action button — shared by the updates section and the manager:
 * "Install" for ready ones, cancel for active ones, "Retry" for failed ones.
 */
@Composable
internal fun DownloadActionButton(
    state: DownloadState,
    installing: Boolean,
    startLabel: String,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    install: InstallState = InstallState.Idle,
) {
    val haptics = rememberRutheniaHaptics()
    val isInstalling = installing ||
        install is InstallState.Installing ||
        install is InstallState.AwaitingUser
    when {
        isInstalling -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RutheniaLoadingIndicator(modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.download_installing),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state is DownloadState.Ready -> Button(
            onClick = {
                haptics.confirm()
                onInstall()
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_download),
                contentDescription = null,
            )
            Text(text = stringResource(R.string.common_install), modifier = Modifier.padding(start = 8.dp))
        }

        state.isBusy -> IconButton(
            onClick = {
                haptics.tick()
                onCancel()
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.downloads_pause),
            )
        }

        else -> FilledTonalButton(
            onClick = {
                haptics.confirm()
                onStart()
            },
        ) {
            Text(
                text = if (state is DownloadState.Paused) {
                    stringResource(R.string.downloads_resume)
                } else {
                    startLabel
                },
            )
        }
    }
}

/** Download progress: queued, downloading with a percentage, or verifying the signature. */
@Composable
internal fun DownloadProgressContent(state: DownloadState) {
    when (state) {
        is DownloadState.Queued -> ProgressCaption(stringResource(R.string.download_queued))

        is DownloadState.Verifying -> ProgressCaption(stringResource(R.string.download_verifying))

        is DownloadState.Downloading -> {
            val fraction = state.fraction
            Spacer(modifier = Modifier.height(10.dp))
            if (fraction == null) {
                RutheniaLoadingIndicator(modifier = Modifier.size(24.dp))
                ProgressCaption(stringResource(R.string.download_downloading))
            } else {
                RutheniaWavyProgress(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .progressSemantics(fraction),
                )
                ProgressCaption(
                    stringResource(R.string.download_downloaded_percent, (fraction * 100).toInt()),
                )
            }
        }

        else -> Unit
    }
}

@Composable
private fun ProgressCaption(text: String) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Section label: "3 apps", "1 app", etc. */
@Composable
internal fun updateCountLabel(count: Int): String = appCountLabel(count)

@Composable
internal fun updateVersionLine(app: InstalledApp): String {
    val installed = app.installedVersionName ?: app.installedVersionCode?.toString()
    val latest = app.latestVersionName ?: app.latestVersionCode?.toString()
    return when {
        installed != null && latest != null ->
            stringResource(R.string.update_version_arrow, installed, latest)

        latest != null -> stringResource(R.string.update_new_version, latest)
        else -> stringResource(R.string.update_available_generic)
    }
}
