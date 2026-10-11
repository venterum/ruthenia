/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.repository

import com.vntrum.ruthenia.core.network.CatalogApp

/** A home feed section: title and apps in display order. */
data class HomeSection(
    val id: String,
    val title: String,
    val apps: List<CatalogApp>,
    /**
     * Special selection ("Recommended"): shown on the home screen as a large
     * carousel rather than a regular compact category row.
     */
    val featured: Boolean = false,
)

/**
 * Curated home feed content.
 *
 * All unconfigured and non-personal: sections are predefined search queries,
 * and "Recommended" is a fixed list of apps resolved by exact package name.
 * No profiling whatsoever.
 */
internal object HomeConfig {

    const val RECOMMENDED_SECTION_ID = "recommended"
    const val RECOMMENDED_SECTION_TITLE = "Рекомендуем"

    /**
     * Game sections on the "Games" page. They share the home feed cache table
     * but are marked with this id prefix, so the two feeds do not mix.
     */
    const val GAMES_SECTION_PREFIX = "game_"
    const val GAMES_POPULAR_SECTION_ID = "game_popular"
    const val GAMES_POPULAR_SECTION_TITLE = "Популярные игры"
    const val GAMES_POPULAR_QUERY = "игра"

    /** Whether the section belongs to the curated games feed. */
    fun isGameSection(sectionId: String): Boolean = sectionId.startsWith(GAMES_SECTION_PREFIX)

    /** How many results to request to find the exact package name. */
    const val RECOMMENDED_SEARCH_SIZE = 10

    /** How many apps to show in a section. */
    const val SECTION_PAGE_SIZE = 12

    data class HomeCategory(val id: String, val title: String, val query: String)

    data class RecommendedApp(val query: String, val packageName: String)

    val categories: List<HomeCategory> = listOf(
        HomeCategory("banks", "Банки", "банк"),
        HomeCategory("gosuslugi", "Госуслуги", "госуслуги"),
        HomeCategory("messengers", "Общение", "мессенджер"),
        HomeCategory("transport", "Транспорт", "такси"),
        HomeCategory("cinema", "Кино и видео", "кино"),
        HomeCategory("games", "Игры", "игра"),
        HomeCategory("shopping", "Покупки", "магазин"),
        HomeCategory("education", "Образование", "образование"),
    )

    /**
     * Game genres for the "Games" page. Titles are localized in the UI layer
     * by section id ([GAMES_SECTION_PREFIX]); the values here are the fallback.
     */
    val gameGenres: List<HomeCategory> = listOf(
        HomeCategory("game_arcade", "Аркады", "аркада"),
        HomeCategory("game_puzzle", "Головоломки", "головоломка"),
        HomeCategory("game_strategy", "Стратегии", "стратегия"),
        HomeCategory("game_racing", "Гонки", "гонки"),
        HomeCategory("game_shooter", "Шутеры", "шутер"),
        HomeCategory("game_simulator", "Симуляторы", "симулятор"),
    )

    /**
     * "Recommended" — a fixed list of package names resolved by exact search.
     */
    val recommended: List<RecommendedApp> = listOf(
        RecommendedApp("сбербанк", "ru.sberbankmobile"),
        RecommendedApp("госуслуги", "ru.rostel"),
        RecommendedApp("т-банк", "com.idamob.tinkoff.android"),
        RecommendedApp("вконтакте", "com.vkontakte.android"),
        RecommendedApp("wildberries", "com.wildberries.ru"),
        RecommendedApp("ozon", "ru.ozon.app.android"),
        RecommendedApp("альфа-банк", "ru.alfabank.mobile.android"),
        RecommendedApp("mail", "ru.mail.mailapp"),
        RecommendedApp("яндекс музыка", "ru.yandex.music"),
        RecommendedApp("telegram", "org.telegram.messenger.web"),
        RecommendedApp("сбер инвестиции", "ru.sberbank.investor"),
        RecommendedApp("макс", "ru.oneme.app"),
    )
}
