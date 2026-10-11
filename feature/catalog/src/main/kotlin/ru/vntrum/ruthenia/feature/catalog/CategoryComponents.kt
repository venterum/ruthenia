/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vntrum.ruthenia.core.data.repository.HomeSection
import com.vntrum.ruthenia.core.designsystem.component.AppPosterCard
import com.vntrum.ruthenia.core.designsystem.component.ExpressiveCard
import com.vntrum.ruthenia.core.designsystem.component.RutheniaIconTile
import com.vntrum.ruthenia.core.designsystem.component.SectionHeader
import com.vntrum.ruthenia.core.designsystem.text.appCountLabel
import com.vntrum.ruthenia.core.network.CatalogApp

/**
 * Category grid: 2 columns of tiles with an icon, name, and app count. Lives on
 * the "Categories" tab — a quick overview of the whole catalog.
 */
@Composable
internal fun CategoriesGrid(
    categories: List<HomeSection>,
    onCollectionClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { section ->
                    CategoryTile(
                        section = section,
                        onClick = { onCollectionClick(section.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    section: HomeSection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RutheniaIconTile(
                painter = painterResource(categoryIcon(section.id)),
                size = 40.dp,
                iconSize = 22.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = appCountLabel(section.apps.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Category icon by its id from [com.vntrum.ruthenia.core.data.repository.HomeConfig]. */
internal fun categoryIcon(id: String): Int = when (id) {
    "banks" -> R.drawable.ic_cat_banks
    "gosuslugi" -> R.drawable.ic_cat_gosuslugi
    "messengers" -> R.drawable.ic_cat_messengers
    "transport" -> R.drawable.ic_cat_transport
    "cinema" -> R.drawable.ic_cat_cinema
    "games" -> R.drawable.ic_cat_games
    "shopping" -> R.drawable.ic_cat_shopping
    "education" -> R.drawable.ic_cat_education
    else -> R.drawable.ic_library
}

/**
 * Section header of a feed (Apps and Games): a large title with an app count
 * and an optional "All →" action that opens the full collection.
 */
@Composable
internal fun FeedSectionHeader(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    showAccent: Boolean = false,
) {
    SectionHeader(
        text = title,
        subtitle = subtitle,
        showAccent = showAccent,
        largeTitle = true,
        onClick = onClick,
        trailing = if (onClick != null) {
            { SeeAllLabel() }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
    )
}

/** "All →" hint next to a section header. */
@Composable
private fun SeeAllLabel() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.home_see_all),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Horizontal rail of compact category cards. */
@Composable
internal fun CategoryRail(
    section: HomeSection,
    onAppClick: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(section.apps, key = { it.packageName }) { app ->
            CategoryAppCard(
                app = app,
                modifier = Modifier.animateItem(),
                onClick = { onAppClick(app.packageName) },
            )
        }
    }
}

/** Compact category card for a horizontal rail. */
@Composable
private fun CategoryAppCard(
    app: CatalogApp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppPosterCard(
        title = app.name,
        iconUrl = app.iconUrl,
        rating = app.averageUserRating,
        fallbackIcon = painterResource(R.drawable.ic_library),
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Localized title of a game section. Game sections are built in the data layer
 * by id (see `HomeConfig`); their titles live here so both languages work.
 * Unknown ids keep the fallback from the data layer.
 */
@Composable
internal fun feedSectionTitle(sectionId: String, fallback: String): String = when (sectionId) {
    "game_popular" -> stringResource(R.string.games_section_popular)
    "game_arcade" -> stringResource(R.string.games_genre_arcade)
    "game_puzzle" -> stringResource(R.string.games_genre_puzzle)
    "game_strategy" -> stringResource(R.string.games_genre_strategy)
    "game_racing" -> stringResource(R.string.games_genre_racing)
    "game_shooter" -> stringResource(R.string.games_genre_shooter)
    "game_simulator" -> stringResource(R.string.games_genre_simulator)
    else -> fallback
}
