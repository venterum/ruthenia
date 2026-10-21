/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vntrum.ruthenia.core.designsystem.component.RutheniaWavyProgress
import com.vntrum.ruthenia.core.designsystem.component.rutheniaContentWidth
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaPillShape
import com.vntrum.ruthenia.core.github.GithubRelease
import com.vntrum.ruthenia.core.updates.GithubInstallState

/**
 * Add a GitHub repository and install an APK from one of its releases.
 *
 * The first install needs a dedicated worker because the Android package name
 * is unknown until the APK has been downloaded and inspected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GithubAddScreen(
    onBack: () -> Unit,
    onReady: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: GithubAddViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showWarning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.github_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .rutheniaContentWidth()
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.github_url_hint)) },
                singleLine = true,
                enabled = state.install !is GithubInstallState.Running,
            )
            Button(
                onClick = viewModel::load,
                enabled = state.input.isNotBlank() && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.github_find))
            }

            if (!state.hasToken) {
                Text(
                    text = stringResource(R.string.github_token_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.github_token_settings))
                }
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (state.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.repo?.let { repo ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!repo.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = repo.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(48.dp)
                                    .clip(RutheniaPillShape),
                            )
                        }
                        Column {
                            Text(
                                text = repo.fullName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            repo.description?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.github_include_prereleases),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = state.includePrereleases,
                        onCheckedChange = viewModel::setIncludePrereleases,
                        enabled = state.install !is GithubInstallState.Running,
                    )
                }

                if (state.availableReleases.isEmpty()) {
                    Text(
                        text = stringResource(R.string.github_no_releases),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.github_releases_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    state.availableReleases.take(MAX_RELEASES).forEach { release ->
                        ReleaseRow(
                            release = release,
                            selected = release.id == state.selectedRelease?.id,
                            enabled = state.install !is GithubInstallState.Running,
                            onClick = { viewModel.selectRelease(release) },
                        )
                    }

                    state.selectedRelease?.let { release ->
                        if (release.apkAssets.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.github_assets_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            release.apkAssets.forEach { asset ->
                                val size = if (asset.size > 0) {
                                    stringResource(
                                        R.string.github_asset_size,
                                        asset.size / BYTES_PER_MB,
                                    )
                                } else {
                                    null
                                }
                                FilterChip(
                                    selected = asset.name == state.selectedAsset?.name,
                                    onClick = { viewModel.selectAsset(asset) },
                                    enabled = state.install !is GithubInstallState.Running,
                                    label = {
                                        Text(
                                            text = listOfNotNull(asset.name, size).joinToString(" · "),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.github_warning_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                InstallPanel(
                    state = state.install,
                    canInstall = state.canInstall,
                    onInstall = { showWarning = true },
                    onCancel = viewModel::cancelInstall,
                    onReady = onReady,
                )
            }
        }
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text(stringResource(R.string.github_warning_title)) },
            text = { Text(stringResource(R.string.github_warning_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWarning = false
                        viewModel.startInstall()
                    },
                ) {
                    Text(stringResource(R.string.github_warning_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarning = false }) {
                    Text(stringResource(R.string.github_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReleaseRow(
    release: GithubRelease,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (release.prerelease) {
                    Text(
                        text = stringResource(R.string.github_prerelease),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            release.name?.takeIf { it.isNotBlank() && it != release.tagName }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InstallPanel(
    state: GithubInstallState,
    canInstall: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onReady: (String) -> Unit,
) {
    when (state) {
        GithubInstallState.Idle -> Button(
            onClick = onInstall,
            enabled = canInstall,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.github_install))
        }

        GithubInstallState.Queued -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(R.string.github_install_running))
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.github_cancel))
            }
        }

        is GithubInstallState.Running -> Column {
            Text(
                text = state.fraction?.let {
                    stringResource(R.string.github_downloading_percent, (it * 100).toInt())
                } ?: stringResource(R.string.github_install_running),
                style = MaterialTheme.typography.bodyMedium,
            )
            state.fraction?.let { fraction ->
                RutheniaWavyProgress(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            } ?: CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.github_cancel))
            }
        }

        is GithubInstallState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.github_install_ready),
                color = MaterialTheme.colorScheme.primary,
            )
            Button(
                onClick = { state.packageName.takeIf { it.isNotBlank() }?.let(onReady) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.github_open_details))
            }
        }

        is GithubInstallState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = state.message ?: stringResource(R.string.github_error_generic),
                color = MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = onInstall,
                enabled = canInstall,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.github_install))
            }
        }
    }
}

private const val MAX_RELEASES = 8
private const val BYTES_PER_MB = 1024.0 * 1024.0
