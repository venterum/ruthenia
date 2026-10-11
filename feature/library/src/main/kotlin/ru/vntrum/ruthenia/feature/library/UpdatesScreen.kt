/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vntrum.ruthenia.core.designsystem.component.RutheniaLoadingIndicator
import com.vntrum.ruthenia.core.designsystem.component.StateMessage
import com.vntrum.ruthenia.core.designsystem.component.appIconTransitionKey
import com.vntrum.ruthenia.core.designsystem.component.rutheniaContentWidth
import com.vntrum.ruthenia.core.designsystem.haptics.rememberRutheniaHaptics
import com.vntrum.ruthenia.core.updates.isActionable
import kotlinx.coroutines.launch

/**
 * "Updates": available updates with background downloading and installation.
 * There's a selection mode: mark several apps and update them in one batch;
 * hiding an update can be undone right from the snackbar.
 *
 * The scopes drive the shared element transition of the app icon into the
 * details hero (see `SharedTransitionLayout` in `RutheniaApp`).
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun UpdatesScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onAppClick: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val updateRows by viewModel.updateRows.collectAsStateWithLifecycle()
    val hiddenUpdateCount by viewModel.hiddenUpdateCount.collectAsStateWithLifecycle()
    val installingPackage by viewModel.installingPackage.collectAsStateWithLifecycle()
    val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()
    val isChecking by viewModel.isChecking.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val confirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val motion = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    val haptics = rememberRutheniaHaptics()
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }

    // We launch the system install dialog from the Activity — it can't be bypassed.
    LaunchedEffect(confirmation) {
        confirmation?.let { intent ->
            runCatching { context.startActivity(intent) }
            viewModel.onConfirmationLaunched()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // If the list is now empty (everything was updated), selection mode is no longer needed.
    LaunchedEffect(updateRows.isEmpty()) {
        if (updateRows.isEmpty() && selectionMode) {
            selectionMode = false
            viewModel.clearSelection()
        }
    }

    val actionable = updateRows.count { it.download.isActionable }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.updates_title)) },
                subtitle = {
                    Text(
                        pluralStringResource(
                            R.plurals.updates_count,
                            updateRows.size,
                            updateRows.size,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (updateRows.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                haptics.tick()
                                if (selectionMode) {
                                    selectionMode = false
                                    viewModel.clearSelection()
                                } else {
                                    selectionMode = true
                                    viewModel.selectAllActionable()
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (selectionMode) R.drawable.ic_close else R.drawable.ic_check,
                                ),
                                contentDescription = stringResource(
                                    if (selectionMode) {
                                        R.string.updates_clear_selection
                                    } else {
                                        R.string.updates_select_all
                                    },
                                ),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (selectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                selectionMode = false
                                viewModel.clearSelection()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.updates_cancel_selection))
                        }
                        Button(
                            onClick = {
                                haptics.confirm()
                                viewModel.updateSelected()
                                selectionMode = false
                            },
                            enabled = selectedPackages.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                stringResource(
                                    R.string.updates_update_selected,
                                    selectedPackages.size,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .rutheniaContentWidth()
                    .fillMaxWidth(),
            ) {
                if (hiddenUpdateCount > 0) {
                    Text(
                        text = stringResource(R.string.updates_hidden_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        updateRows.isEmpty() && isChecking -> Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            RutheniaLoadingIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.updates_checking),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        updateRows.isEmpty() -> StateMessage(
                            title = stringResource(R.string.updates_empty_title),
                            text = stringResource(R.string.updates_empty_text),
                            icon = painterResource(R.drawable.ic_check),
                        )

                        else -> LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = PaddingValues(bottom = 96.dp),
                        ) {
                            items(updateRows, key = { "update-${it.app.packageName}" }) { row ->
                                UpdateCard(
                                    row = row,
                                    installing = installingPackage == row.app.packageName,
                                    iconModifier = with(sharedTransitionScope) {
                                        Modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState(
                                                appIconTransitionKey(row.app.packageName),
                                            ),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                        )
                                    },
                                    selectionMode = selectionMode,
                                    selected = row.app.packageName in selectedPackages,
                                    onSelectedChange = {
                                        haptics.tick()
                                        viewModel.toggleSelected(row.app.packageName)
                                    },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = motion.defaultEffectsSpec(),
                                        placementSpec = motion.defaultSpatialSpec(),
                                        fadeOutSpec = motion.fastEffectsSpec(),
                                    ),
                                    onClick = { onAppClick(row.app.packageName) },
                                    onUpdate = { viewModel.update(row.app) },
                                    onCancel = { viewModel.cancelDownload(row.app) },
                                    onInstall = { viewModel.install(row.app) },
                                    onHide = {
                                        viewModel.hideUpdate(row.app)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = context.getString(
                                                    R.string.updates_hidden_toast,
                                                ),
                                                actionLabel = context.getString(
                                                    R.string.updates_undo,
                                                ),
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.restoreUpdate(row.app.packageName)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (!selectionMode) {
                FloatingActionButtonMenu(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    expanded = fabExpanded,
                    button = {
                        ToggleFloatingActionButton(
                            checked = fabExpanded,
                            onCheckedChange = {
                                haptics.tick()
                                fabExpanded = it
                            },
                        ) {
                            if (isChecking) {
                                RutheniaLoadingIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_refresh),
                                    contentDescription = stringResource(R.string.updates_check),
                                )
                            }
                        }
                    },
                ) {
                    FloatingActionButtonMenuItem(
                        onClick = {
                            haptics.confirm()
                            fabExpanded = false
                            // "Update all (0)" no longer pretends to work.
                            if (actionable > 0 && !isChecking) viewModel.updateAll()
                        },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = null,
                            )
                        },
                        text = { Text(stringResource(R.string.updates_update_all, actionable)) },
                    )
                    FloatingActionButtonMenuItem(
                        onClick = {
                            haptics.confirm()
                            fabExpanded = false
                            if (!isChecking) viewModel.checkUpdates()
                        },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = null,
                            )
                        },
                        text = { Text(stringResource(R.string.updates_check)) },
                    )
                }
            }
        }
    }
}
