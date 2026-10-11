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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vntrum.ruthenia.R
import com.vntrum.ruthenia.core.data.identity.ProfileEmblem
import com.vntrum.ruthenia.core.data.identity.TOKEN_MOTIF_VARIANTS
import com.vntrum.ruthenia.core.data.identity.TokenRing
import com.vntrum.ruthenia.core.data.identity.tokenRotation
import com.vntrum.ruthenia.core.data.settings.AccentPalette
import com.vntrum.ruthenia.core.designsystem.component.RutheniaStatCard
import com.vntrum.ruthenia.core.designsystem.component.RutheniaToken
import com.vntrum.ruthenia.core.designsystem.component.RutheniaTokenRing
import com.vntrum.ruthenia.core.designsystem.component.SectionHeader
import com.vntrum.ruthenia.core.designsystem.component.rutheniaTokenRingByName
import com.vntrum.ruthenia.core.designsystem.haptics.rememberRutheniaHaptics
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaAccent
import com.vntrum.ruthenia.core.designsystem.theme.primaryColor
import com.vntrum.ruthenia.core.designsystem.theme.rutheniaAccentByName
import java.text.DateFormat
import java.util.Date

/**
 * The local "device token" sheet, opened from the Apps header.
 *
 * There is no account: the sheet shows the personalised token, the playful rank
 * derived from the library, two on-device counters, and shortcuts into the
 * existing destinations. Settings gives way from the header to here.
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
            onEmblemChange = viewModel::setEmblem,
            onShuffle = viewModel::shuffleEmblem,
            onRegenerateCodename = viewModel::regenerateCodename,
            onOpenSettings = onOpenSettings,
            onOpenUpdates = onOpenUpdates,
            onOpenAbout = onOpenAbout,
            onOpenPrivacy = onOpenPrivacy,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onEmblemChange: (ProfileEmblem) -> Unit,
    onShuffle: () -> Unit,
    onRegenerateCodename: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val rank = state.rank
    val accent = rutheniaAccentByName(state.emblem.accent.name)
    val ring = rutheniaTokenRingByName(state.emblem.ring.name)
    val rotation = tokenRotation(state.seed)
    val haptics = rememberRutheniaHaptics()
    val codename = codenameFor(
        seed = state.seed,
        adjectives = stringArrayResource(R.array.profile_codename_adjectives).toList(),
        nouns = stringArrayResource(R.array.profile_codename_nouns).toList(),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TokenHero(
            rank = rank,
            motif = state.emblem.motif,
            accent = accent,
            ring = ring,
            rotation = rotation,
            codename = codename,
            firstSeen = state.firstSeen,
            onRegenerateCodename = {
                haptics.tick()
                onRegenerateCodename()
            },
        )

        RankProgress(rank = rank, installedCount = state.installedCount)

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

        TokenCustomization(
            emblem = state.emblem,
            accent = accent,
            onEmblemChange = { emblem ->
                haptics.tick()
                onEmblemChange(emblem)
            },
            onShuffle = {
                haptics.tick()
                onShuffle()
            },
        )

        ProfileActions(
            onOpenSettings = onOpenSettings,
            onOpenUpdates = onOpenUpdates,
            onOpenAbout = onOpenAbout,
            onOpenPrivacy = onOpenPrivacy,
        )
    }
}

/** Hero: the token itself, the rank, the codename and the "with us since" line. */
@Composable
private fun TokenHero(
    rank: TokenRank,
    motif: Int,
    accent: RutheniaAccent,
    ring: RutheniaTokenRing,
    rotation: Float,
    codename: String,
    firstSeen: Long,
    onRegenerateCodename: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RutheniaToken(
            motif = motif,
            accent = accent,
            ring = ring,
            rotation = rotation,
            size = 76.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(rank.titleRes),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            // The Latin half is intentionally not translated: it is the
            // "engraved" counterpart of the localised rank title.
            Text(
                text = "${rank.numeral} · ${rank.latin}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = codename,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                IconButton(
                    onClick = onRegenerateCodename,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.profile_codename_reroll),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
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
    }
}

/** Progress towards the next rank, or a note that the top has been reached. */
@Composable
private fun RankProgress(rank: TokenRank, installedCount: Int) {
    val next = TokenRank.next(rank)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { TokenRank.progress(rank, installedCount) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = if (next != null) {
                val remaining = TokenRank.appsToNext(rank, installedCount) ?: 1
                pluralStringResource(
                    R.plurals.profile_rank_progress,
                    remaining,
                    stringResource(next.titleRes),
                    remaining,
                )
            } else {
                stringResource(R.string.profile_rank_max)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Motif, accent and ring pickers plus the reshuffle action. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TokenCustomization(
    emblem: ProfileEmblem,
    accent: RutheniaAccent,
    onEmblemChange: (ProfileEmblem) -> Unit,
    onShuffle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            text = stringResource(R.string.profile_customize),
            showAccent = true,
            trailing = {
                IconButton(onClick = onShuffle) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shuffle),
                        contentDescription = stringResource(R.string.profile_shuffle),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                        .clickable { onEmblemChange(emblem.copy(motif = index)) },
                    contentAlignment = Alignment.Center,
                ) {
                    RutheniaToken(
                        motif = index,
                        accent = accent,
                        ring = RutheniaTokenRing.NONE,
                        rotation = 0f,
                        size = 34.dp,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentPalette.entries.forEach { palette ->
                val paletteAccent = rutheniaAccentByName(palette.name)
                val selected = palette == emblem.accent
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(paletteAccent.primaryColor())
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .clickable { onEmblemChange(emblem.copy(accent = palette)) },
                )
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TokenRing.entries.forEachIndexed { index, ringStyle ->
                SegmentedButton(
                    selected = ringStyle == emblem.ring,
                    onClick = { onEmblemChange(emblem.copy(ring = ringStyle)) },
                    shape = SegmentedButtonDefaults.itemShape(index, TokenRing.entries.size),
                    label = { Text(stringResource(ringStyle.labelRes())) },
                )
            }
        }
    }
}

/** The four shortcuts into the existing destinations, as a 2×2 tile grid. */
@Composable
private fun ProfileActions(
    onOpenSettings: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileActionTile(
                titleRes = R.string.nav_settings,
                iconRes = R.drawable.ic_settings,
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
            ProfileActionTile(
                titleRes = R.string.profile_updates,
                iconRes = R.drawable.ic_download,
                onClick = onOpenUpdates,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileActionTile(
                titleRes = R.string.profile_about,
                iconRes = R.drawable.ic_info,
                onClick = onOpenAbout,
                modifier = Modifier.weight(1f),
            )
            ProfileActionTile(
                titleRes = R.string.profile_privacy,
                iconRes = R.drawable.ic_privacy,
                onClick = onOpenPrivacy,
                modifier = Modifier.weight(1f),
            )
        }
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
    TokenRing.TEETH -> R.string.profile_ring_teeth
}
