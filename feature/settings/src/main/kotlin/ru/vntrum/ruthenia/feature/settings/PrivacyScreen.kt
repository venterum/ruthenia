/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.core.designsystem.component.ExpressiveCard
import com.vntrum.ruthenia.core.designsystem.component.SectionHeader
import com.vntrum.ruthenia.core.designsystem.component.rutheniaContentWidth

/**
 * Privacy screen: an honest, exhaustive list of what the app does on the
 * network and what it stores locally. No marketing language.
 *
 * The sections are cards on the `surfaceContainerLow` tone that collapse with
 * the theme's spring animation, so long text doesn't turn into a solid "wall".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.privacy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .rutheniaContentWidth()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            PrivacySection(
                title = stringResource(R.string.privacy_short_title),
                body = stringResource(R.string.privacy_short_body),
            )
            PrivacySection(
                title = stringResource(R.string.privacy_hosts_title),
                body = stringResource(R.string.privacy_hosts_body),
            )
            PrivacySection(
                title = stringResource(R.string.privacy_transfer_title),
                body = stringResource(R.string.privacy_transfer_body),
            )
            PrivacySection(
                title = stringResource(R.string.privacy_import_title),
                body = stringResource(R.string.privacy_import_body),
            )
            PrivacySection(
                title = stringResource(R.string.privacy_downloads_title),
                body = stringResource(R.string.privacy_downloads_body),
            )
            PrivacySection(
                title = stringResource(R.string.privacy_permissions_title),
                body = stringResource(R.string.privacy_permissions_body),
            )
            PrivacySection(
                title = stringResource(R.string.privacy_storage_title),
                body = stringResource(R.string.privacy_storage_body),
            )
            PrivacySection(
                title = stringResource(R.string.privacy_certs_title),
                body = stringResource(R.string.privacy_certs_body),
            )
            PrivacySection(
                title = stringResource(R.string.privacy_never_title),
                body = stringResource(R.string.privacy_never_body),
            )
            }
        }
    }
}

@Composable
private fun PrivacySection(
    title: String,
    body: String,
    initiallyExpanded: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val motion = MaterialTheme.motionScheme
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "privacyChevron",
    )

    ExpressiveCard(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                text = title,
                trailing = {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more),
                        contentDescription = stringResource(
                            if (expanded) R.string.privacy_collapse else R.string.privacy_expand,
                        ),
                        modifier = Modifier.rotate(chevronRotation),
                    )
                },
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = motion.defaultSpatialSpec()) +
                    fadeIn(animationSpec = motion.defaultEffectsSpec()),
                exit = shrinkVertically(animationSpec = motion.defaultSpatialSpec()) +
                    fadeOut(animationSpec = motion.fastEffectsSpec()),
            ) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
