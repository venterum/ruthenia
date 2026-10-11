// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.ui.profile

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vntrum.ruthenia.R
import com.vntrum.ruthenia.core.data.identity.ProfileEmblem
import com.vntrum.ruthenia.core.data.identity.TOKEN_MOTIF_VARIANTS
import com.vntrum.ruthenia.core.data.identity.TokenColor
import com.vntrum.ruthenia.core.data.identity.TokenRing
import com.vntrum.ruthenia.core.data.identity.tokenRotation
import com.vntrum.ruthenia.core.designsystem.component.RutheniaStatCard
import com.vntrum.ruthenia.core.designsystem.component.RutheniaToken
import com.vntrum.ruthenia.core.designsystem.component.RutheniaTokenRing
import com.vntrum.ruthenia.core.designsystem.component.rutheniaTokenRingByName
import com.vntrum.ruthenia.core.designsystem.haptics.rememberRutheniaHaptics
import com.vntrum.ruthenia.core.designsystem.theme.frameColor
import com.vntrum.ruthenia.core.designsystem.theme.rutheniaTokenColorByName
import java.text.DateFormat
import java.util.Date

/**
 * The local "device token" sheet, opened from the Apps header.
 *
 * There is no account: the sheet shows the personalised token with its codename,
 * two on-device counters, a button into the token setup, and shortcuts into the
 * existing destinations. Settings gives way from the header to here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCustomization by rememberSaveable { mutableStateOf(false) }

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
        if (showCustomization) {
            TokenCustomization(
                emblem = state.emblem,
                onEmblemChange = viewModel::setEmblem,
                onBack = { showCustomization = false },
            )
        } else {
            ProfileContent(
                state = state,
                onRandomize = viewModel::randomizeToken,
                onCustomize = { showCustomization = true },
                onOpenSettings = onOpenSettings,
                onOpenUpdates = onOpenUpdates,
                onOpenAbout = onOpenAbout,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onRandomize: () -> Unit,
    onCustomize: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val haptics = rememberRutheniaHaptics()
    val codename = codenameFor(
        seed = state.seed,
        adjectives = stringArrayResource(R.array.profile_codename_adjectives).toList(),
        nouns = stringArrayResource(R.array.profile_codename_nouns).toList(),
    )
    var showInfo by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TokenHero(
            emblem = state.emblem,
            seed = state.seed,
            codename = codename,
            firstSeen = state.firstSeen,
            onRandomize = {
                haptics.confirm()
                onRandomize()
            },
        )

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
                onClick = {
                    haptics.tick()
                    onOpenUpdates()
                },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    haptics.tick()
                    onCustomize()
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.profile_customize))
            }
            IconButton(
                onClick = {
                    haptics.tick()
                    showInfo = true
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = stringResource(R.string.profile_info),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ProfileActions(
            onOpenSettings = onOpenSettings,
            onOpenAbout = onOpenAbout,
        )
    }

    if (showInfo) {
        TokenInfoDialog(onDismiss = { showInfo = false })
    }
}

/** A short, playful explanation of what the token is and why it exists. */
@Composable
private fun TokenInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_info_title)) },
        text = { Text(stringResource(R.string.profile_info_body)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_info_ok))
            }
        },
    )
}

/**
 * Hero: the token itself, its codename and the single randomise button — the
 * name and the look are generated together, because the name is part of the token.
 */
@Composable
private fun TokenHero(
    emblem: ProfileEmblem,
    seed: String,
    codename: String,
    firstSeen: Long,
    onRandomize: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RutheniaToken(
            motif = emblem.motif,
            accent = rutheniaTokenColorByName(emblem.accent.name),
            ring = rutheniaTokenRingByName(emblem.ring.name),
            ringAccent = rutheniaTokenColorByName(emblem.ringColor.name),
            rotation = tokenRotation(seed),
            size = 76.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = codename,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (firstSeen > 0L) {
                Text(
                    text = stringResource(
                        R.string.profile_since_value,
                        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(firstSeen)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRandomize) {
            Icon(
                painter = painterResource(R.drawable.ic_shuffle),
                contentDescription = stringResource(R.string.profile_randomize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The token setup, opened from the "token setup" button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TokenCustomization(
    emblem: ProfileEmblem,
    onEmblemChange: (ProfileEmblem) -> Unit,
    onBack: () -> Unit,
) {
    val haptics = rememberRutheniaHaptics()
    val accent = rutheniaTokenColorByName(emblem.accent.name)
    val ringAccent = rutheniaTokenColorByName(emblem.ringColor.name)
    val ring = rutheniaTokenRingByName(emblem.ring.name)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.profile_back),
                )
            }
            Text(
                text = stringResource(R.string.profile_customize),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            RutheniaToken(
                motif = emblem.motif,
                accent = accent,
                ring = ring,
                ringAccent = ringAccent,
                rotation = 0f,
                size = 104.dp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CustomizationLabel(stringResource(R.string.profile_motif))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TOKEN_MOTIF_VARIANTS) { index ->
                    val selected = index == emblem.motif
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .clickable {
                                haptics.tick()
                                onEmblemChange(emblem.copy(motif = index))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        RutheniaToken(
                            motif = index,
                            accent = accent,
                            ring = RutheniaTokenRing.THIN,
                            ringAccent = ringAccent,
                            rotation = 0f,
                            size = 34.dp,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CustomizationLabel(stringResource(R.string.profile_color))
            ColorDotRow(
                selected = emblem.accent,
                onSelect = {
                    haptics.tick()
                    onEmblemChange(emblem.copy(accent = it))
                },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CustomizationLabel(stringResource(R.string.profile_ring))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TokenRing.entries.forEachIndexed { index, ringStyle ->
                    SegmentedButton(
                        selected = ringStyle == emblem.ring,
                        onClick = {
                            haptics.tick()
                            onEmblemChange(emblem.copy(ring = ringStyle))
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, TokenRing.entries.size),
                        label = { Text(stringResource(ringStyle.labelRes())) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CustomizationLabel(stringResource(R.string.profile_ring_color))
            ColorDotRow(
                selected = emblem.ringColor,
                onSelect = {
                    haptics.tick()
                    onEmblemChange(emblem.copy(ringColor = it))
                },
            )
        }
    }
}

@Composable
private fun CustomizationLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The shared token palette, as selectable dots, two rows of five. */
@Composable
private fun ColorDotRow(
    selected: TokenColor,
    onSelect: (TokenColor) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TokenColor.entries.chunked(COLORS_PER_ROW).forEach { rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowColors.forEach { color ->
                    val isSelected = color == selected
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(rutheniaTokenColorByName(color.name).frameColor())
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            )
                            .clickable { onSelect(color) },
                    )
                }
            }
        }
    }
}

/** Shortcuts into the remaining destinations, as a row of tiles. */
@Composable
private fun ProfileActions(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ProfileActionTile(
            titleRes = R.string.nav_settings,
            iconRes = R.drawable.ic_settings,
            onClick = onOpenSettings,
            modifier = Modifier.weight(1f),
        )
        ProfileActionTile(
            titleRes = R.string.profile_about,
            iconRes = R.drawable.ic_info,
            onClick = onOpenAbout,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ProfileActionTile(
    @StringRes titleRes: Int,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@StringRes
private fun TokenRing.labelRes(): Int = when (this) {
    TokenRing.NONE -> R.string.profile_ring_none
    TokenRing.THIN -> R.string.profile_ring_thin
    TokenRing.THICK -> R.string.profile_ring_thick
    TokenRing.TEETH -> R.string.profile_ring_teeth
}

/** Colours per row in the palette pickers. */
private const val COLORS_PER_ROW = 5
