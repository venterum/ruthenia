/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing of real RuStore responses.
 *
 * The response bodies were captured by a probe script (September 2026) and are kept in the test
 * as strings: the tests do NOT hit the network and do not depend on API availability.
 */
class RuStoreParsingTest {

    private val json = ruStoreJson()

    @Test
    fun `search response maps to catalog apps and ignores unknown fields`() {
        val envelope = json.decodeFromString<SearchEnvelope>(SEARCH_BODY)
        val apps = envelope.body?.content.orEmpty().map(SearchItemDto::toModel)

        assertEquals(2, apps.size)

        val first = apps.first()
        assertEquals(462271L, first.appId)
        assertEquals("ru.sberbankmobile", first.packageName)
        assertEquals("СберБанк Онлайн", first.name)
        assertEquals("Больше чем банк", first.shortDescription)
        assertEquals(2026062216L, first.versionCode)
        assertEquals(26, first.minSdkVersion)
        assertTrue(first.iconUrl!!.startsWith("https://static-m.rustore.ru/"))
        assertEquals(4.82, first.averageUserRating!!, 0.001)
        assertEquals(4382218L, first.totalRatings)
        assertEquals("90 млн +", first.downloadsText)
        assertEquals(listOf(7), first.labelIds)

        val second = apps[1]
        assertEquals("by.bpssberbank.sbol", second.packageName)
        assertNull(second.iconUrl)
    }

    @Test
    fun `overall info maps to app details`() {
        val details = json.decodeFromString<AppInfoEnvelope>(OVERALL_INFO_BODY).body!!.toModel()

        assertEquals(462271L, details.appId)
        assertEquals("ru.sberbankmobile", details.packageName)
        assertEquals("СберБанк Онлайн", details.name)
        assertEquals("ПАО Сбербанк", details.publisher)
        assertEquals("17.12.0", details.versionName)
        assertEquals(2026062216L, details.versionCode)
        assertEquals("2026-09-10T12:47:46.954+00:00", details.updatedAt)
        assertEquals(125116455L, details.fileSize)
        assertTrue(details.whatsNew!!.contains("самозанятых"))
        assertFalse(details.isAggregated)
        assertEquals(listOf("finance"), details.categories)
        assertEquals("a65961e0", details.publicCompanyId)
        assertEquals("Больше чем банк", details.shortDescription)
        assertEquals("Полное описание приложения.", details.fullDescription)
        assertEquals("https://www.sberbank.ru", details.website)
        assertEquals("0+", details.ageRestriction)
        assertEquals(
            listOf(
                "https://static.rustore.ru/screen-1.png",
                "https://static.rustore.ru/screen-2.png",
            ),
            details.screenshots,
        )
        assertEquals(90000000L, details.downloads)
        assertEquals(listOf(7), details.labelIds)
        assertEquals("deadbeef", details.expectedSignature)
    }

    @Test
    fun `developer apps page maps elements and continuation`() {
        val body = json.decodeFromString<DevAppsEnvelope>(DEV_APPS_BODY).body!!
        val apps = body.elements.map(SearchItemDto::toModel)

        assertEquals(2, apps.size)
        assertEquals("ru.sberbankmobile", apps[0].packageName)
        assertEquals("СберБанк Онлайн", apps[0].name)
        assertEquals(4.82, apps[0].averageUserRating!!, 0.001)
        assertEquals("466879", body.continuation)
    }

    @Test
    fun `aggregated card is flagged when aggregatorInfo is present`() {
        val details = json.decodeFromString<AppInfoEnvelope>(AGGREGATED_BODY).body!!.toModel()
        assertTrue(details.isAggregated)
    }

    @Test
    fun `download link response maps both urls and version code`() {
        val response = json.decodeFromString<DownloadLinkResponse>(DOWNLOAD_LINK_BODY)

        assertEquals(462271L, response.appId)
        assertEquals(2026062216L, response.versionCode)
        assertEquals(2, response.downloadUrls.size)
        assertEquals(83054649L, response.downloadUrls[0].size)
        assertEquals("589d6e937dd8f88c", response.downloadUrls[0].hash)
        assertTrue(response.downloadUrls[1].url.endsWith(".zip"))
    }

    private companion object {

        const val SEARCH_BODY = """
{"code":"OK","message":null,"body":{"content":[
{"appId":462271,"packageName":"ru.sberbankmobile","minSdkVersion":26,"appName":"СберБанк Онлайн",
 "shortDescription":"Больше чем банк",
 "iconUrl":"https://static-m.rustore.ru/apk/462271/content/ICON/f1b3c68a-b734-48ce-b62f-490208d3fa0e.png",
 "appType":"MAIN","versionCode":2026062216,"price":0,"averageUserRating":4.82,
 "totalRatings":4382218,"labelIds":[7],"isNavSuggest":true,"roundedDownloadsText":"90 млн +"},
{"appId":2063721753,"packageName":"by.bpssberbank.sbol","minSdkVersion":26,
 "appName":"СберБанк Онлайн Беларусь","shortDescription":"Это ваш личный помощник.",
 "appType":"MAIN","versionCode":283,"price":0,"averageUserRating":4.43,"totalRatings":49,
 "labelIds":[],"isNavSuggest":false,"roundedDownloadsText":"200 тыс +"}
]}}
"""

        const val OVERALL_INFO_BODY = """
{"code":"OK","message":null,"body":{
 "appId":462271,"packageName":"ru.sberbankmobile","appName":"СберБанк Онлайн",
 "categories":["finance"],"companyName":"ПАО Сбербанк","shortDescription":"Больше чем банк",
 "fileSize":125116455,"versionName":"17.12.0","appType":"MAIN","downloads":90000000,"price":0,
 "appVerUpdatedAt":"2026-09-10T12:47:46.954+00:00","aggregatorInfo":null,"video":null,
 "publicCompanyId":"a65961e0","labelIds":[7],"versionCode":2026062216,"minSdkVersion":26,
 "targetSdkVersion":36,"maxSdkVersion":0,
 "whatsNew":"\nРаботать на себя ещё проще - на главном экране появился раздел для самозанятых.",
 "iconUrl":"https://static-m.rustore.ru/apk/462271/content/ICON/f1b3c68a.png",
 "fullDescription":"Полное описание приложения.","website":"https://www.sberbank.ru",
 "ageRestriction":{"category":"0+","name":"Для всех","description":"...","imageUrl":"https://static.rustore.ru/age.svg"},
 "fileUrls":[
   {"fileUrl":"https://static.rustore.ru/screen-1.png","ordinal":1,"type":"SCREENSHOT","orientation":"PORTRAIT"},
   {"fileUrl":"https://static.rustore.ru/video.mp4","ordinal":0,"type":"VIDEO","orientation":"LANDSCAPE"},
   {"fileUrl":"https://static.rustore.ru/screen-2.png","ordinal":2,"type":"SCREENSHOT","orientation":"PORTRAIT"}
 ],
 "signature":"deadbeef","preordered":false,"inWishlist":false,"unknownFutureField":42
}}
"""

        const val AGGREGATED_BODY = """
{"code":"OK","message":null,"body":{
 "appId":1,"packageName":"ru.example.aggregated","appName":"Агрегатор","companyName":"Внешний источник",
 "aggregatorInfo":{"source":"external","url":"https://example.com"},"versionCode":7
}}
"""

        const val DOWNLOAD_LINK_BODY = """
{"appId":462271,"versionCode":2026062216,"versionId":2064816386,"downloadUrls":[
 {"url":"https://static-m.rustore.ru/2026/9/10/77/7e8825d2-c56e-4993-bf84-744af6914c1e.zip",
  "size":83054649,"hash":"589d6e937dd8f88c"},
 {"url":"https://static-m.rustore.ru/2026/9/10/77/91203661-f108-45f3-942c-0fe7a77a659b.zip",
  "size":44777557,"hash":"b22c656c7cc06826"}],
 "preApproveInfo":{"label":"СберБанк","locale":"ru-RU"},"detailsCode":null}
"""

        const val DEV_APPS_BODY = """
{"code":"OK","message":null,"body":{"elementsCount":2,"continuation":"466879","elements":[
{"appId":462271,"packageName":"ru.sberbankmobile","versionCode":2026062216,"minSdkVersion":26,
 "appName":"СберБанк Онлайн","appType":"MAIN","shortDescription":"Больше чем банк",
 "iconUrl":"https://static-m.rustore.ru/apk/462271/content/ICON/f1b3c68a.png",
 "banner":"","price":0,"isPurchased":false,"averageUserRating":4.82,"totalRatings":4383686},
{"appId":466879,"packageName":"ru.sberbank.investor","versionCode":2026090700,"minSdkVersion":26,
 "appName":"СберИнвестиции","appType":"MAIN","shortDescription":"Ваш онлайн брокер ",
 "iconUrl":"https://static-m.rustore.ru/2026/1/13/86/apk/466879/content/ICON/e341dc76.png",
 "banner":"","price":0,"isPurchased":false,"averageUserRating":4.75,"totalRatings":138012}
]},"timestamp":"2026-09-24T04:48:30.463Z"}
"""
    }
}
