/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaPillShape
import com.vntrum.ruthenia.core.updates.DownloadState
import com.vntrum.ruthenia.core.updates.DownloadTask
import com.vntrum.ruthenia.core.updates.FailureAction
import com.vntrum.ruthenia.core.updates.FailureText
import com.vntrum.ruthenia.core.updates.InstallState
import com.vntrum.ruthenia.core.updates.QueueReason
import com.vntrum.ruthenia.core.updates.TaskFailureCode
import com.vntrum.ruthenia.core.updates.isRetryable
import com.vntrum.ruthenia.core.updates.label
import com.vntrum.ruthenia.core.updates.needsRedownload
import com.vntrum.ruthenia.core.updates.text

/**
 * Download manager on the library page.
 *
 * Collapsed, it shows an honest summary of every group. Expanded, it becomes
 * the full manager: active downloads, ready-to-install rows, typed failures
 * with recovery actions, paused tasks and completed history.
 */
@Composable
internal fun DownloadManagerCard(
    tasks: List<DownloadTask>,
    installingPackage: String?,
    cacheBytes: Long,
    modifier: Modifier = Modifier,
    expandSignal: Int = 0,
    onInstall: (DownloadTask) -> Unit,
    onPause: (DownloadTask) -> Unit,
    onResume: (DownloadTask) -> Unit,
    onRetry: (DownloadTask) -> Unit,
    onRedownload: (DownloadTask) -> Unit,
    onRemove: (DownloadTask) -> Unit,
    onClearCompleted: () -> Unit,
    onClearCache: () -> Unit,
    onRetryAllFailed: () -> Unit,
    onRemoveAllFailed: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onCheckUpdates: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }
    var confirmRemoveFailed by remember { mutableStateOf(false) }

    LaunchedEffect(expandSignal) {
        if (expandSignal > 0) expanded = true
    }

    val active = tasks.filter { it.isActive }
    val ready = tasks.filter { it.isReady && !it.isFailed && !it.isActive }
    val attention = tasks.filter { it.isFailed }
    val paused = tasks.filter { it.isPaused }
    val completed = tasks.filter { it.isCompleted }
    val overall = overallFraction(active)

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) -90f else 90f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "downloadsChevron",
    )
    val containerColor by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "downloadsContainer",
    )
    val retryableDownloads = attention.any {
        it.download is DownloadState.Failed &&
            (it.failure?.isRetryable == true || it.failure?.needsRedownload == true)
    }

    ExpressiveCard(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
        containerColor = containerColor,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = when {
                        attention.isNotEmpty() -> MaterialTheme.colorScheme.errorContainer
                        active.isNotEmpty() -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = when {
                        attention.isNotEmpty() -> MaterialTheme.colorScheme.onErrorContainer
                        active.isNotEmpty() -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                        .padding(start = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = stringResource(R.string.downloads_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                    Text(
                        text = managerSummary(
                            activeCount = active.size,
                            readyCount = ready.size,
                            failedCount = attention.size,
                            pausedCount = paused.size,
                            completedCount = completed.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (attention.isNotEmpty()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (cacheBytes > 0L && active.isEmpty() && attention.isEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.downloads_cache,
                                Formatter.formatShortFileSize(LocalContext.current, cacheBytes),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = stringResource(
                                if (expanded) {
                                    R.string.downloads_collapse
                                } else {
                                    R.string.downloads_expand
                                },
                            ),
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronRotation),
                        )
                    }
                }
            }

            if (active.isNotEmpty()) {
                ActiveDownloadsSummary(active = active, overall = overall)
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (tasks.isEmpty()) {
                        EmptyManagerState()
                    }

                    DownloadSection(
                        title = stringResource(R.string.downloads_section_active),
                        accent = MaterialTheme.colorScheme.primary,
                        tasks = active,
                        installingPackage = installingPackage,
                        onInstall = onInstall,
                        onPause = onPause,
                        onResume = onResume,
                        onRetry = onRetry,
                        onRedownload = onRedownload,
                        onRemove = onRemove,
                        onOpenAppDetails = onOpenAppDetails,
                        onCheckUpdates = onCheckUpdates,
                    )
                    DownloadSection(
                        title = stringResource(R.string.downloads_section_attention),
                        accent = MaterialTheme.colorScheme.error,
                        tasks = attention,
                        installingPackage = installingPackage,
                        onInstall = onInstall,
                        onPause = onPause,
                        onResume = onResume,
                        onRetry = onRetry,
                        onRedownload = onRedownload,
                        onRemove = onRemove,
                        onOpenAppDetails = onOpenAppDetails,
                        onCheckUpdates = onCheckUpdates,
                    )
                    DownloadSection(
                        title = stringResource(R.string.downloads_section_ready),
                        accent = MaterialTheme.colorScheme.tertiary,
                        tasks = ready,
                        installingPackage = installingPackage,
                        onInstall = onInstall,
                        onPause = onPause,
                        onResume = onResume,
                        onRetry = onRetry,
                        onRedownload = onRedownload,
                        onRemove = onRemove,
                        onOpenAppDetails = onOpenAppDetails,
                        onCheckUpdates = onCheckUpdates,
                    )
                    DownloadSection(
                        title = stringResource(R.string.downloads_section_paused),
                        accent = MaterialTheme.colorScheme.secondary,
                        tasks = paused,
                        installingPackage = installingPackage,
                        onInstall = onInstall,
                        onPause = onPause,
                        onResume = onResume,
                        onRetry = onRetry,
                        onRedownload = onRedownload,
                        onRemove = onRemove,
                        onOpenAppDetails = onOpenAppDetails,
                        onCheckUpdates = onCheckUpdates,
                    )
                    CompletedSection(
                        tasks = completed,
                        onRemove = onRemove,
                    )

                    val hasFooter = cacheBytes > 0L ||
                        retryableDownloads ||
                        attention.isNotEmpty() ||
                        completed.isNotEmpty()
                    if (hasFooter) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        ManagerFooter(
                            cacheBytes = cacheBytes,
                            retryableDownloads = retryableDownloads,
                            hasFailures = attention.isNotEmpty(),
                            hasCompleted = completed.isNotEmpty(),
                            onRetryAllFailed = onRetryAllFailed,
                            onRemoveAllFailed = { confirmRemoveFailed = true },
                            onClearCompleted = onClearCompleted,
                            onClearCache = { confirmClearCache = true },
                        )
                    }
                }
            }
        }
    }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text(stringResource(R.string.downloads_confirm_clear_cache_title)) },
            text = { Text(stringResource(R.string.downloads_confirm_clear_cache_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearCache = false
                        onClearCache()
                    },
                ) {
                    Text(stringResource(R.string.downloads_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) {
                    Text(stringResource(R.string.downloads_cancel))
                }
            },
        )
    }

    if (confirmRemoveFailed) {
        AlertDialog(
            onDismissRequest = { confirmRemoveFailed = false },
            title = { Text(stringResource(R.string.downloads_confirm_remove_all_title)) },
            text = { Text(stringResource(R.string.downloads_confirm_remove_all_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoveFailed = false
                        onRemoveAllFailed()
                    },
                ) {
                    Text(stringResource(R.string.downloads_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveFailed = false }) {
                    Text(stringResource(R.string.downloads_cancel))
                }
            },
        )
    }
}

@Composable
private fun ActiveDownloadsSummary(
    active: List<DownloadTask>,
    overall: Float?,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.download_active_count,
                        active.size,
                        active.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (overall != null) {
                    Text(
                        text = "${(overall * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (overall != null) {
                RutheniaWavyProgress(
                    progress = { overall },
                    modifier = Modifier
                        .fillMaxWidth()
                        .progressSemantics(overall),
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RutheniaLoadingIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.downloads_active_in_progress),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyManagerState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_download),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.downloads_empty_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.downloads_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManagerSectionHeader(
    title: String,
    count: Int,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = accent, shape = CircleShape),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RutheniaPillShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManagerFooter(
    cacheBytes: Long,
    retryableDownloads: Boolean,
    hasFailures: Boolean,
    hasCompleted: Boolean,
    onRetryAllFailed: () -> Unit,
    onRemoveAllFailed: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearCache: () -> Unit,
) {
    val hasBatchActions = retryableDownloads || hasFailures || hasCompleted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.downloads_cache_actions,
                    Formatter.formatShortFileSize(LocalContext.current, cacheBytes),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (cacheBytes > 0L) {
                TextButton(onClick = onClearCache) {
                    Text(stringResource(R.string.downloads_clear_cache))
                }
            }
        }
        if (hasBatchActions) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (retryableDownloads) {
                    TextButton(onClick = onRetryAllFailed) {
                        Text(stringResource(R.string.downloads_retry_all))
                    }
                }
                if (hasFailures) {
                    TextButton(
                        onClick = onRemoveAllFailed,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.downloads_remove_all_failed))
                    }
                }
                if (hasCompleted) {
                    TextButton(onClick = onClearCompleted) {
                        Text(
                            text = stringResource(R.string.downloads_clear_history),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadSection(
    title: String,
    accent: Color,
    tasks: List<DownloadTask>,
    installingPackage: String?,
    onInstall: (DownloadTask) -> Unit,
    onPause: (DownloadTask) -> Unit,
    onResume: (DownloadTask) -> Unit,
    onRetry: (DownloadTask) -> Unit,
    onRedownload: (DownloadTask) -> Unit,
    onRemove: (DownloadTask) -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onCheckUpdates: () -> Unit,
) {
    if (tasks.isEmpty()) return
    ManagerSectionHeader(title = title, count = tasks.size, accent = accent)
    tasks.forEachIndexed { index, task ->
        if (index > 0) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        DownloadTaskItem(
            task = task,
            installing = installingPackage == task.packageName ||
                task.install is InstallState.Installing ||
                task.install is InstallState.AwaitingUser,
            onInstall = { onInstall(task) },
            onPause = { onPause(task) },
            onResume = { onResume(task) },
            onRetry = { onRetry(task) },
            onRedownload = { onRedownload(task) },
            onRemove = { onRemove(task) },
            onOpenAppDetails = { onOpenAppDetails(task.packageName) },
            onCheckUpdates = onCheckUpdates,
        )
    }
}

@Composable
private fun CompletedSection(
    tasks: List<DownloadTask>,
    onRemove: (DownloadTask) -> Unit,
) {
    if (tasks.isEmpty()) return
    var showAll by rememberSaveable { mutableStateOf(false) }
    val visible = if (showAll) tasks else tasks.take(COMPLETED_PREVIEW_COUNT)
    ManagerSectionHeader(
        title = stringResource(R.string.downloads_section_completed),
        count = tasks.size,
        accent = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    visible.forEachIndexed { index, task ->
        if (index > 0) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        CompletedTaskItem(task = task, onRemove = { onRemove(task) })
    }
    if (tasks.size > COMPLETED_PREVIEW_COUNT) {
        TextButton(
            onClick = { showAll = !showAll },
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text(
                stringResource(
                    if (showAll) R.string.downloads_show_less else R.string.downloads_show_all,
                ),
            )
        }
    }
}

@Composable
private fun DownloadTaskItem(
    task: DownloadTask,
    installing: Boolean,
    onInstall: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRedownload: () -> Unit,
    onRemove: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    val context = LocalContext.current
    var showDetails by rememberSaveable(task.packageName) { mutableStateOf(false) }
    val failure = task.failure
    val failureText = failure?.text(context)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RutheniaAppIcon(
                iconUrl = task.iconUrl,
                fallbackIcon = painterResource(R.drawable.ic_library),
                size = 42.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = task.name ?: task.packageName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TaskStatusLine(task = task, failureText = failureText)
            }

            TaskActions(
                task = task,
                installing = installing,
                failureText = failureText,
                onInstall = onInstall,
                onPause = onPause,
                onResume = onResume,
                onRemove = onRemove,
            )
        }

        if (!task.isFailed) {
            DownloadProgressContent(task = task)
        }

        if (failureText != null) {
            FailurePanel(
                task = task,
                failureText = failureText,
                detail = failure.detail,
                showDetails = showDetails,
                onToggleDetails = { showDetails = !showDetails },
                onInstall = onInstall,
                onRetry = onRetry,
                onRedownload = onRedownload,
                onRemove = onRemove,
                onOpenAppDetails = onOpenAppDetails,
                onCheckUpdates = onCheckUpdates,
            )
        }
    }
}

@Composable
private fun TaskStatusLine(
    task: DownloadTask,
    failureText: FailureText?,
) {
    val isFailure = failureText != null && task.isFailed
    val accent = when {
        isFailure -> MaterialTheme.colorScheme.error
        task.isPaused -> MaterialTheme.colorScheme.tertiary
        task.isReady || task.isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isFailure) {
            Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = accent,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color = accent, shape = CircleShape),
            )
        }
        Text(
            text = failureText?.title ?: task.statusText(),
            style = MaterialTheme.typography.bodySmall,
            color = if (isFailure) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FailurePanel(
    task: DownloadTask,
    failureText: FailureText,
    detail: String?,
    showDetails: Boolean,
    onToggleDetails: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onRedownload: () -> Unit,
    onRemove: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_error),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (failureText.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = failureText.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FailureActions(
                task = task,
                failureText = failureText,
                showDetails = showDetails,
                onToggleDetails = onToggleDetails,
                onInstall = onInstall,
                onRetry = onRetry,
                onRedownload = onRedownload,
                onRemove = onRemove,
                onOpenAppDetails = onOpenAppDetails,
                onCheckUpdates = onCheckUpdates,
            )
            if (showDetails && detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TaskActions(
    task: DownloadTask,
    installing: Boolean,
    failureText: FailureText?,
    onInstall: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
) {
    when {
        installing || task.install is InstallState.Installing ||
            task.install is InstallState.AwaitingUser -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RutheniaLoadingIndicator(modifier = Modifier.size(18.dp))
        }

        task.isFailed && failureText != null -> Unit

        task.isPaused -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(onClick = onResume) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = stringResource(R.string.downloads_resume),
                )
            }
            RemoveIcon(onRemove = onRemove)
        }

        task.canPause -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(onClick = onPause) {
                Icon(
                    painter = painterResource(R.drawable.ic_pause),
                    contentDescription = stringResource(R.string.downloads_pause),
                )
            }
            RemoveIcon(onRemove = onRemove)
        }

        task.isReady -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Button(onClick = onInstall) {
                Icon(
                    painter = painterResource(R.drawable.ic_download),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.common_install),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            RemoveIcon(onRemove = onRemove)
        }

        task.isCompleted -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoveIcon(onRemove: () -> Unit) {
    IconButton(onClick = onRemove) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = stringResource(R.string.downloads_remove),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FailureActions(
    task: DownloadTask,
    failureText: FailureText,
    showDetails: Boolean,
    onToggleDetails: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onRedownload: () -> Unit,
    onRemove: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    val context = LocalContext.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        failureText.primaryAction?.let { action ->
            Button(onClick = {
                handleFailureAction(
                    context = context,
                    action = action,
                    task = task,
                    onInstall = onInstall,
                    onRetry = onRetry,
                    onRedownload = onRedownload,
                    onRemove = onRemove,
                    onOpenAppDetails = onOpenAppDetails,
                    onCheckUpdates = onCheckUpdates,
                )
            }) {
                Text(action.label(context))
            }
        }
        failureText.secondaryAction?.let { action ->
            TextButton(
                onClick = {
                    handleFailureAction(
                        context = context,
                        action = action,
                        task = task,
                        onInstall = onInstall,
                        onRetry = onRetry,
                        onRedownload = onRedownload,
                        onRemove = onRemove,
                        onOpenAppDetails = onOpenAppDetails,
                        onCheckUpdates = onCheckUpdates,
                    )
                },
                colors = if (action == FailureAction.REMOVE) {
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(action.label(context))
            }
        }
        val canInstallAgain = task.isReady && task.failure?.code in setOf(
            TaskFailureCode.PERMISSION_REQUIRED,
            TaskFailureCode.STORAGE_FULL,
            TaskFailureCode.INSTALL_BLOCKED,
        ) &&
            failureText.primaryAction != FailureAction.INSTALL_AGAIN &&
            failureText.secondaryAction != FailureAction.RETRY
        if (canInstallAgain) {
            TextButton(onClick = onInstall) {
                Text(stringResource(R.string.downloads_install_again))
            }
        }
        if (task.failure?.detail != null) {
            TextButton(onClick = onToggleDetails) {
                Text(
                    stringResource(
                        if (showDetails) R.string.downloads_hide_details else R.string.downloads_details,
                    ),
                )
            }
        }
    }
}

private fun handleFailureAction(
    context: Context,
    action: FailureAction,
    task: DownloadTask,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onRedownload: () -> Unit,
    onRemove: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    when (action) {
        // A Ready task with an install failure should retry the install, not
        // download the whole package again.
        FailureAction.RETRY -> if (task.isReady) onInstall() else onRetry()
        FailureAction.REDOWNLOAD -> onRedownload()
        FailureAction.INSTALL_AGAIN -> onInstall()
        FailureAction.OPEN_APP_DETAILS -> onOpenAppDetails()
        FailureAction.CHECK_UPDATES -> onCheckUpdates()
        FailureAction.REMOVE -> onRemove()
        FailureAction.COPY_DETAILS -> {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val text = task.failure?.let { failure ->
                "${failure.code.name}\n${failure.detail.orEmpty()}"
            }.orEmpty()
            clipboard?.setPrimaryClip(ClipData.newPlainText("Ruthenia download", text))
            Toast.makeText(
                context,
                context.getString(R.string.downloads_details_copied),
                Toast.LENGTH_SHORT,
            ).show()
        }

        FailureAction.OPEN_INSTALL_SETTINGS -> context.startSettings(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ),
        )

        FailureAction.OPEN_STORAGE_SETTINGS -> context.startSettings(
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        )

        FailureAction.OPEN_NETWORK_SETTINGS -> context.startSettings(
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
        )

        FailureAction.OPEN_SHIZUKU -> context.startSettings(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:moe.shizuku.privileged.api"),
            ),
        )
    }
}

private fun Context.startSettings(intent: Intent) {
    runCatching { startActivity(intent) }
}

@Composable
private fun CompletedTaskItem(
    task: DownloadTask,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val launchIntent = remember(task.packageName) {
        context.packageManager.getLaunchIntentForPackage(task.packageName)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RutheniaAppIcon(
            iconUrl = task.iconUrl,
            fallbackIcon = painterResource(R.drawable.ic_library),
            size = 42.dp,
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
            task.versionName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = completedLabel(task.completedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (launchIntent != null) {
            TextButton(onClick = { context.startActivity(launchIntent) }) {
                Text(stringResource(R.string.downloads_open))
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.downloads_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun completedLabel(completedAt: Long?): String {
    if (completedAt == null) return stringResource(R.string.downloads_installed_just_now)
    val now = System.currentTimeMillis()
    if (now - completedAt < DateUtils.MINUTE_IN_MILLIS) {
        return stringResource(R.string.downloads_installed_just_now)
    }
    val relative = DateUtils.getRelativeTimeSpanString(
        completedAt,
        now,
        DateUtils.MINUTE_IN_MILLIS,
    )
    return stringResource(R.string.downloads_installed_relative, relative)
}

@Composable
private fun DownloadTask.statusText(): String {
    return when {
        install is InstallState.Installing -> stringResource(R.string.downloads_installing)
        install is InstallState.AwaitingUser ->
            stringResource(R.string.downloads_waiting_confirmation)

        else -> when (val state = download) {
            is DownloadState.Queued -> when (state.reason) {
                QueueReason.WAITING_NETWORK -> stringResource(R.string.downloads_queue_network)
                QueueReason.WAITING_WIFI -> stringResource(R.string.downloads_queue_wifi)
                QueueReason.RETRYING -> stringResource(R.string.downloads_queue_retrying)
                QueueReason.QUEUED -> stringResource(R.string.downloads_queue)
            }

            is DownloadState.Downloading -> state.fraction
                ?.let { stringResource(R.string.download_downloading_percent, (it * 100).toInt()) }
                ?: stringResource(R.string.download_downloading)

            is DownloadState.Verifying -> stringResource(R.string.download_verifying)
            is DownloadState.Ready -> buildReadyLabel()
            is DownloadState.Paused -> stringResource(R.string.downloads_paused)
            is DownloadState.Completed -> stringResource(R.string.downloads_installed_just_now)
            is DownloadState.Failed -> state.failure.detail
                ?: stringResource(R.string.downloads_unknown_error)

            DownloadState.Idle -> ""
        }
    }
}

@Composable
private fun DownloadTask.buildReadyLabel(): String {
    val version = versionName ?: versionCode?.toString()
    val size = totalBytes?.let {
        Formatter.formatShortFileSize(LocalContext.current, it)
    }
    return listOfNotNull(
        version?.let { it },
        size,
    ).joinToString(" · ").ifEmpty { stringResource(R.string.download_ready) }
}

@Composable
private fun DownloadProgressContent(task: DownloadTask) {
    val state = task.download
    when (state) {
        is DownloadState.Downloading -> {
            val fraction = state.fraction
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (fraction == null) {
                        RutheniaLoadingIndicator(modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.download_downloading),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.download_downloaded_percent,
                                (fraction * 100).toInt(),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = bytesCaption(state.downloadedBytes, state.totalBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (fraction != null) {
                    RutheniaWavyProgress(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .progressSemantics(fraction),
                    )
                }
            }
        }

        else -> Unit
    }
}

@Composable
private fun bytesCaption(downloaded: Long, total: Long?): String {
    val context = LocalContext.current
    val downloadedText = Formatter.formatShortFileSize(context, downloaded)
    return if (total != null && total > 0L) {
        stringResource(
            R.string.downloads_size_progress,
            downloadedText,
            Formatter.formatShortFileSize(context, total),
        )
    } else {
        stringResource(R.string.downloads_size, downloadedText)
    }
}

@Composable
private fun managerSummary(
    activeCount: Int,
    readyCount: Int,
    failedCount: Int,
    pausedCount: Int,
    completedCount: Int,
): String {
    val parts = buildList {
        if (activeCount > 0) {
            add(pluralStringResource(R.plurals.download_active_count, activeCount, activeCount))
        }
        if (readyCount > 0) {
            add(pluralStringResource(R.plurals.download_ready_count, readyCount, readyCount))
        }
        if (failedCount > 0) {
            add(pluralStringResource(R.plurals.download_failed_count, failedCount, failedCount))
        }
        if (pausedCount > 0) {
            add(pluralStringResource(R.plurals.download_paused_count, pausedCount, pausedCount))
        }
        if (completedCount > 0) {
            add(
                pluralStringResource(
                    R.plurals.download_completed_count,
                    completedCount,
                    completedCount,
                ),
            )
        }
    }
    return if (parts.isEmpty()) {
        stringResource(R.string.downloads_empty_idle)
    } else {
        parts.joinToString(" · ")
    }
}

/** Average progress of active downloads; `null` if at least one size is unknown. */
private fun overallFraction(active: List<DownloadTask>): Float? {
    if (active.isEmpty()) return null
    val fractions = active.map { task ->
        (task.download as? DownloadState.Downloading)?.fraction ?: return null
    }
    return fractions.average().toFloat().coerceIn(0f, 1f)
}

private const val COMPLETED_PREVIEW_COUNT = 5
