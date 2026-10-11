/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parsing of selections from the RuStore web catalog payload.
 *
 * The fragments were captured from a real `www.rustore.ru` response with the
 * `RSC: 1` header (October 2026). The tests do not hit the network.
 */
class RuStoreWebSelectionsParserTest {

    private val json = ruStoreJson()

    @Test
    fun `selection cards are extracted with nested props`() {
        val selections = RuStoreWebSelectionsParser.parseSelections(SELECTIONS_BODY, json)

        assertEquals(2, selections.size)

        val first = selections.first()
        assertEquals("8c5a85fd-dbc5-40ca-8f56-44467f625920", first.id)
        assertEquals("Всё для таксиста", first.title)
        assertEquals("Заказы, маршруты и учёт доходов", first.subtitle)
        assertEquals("https://static.rustore.ru/selection/cover/a.jpg", first.coverUrl)

        assertNull(selections[1].subtitle)
        assertNull(selections[1].coverUrl)
    }

    @Test
    fun `apps array maps to catalog apps and skips duplicates`() {
        val apps = RuStoreWebSelectionsParser.parseApps(APPS_BODY, json)

        assertEquals(2, apps.size)
        assertEquals("ru.rostel", apps[0].packageName)
        assertEquals("Госуслуги", apps[0].name)
        assertEquals("Официальное приложение", apps[0].shortDescription)
        assertEquals(4.47, apps[0].averageUserRating!!, 0.001)
        assertEquals(275541L, apps[0].totalRatings)
        assertEquals(listOf(7), apps[0].labelIds)
        assertNull(apps[0].versionCode)

        // The second object duplicates the first's package — only one remains in the output.
        assertEquals(1, apps.count { it.packageName == "ru.rostel" })
        assertEquals("ru.yandex.metro", apps[1].packageName)
    }

    @Test
    fun `app objects are used as a fallback when no array is present`() {
        val apps = RuStoreWebSelectionsParser.parseApps(APP_OBJECTS_BODY, json)

        assertEquals(1, apps.size)
        assertEquals("c.d", apps.first().packageName)
        assertEquals("Резерв", apps.first().name)
    }

    @Test
    fun `blank and malformed payloads yield nothing`() {
        assertEquals(emptyList<RuStoreSelection>(), RuStoreWebSelectionsParser.parseSelections("", json))
        assertEquals(emptyList<CatalogApp>(), RuStoreWebSelectionsParser.parseApps("not json at all", json))
    }

    private companion object {
        val SELECTIONS_BODY = """
            ["${'$'}","${'$'}L2c","8c5a85fd-dbc5-40ca-8f56-44467f625920",
            {"id":"8c5a85fd-dbc5-40ca-8f56-44467f625920","title":"Всё для таксиста",
            "subtitle":"Заказы, маршруты и учёт доходов",
            "imageUrl":"https://static.rustore.ru/selection/cover/a.jpg",
            "compositeBackground":{"sources":["https://static.rustore.ru/x.png"]}}],
            ["${'$'}","${'$'}L2c","7b009757-acce-4bf3-a308-0954f76b6da2",
            {"id":"7b009757-acce-4bf3-a308-0954f76b6da2","title":"В помощь курьеру",
            "subtitle":"","imageUrl":""}]
        """

        val APPS_BODY = """
            {"content":{"id":"3d1069f8-e933-4c61-8615-47d677ca9906","apps":[{"appId":537791,
            "appName":"Госуслуги","packageName":"ru.rostel",
            "shortDescription":"Официальное приложение",
            "iconUrl":"https://static.rustore.ru/icon.png","averageUserRating":4.47,
            "totalRatings":275541,"labels":[{"id":7,"name":"Топ"}],
            "fileUrls":[{"fileUrl":"https://static.rustore.ru/s.png"}]},
            {"appId":1,"appName":"Дубль","packageName":"ru.rostel"},
            {"appId":593087,"appName":"Яндекс Метро","packageName":"ru.yandex.metro"}]}}
        """

        val APP_OBJECTS_BODY = """
            [["${'$'}","${'$'}L20","Резерв",{"app":{"appId":5,"appName":"Резерв","packageName":"c.d"}}]]
        """
    }
}
