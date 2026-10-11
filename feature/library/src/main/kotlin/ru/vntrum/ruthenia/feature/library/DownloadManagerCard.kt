/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.core.designsystem.component.ExpressiveCard
import com.vntrum.ruthenia.core.designsystem.component.RutheniaAppIcon
import com.vntrum.ruthenia.core.designsystem.component.RutheniaLoadingIndicator
import com.vntrum.ruthenia.core.designsystem.component.RutheniaWavyProgress
import com.vntrum.ruthenia.core.designsystem.component.progressSemantics
import com.vntrum.ruthenia.core.updates.DownloadState
import com.vntrum.ruthenia.core.updates.DownloadTask

/**
 * Download manager row on the library page.
 *
 * Collapsed, it's a compact row with overall progress; on tap it expands into a
 * list of downloads, where each can be canceled, retried, or installed. It's
 * shown only when there's something to show: active downloads, ones ready to
 * install, or failed ones.
 */
@Composable
internal fun DownloadManagerCard(
    tasks: List<DownloadTask>,
    installingPackage: String?,
    modifier: Modifier = Modifier,
    onInstall: (DownloadTask) -> Unit,
    onCancel: (DownloadTask) -> Unit,
    onRetry: (DownloadTask) -> Unit,
) {
    if (tasks.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }

    val active = tasks.filter { it.isActive }
    val ready = tasks.filter { it.isReady }
    val failed = tasks.filter { it.isFailed }
    val overall = overallFraction(active)

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) -90f else 90f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "downloadsChevron",
    )

    ExpressiveCard(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (active.isNotEmpty()) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    },
                    contentColor = if (active.isNotEmpty()) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Box(
                        modifier = Modifier.size(56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.downloads_title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(
                        text = managerSummary(active.size, ready.size, failed.size, overall),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = stringResource(
                        if (expanded) R.string.downloads_collapse else R.string.downloads_expand,
                    ),
                    modifier = Modifier.rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (active.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (overall == null) {
                        RutheniaLoadingIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        RutheniaWavyProgress(
                            progress = { overall },
                            modifier = Modifier
                                .weight(1f)
                                .progressSemantics(overall),
                        )
                        Text(
                            text = "${(overall * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    tasks.forEachIndexed { index, task ->
                        if (index > 0) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        }
                        DownloadTaskItem(
                            task = task,
                            installing = installingPackage == task.packageName,
                            onInstall = { onInstall(task) },
                            onCancel = { onCancel(task) },
                            onRetry = { onRetry(task) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskItem(
    task: DownloadTask,
    installing: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RutheniaAppIcon(
                iconUrl = task.iconUrl,
                fallbackIcon = painterResource(R.drawable.ic_library),
                size = 40.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = task.name ?: task.packageName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = task.statusText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.isFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            DownloadActionButton(
                state = task.state,
                installing = installing,
                startLabel = stringResource(R.string.download_retry),
                onStart = onRetry,
                onCancel = onCancel,
                onInstall = onInstall,
            )
        }

        DownloadProgressContent(state = task.state)
    }
}

/** Collapsed row label: active downloads take priority over ready and failed ones. */
@Composable
private fun managerSummary(
    active: Int,
    ready: Int,
    failed: Int,
    overall: Float?,
): String = when {
    active > 0 -> {
        val count = pluralStringResource(R.plurals.download_active_count, active, active)
        if (overall != null) {
            stringResource(R.string.download_summary_active, count, (overall * 100).toInt())
        } else {
            count
        }
    }

    ready > 0 -> pluralStringResource(R.plurals.download_ready_count, ready, ready)
    else -> pluralStringResource(R.plurals.download_failed_count, failed, failed)
}

@Composable
private fun DownloadTask.statusText(): String = when (val state = state) {
    is DownloadState.Queued -> stringResource(R.string.download_queued_short)
    is DownloadState.Downloading -> state.fraction
        ?.let { stringResource(R.string.download_downloading_percent, (it * 100).toInt()) }
        ?: stringResource(R.string.download_downloading)

    is DownloadState.Verifying -> stringResource(R.string.download_verifying)
    is DownloadState.Ready -> stringResource(R.string.download_ready)
    is DownloadState.Failed -> state.message
    DownloadState.Idle, DownloadState.Cancelled -> ""
}

/** Average progress of active downloads; `null` if at least one size is unknown. */
private fun overallFraction(active: List<DownloadTask>): Float? {
    if (active.isEmpty()) return null
    val fractions = active.map { (it.state as? DownloadState.Downloading)?.fraction ?: return null }
    return fractions.average().toFloat().coerceIn(0f, 1f)
}
