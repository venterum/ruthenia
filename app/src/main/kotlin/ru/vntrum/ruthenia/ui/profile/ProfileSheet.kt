// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vntrum.ruthenia.R
import com.vntrum.ruthenia.core.designsystem.component.RutheniaProfileAvatar
import com.vntrum.ruthenia.core.designsystem.component.RutheniaStatCard

/**
 * The local "device profile" sheet, opened from the Apps header avatar.
 *
 * There is no account: the sheet shows a locally generated seal, two on-device
 * counters, and shortcuts into the existing destinations. Settings gives way
 * from the header to here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPrivacy: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // The recommended rememberBottomSheetState takes the set of enabled
        // values; dropping PartiallyExpanded makes the profile open fully.
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ProfileContent(
            state = state,
            onOpenSettings = onOpenSettings,
            onOpenUpdates = onOpenUpdates,
            onOpenAbout = onOpenAbout,
            onOpenPrivacy = onOpenPrivacy,
            onChangeSeal = viewModel::regenerateSeal,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onOpenSettings: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onChangeSeal: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RutheniaProfileAvatar(seed = state.seed, size = 56.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
                Text(
                    text = stringResource(R.string.profile_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RutheniaStatCard(
                label = stringResource(R.string.profile_installed),
                value = state.installedCount.toString(),
                modifier = Modifier.weight(1f),
            )
            RutheniaStatCard(
                label = stringResource(R.string.profile_updates),
                value = state.updateCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        Column {
            ProfileAction(
                title = stringResource(R.string.nav_settings),
                painter = painterResource(R.drawable.ic_settings),
                onClick = onOpenSettings,
            )
            ProfileAction(
                title = stringResource(R.string.profile_updates),
                painter = painterResource(R.drawable.ic_download),
                onClick = onOpenUpdates,
            )
            ProfileAction(
                title = stringResource(R.string.profile_about),
                painter = painterResource(R.drawable.ic_info),
                onClick = onOpenAbout,
            )
            ProfileAction(
                title = stringResource(R.string.profile_privacy),
                painter = painterResource(R.drawable.ic_privacy),
                onClick = onOpenPrivacy,
            )
        }

        TextButton(
            onClick = onChangeSeal,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.profile_change_seal))
        }
    }
}

@Composable
private fun ProfileAction(
    title: String,
    painter: Painter,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
