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
    /** Optional one-line note (from [CuratedCategory.description]). */
    val description: String? = null,
)

/** A curated catalog app: a display name and an exact RuStore package name. */
data class CuratedApp(
    val name: String,
    val packageName: String,
)

/**
 * A curated home category: an ordered, hand-picked app list. [description]
 * is an optional one-line note shown next to the title.
 */
data class CuratedCategory(
    val id: String,
    val title: String,
    val apps: List<CuratedApp>,
    val description: String? = null,
)

/**
 * Curated home feed content.
 *
 * All unconfigured and non-personal: sections are fixed, hand-picked app
 * lists resolved by exact package name. No profiling whatsoever.
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

    /** Whether [sectionId] belongs to the curated app feed (categories + recommended). */
    fun isAppSection(sectionId: String): Boolean =
        sectionId == RECOMMENDED_SECTION_ID || categories.any { it.id == sectionId }

    /** Optional section subtitle, by id. */
    fun descriptionFor(sectionId: String): String? = when (sectionId) {
        RECOMMENDED_SECTION_ID -> RECOMMENDED_DESCRIPTION
        else -> categories.firstOrNull { it.id == sectionId }?.description
    }

    /** How many results to request to find the exact package name. */
    const val RECOMMENDED_SEARCH_SIZE = 10

    /** How many apps to show in a search-query section (games). */
    const val SECTION_PAGE_SIZE = 12

    /** A games section: a predefined search query. */
    data class HomeCategory(val id: String, val title: String, val query: String)

    /** "Recommended" — the curated app list (all groups merged). */
    const val RECOMMENDED_DESCRIPTION = "Подборка от разработчика: самое полезное и нужное"
    val featured: List<CuratedApp> = listOf(
        CuratedApp("СберБанк Онлайн", "ru.sberbankmobile"),
        CuratedApp("Т-Банк", "com.idamob.tinkoff.android"),
        CuratedApp("Госуслуги", "ru.rostel"),
        CuratedApp("Mir Pay", "ru.nspk.mirpay"),
        CuratedApp("Telegram", "org.telegram.messenger.web"),
        CuratedApp("ВКонтакте", "com.vkontakte.android"),
        CuratedApp("2ГИС", "ru.dublgis.dgismobile"),
        CuratedApp("Яндекс Карты и Навигатор", "ru.yandex.yandexmaps"),
        CuratedApp("Яндекс Go", "ru.yandex.taxi"),
        CuratedApp("OZON", "ru.ozon.app.android"),
        CuratedApp("Wildberries", "com.wildberries.ru"),
        CuratedApp("Авито", "com.avito.android"),
        CuratedApp("Яндекс Лавка", "com.yandex.lavka"),
        CuratedApp("Самокат", "ru.sbcs.store"),
        CuratedApp("Яндекс Еда", "ru.foodfox.client"),
        CuratedApp("Купер (СберМаркет)", "ru.instamart"),
        CuratedApp("ВкусВилл", "ru.vkusvill"),
        CuratedApp("Кинопоиск", "ru.kinopoisk"),
        CuratedApp("Яндекс Музыка", "ru.yandex.music"),
        CuratedApp("VK Музыка", "com.uma.musicvk"),
        CuratedApp("VK Видео", "com.vk.vkvideo"),
        CuratedApp("RUTUBE", "ru.rutube.app"),
        CuratedApp("Mail: почта, облако, календарь", "ru.mail.mailapp"),
        CuratedApp("Яндекс Диск", "ru.yandex.disk"),
        CuratedApp("Яндекс Переводчик", "ru.yandex.translate"),
        CuratedApp("Яндекс Погода", "ru.yandex.weatherplugin"),
    )

    /** Curated catalog categories, in display order. */
    val categories: List<CuratedCategory> = listOf(
        CuratedCategory(
            id = "banks",
            title = "Банки",
            apps = listOf(
                CuratedApp("СберБанк Онлайн", "ru.sberbankmobile"),
                CuratedApp("Т-Банк", "com.idamob.tinkoff.android"),
                CuratedApp("ВТБ Онлайн", "ru.vtb24.mobilebanking.android"),
                CuratedApp("Альфа-Банк", "ru.alfabank.mobile.android"),
                CuratedApp("Газпромбанк", "ru.gazprombank.android.mobilebank.app"),
                CuratedApp("Райффайзен Онлайн", "ru.raiffeisennews"),
                CuratedApp("Россельхозбанк", "ru.rshb.dbo"),
                CuratedApp("Халва – Совкомбанк", "ru.sovcomcard.halva.v1"),
                CuratedApp("Ozon Банк", "ru.ozon.fintech.finance"),
                CuratedApp("Почта Банк", "ru.letobank.Prometheus"),
                CuratedApp("Банк Санкт-Петербург", "ru.bspb"),
                CuratedApp("МТС Банк", "ru.mts.bank"),
                CuratedApp("Mir Pay", "ru.nspk.mirpay"),
                CuratedApp("СБПэй", "ru.nspk.sbpay"),
                CuratedApp("Яндекс Пэй", "com.yandex.bank"),
                CuratedApp("ПСБ", "logo.com.mbanking"),
                CuratedApp("МКБ", "ru.mkb.mobile"),
                CuratedApp("Дом.РФ", "com.bank.domrf.v2"),
                CuratedApp("Точка", "ru.zhuck.webapp"),
                CuratedApp("Уралсиб", "ru.bankuralsib.mb.android"),
                CuratedApp("Русский Стандарт", "ru.simpls.brs2.mobbank"),
            ),
        ),
        CuratedCategory(
            id = "gov",
            title = "Госуслуги",
            apps = listOf(
                CuratedApp("Госуслуги", "ru.rostel"),
                CuratedApp("Госключ", "ru.gosuslugi.goskey"),
                CuratedApp("Госуслуги Дом", "ru.sigma.gisgkh"),
                CuratedApp("Госуслуги Авто", "ru.gosuslugi.auto"),
                CuratedApp("Госуслуги. Решаем вместе", "ru.gosuslugi.pos"),
                CuratedApp("Госуслуги Культура", "ru.gosuslugi.culture"),
                CuratedApp("Работа России", "ru.trudvsem.mobile"),
                CuratedApp("Штрафы ГИБДД", "ru.gibdd_pay.app"),
                CuratedApp("Налоги ФЛ", "ru.fns.lkfl"),
                CuratedApp("Мой налог", "com.gnivts.selfemployed"),
                CuratedApp("Почта России", "com.octopod.russianpost.client.android"),
            ),
        ),
        CuratedCategory(
            id = "chat",
            title = "Общение",
            apps = listOf(
                CuratedApp("МАКС", "ru.oneme.app"),
                CuratedApp("Telegram", "org.telegram.messenger.web"),
                CuratedApp("ВКонтакте", "com.vkontakte.android"),
                CuratedApp("VK Мессенджер", "com.vk.im"),
                CuratedApp("Одноклассники", "ru.ok.android"),
                CuratedApp("Сферум", "ru.sferum.android"),
                CuratedApp("TamTam", "biz.tamtam.android"),
                CuratedApp("Яндекс Телемост", "ru.yandex.telemost"),
                CuratedApp("VK Звонки", "com.vk.calls"),
            ),
        ),
        CuratedCategory(
            id = "shopping",
            title = "Покупки",
            apps = listOf(
                CuratedApp("OZON", "ru.ozon.app.android"),
                CuratedApp("Wildberries", "com.wildberries.ru"),
                CuratedApp("Яндекс Маркет", "ru.beru.android"),
                CuratedApp("Авито", "com.avito.android"),
                CuratedApp("Лента Онлайн", "ru.lenta.lentochka"),
                CuratedApp("Мегамаркет", "ru.megamarket.marketplace"),
                CuratedApp("Lamoda", "com.lamoda.lite"),
                CuratedApp("Золотое яблоко", "goldapple.ru.goldapple.customers"),
                CuratedApp("Летуаль", "ru.letu"),
                CuratedApp("DNS", "ru.dns.shop.android"),
                CuratedApp("М.Видео", "ru.filit.mvideo.b2c"),
                CuratedApp("Спортмастер", "ru.sportmaster.app"),
            ),
        ),
        CuratedCategory(
            id = "food",
            title = "Еда",
            apps = listOf(
                CuratedApp("Яндекс Еда", "ru.foodfox.client"),
                CuratedApp("Яндекс Лавка", "com.yandex.lavka"),
                CuratedApp("Самокат", "ru.sbcs.store"),
                CuratedApp("Купер (СберМаркет)", "ru.instamart"),
                CuratedApp("ВкусВилл", "ru.vkusvill"),
                CuratedApp("Перекрёсток", "ru.perekrestok.app"),
                CuratedApp("Пятёрочка", "ru.pyaterochka.app.browser"),
                CuratedApp("Магнит", "ru.tander.magnit"),
                CuratedApp("Додо Пицца", "ru.dodopizza.app"),
                CuratedApp("Вкусно — и точка", "com.apegroup.mcdonaldsrussia"),
                CuratedApp("Burger King", "ru.burgerking"),
                CuratedApp("Rostic's", "ru.kfc.kfc_delivery"),
                CuratedApp("Сушивок", "ru.devcluster.sushiwok"),
            ),
        ),
        CuratedCategory(
            id = "transport",
            title = "Транспорт",
            apps = listOf(
                CuratedApp("Яндекс Go", "ru.yandex.taxi"),
                CuratedApp("2ГИС", "ru.dublgis.dgismobile"),
                CuratedApp("Яндекс Карты и Навигатор", "ru.yandex.yandexmaps"),
                CuratedApp("Яндекс Навигатор", "ru.yandex.yandexnavi"),
                CuratedApp("Яндекс Метро", "ru.yandex.metro"),
                CuratedApp("Подорожник", "com.orgp.spbpodorozhnik"),
                CuratedApp("Метро Москвы", "ru.mosmetro.metro"),
                CuratedApp("Ситимобил", "com.citymobil"),
                CuratedApp("Whoosh", "com.punicapp.whoosh"),
                CuratedApp("Urent", "ru.urentbike.app"),
                CuratedApp("Аэроэкспресс", "ru.lynx.aero"),
                CuratedApp("Парковки Москвы", "ru.mosparking.appnew"),
            ),
        ),
        CuratedCategory(
            id = "entertainment",
            title = "Развлечения",
            description = "Видео, музыка, книги и так далее",
            apps = listOf(
                CuratedApp("Кинопоиск", "ru.kinopoisk"),
                CuratedApp("Иви", "ru.ivi.client"),
                CuratedApp("Okko", "ru.more.play"),
                CuratedApp("Wink", "ru.rt.video.app.mobile"),
                CuratedApp("PREMIER", "gpm.tnt_premier"),
                CuratedApp("КИОН", "ru.mts.mtstv"),
                CuratedApp("RUTUBE", "ru.rutube.app"),
                CuratedApp("VK Видео", "com.vk.vkvideo"),
                CuratedApp("Яндекс Музыка", "ru.yandex.music"),
                CuratedApp("VK Музыка", "com.uma.musicvk"),
                CuratedApp("Звук", "com.zvooq.openplay"),
                CuratedApp("Литрес", "ru.litres.android"),
                CuratedApp("Афиша", "ru.afisha.android"),
                CuratedApp("MyBook", "ru.mybook"),
                CuratedApp("МТС Музыка", "ru.mts.music.android"),
                CuratedApp("Яндекс Книги", "ru.plus.bookmate"),
                CuratedApp("Boosty", "to.boosty.mobile"),
                CuratedApp("Яндекс Афиша", "ru.yandex.mobile.afisha"),
            ),
        ),
        CuratedCategory(
            id = "travel",
            title = "Путешествия",
            apps = listOf(
                CuratedApp("Яндекс Путешествия", "ru.yandex.travel"),
                CuratedApp("Авиасейлс", "ru.aviasales"),
                CuratedApp("Островок", "ru.ostrovok.android"),
                CuratedApp("Туту", "ru.tutu.tutu_emp"),
                CuratedApp("РЖД Пассажирам", "ru.rzd.pass"),
                CuratedApp("Яндекс Электрички и ЖД билеты", "ru.yandex.rasp"),
                CuratedApp("Суточно", "ru.sutochno_redesign"),
                CuratedApp("OneTwoTrip", "com.onetwotrip.onetwotrip"),
            ),
        ),
        CuratedCategory(
            id = "education",
            title = "Образование",
            apps = listOf(
                CuratedApp("Сферум", "ru.sferum.android"),
                CuratedApp("Учи.ру", "com.uchi.app"),
                CuratedApp("Фоксфорд: Учебник и тренажёр", "ru.slearn.app"),
                CuratedApp("Дневник МЭШ", "ru.mes.dnevnik"),
                CuratedApp("Журнал МЭШ", "ru.mos.school.journal"),
                CuratedApp("Яндекс Переводчик", "ru.yandex.translate"),
                CuratedApp("Stepik", "org.stepic.droid"),
                CuratedApp("Skyeng", "skyeng.words.prod"),
                CuratedApp("Нетология", "ru.netology"),
                CuratedApp("Умскул", "net.umschool.umschool_flutter.prod"),
                CuratedApp("Duolingo", "com.duolingo"),
            ),
        ),
    )

    /** Every distinct curated package across the app feed (featured + categories). */
    val curatedPackages: Set<String> = (featured + categories.flatMap { it.apps })
        .mapTo(LinkedHashSet()) { it.packageName }

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
}
