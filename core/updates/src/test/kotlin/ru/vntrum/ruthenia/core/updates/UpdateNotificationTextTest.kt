/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateNotificationTextTest {

    private val more = "и ещё %d"
    private val arrow = "%s → %s"
    private val newVersion = "Новая версия %s"
    private val available = "Доступно обновление"

    @Test
    fun `summary lists all names when there are few`() {
        assertEquals(
            "СберБанк, ВТБ, Альфа-Банк",
            UpdateNotificationText.summary(listOf("СберБанк", "ВТБ", "Альфа-Банк"), more),
        )
    }

    @Test
    fun `summary counts the remaining names`() {
        assertEquals(
            "СберБанк, ВТБ, Альфа-Банк и ещё 2",
            UpdateNotificationText.summary(
                listOf("СберБанк", "ВТБ", "Альфа-Банк", "Mir Pay", "Ozon Банк"),
                more,
            ),
        )
    }

    @Test
    fun `summary of empty list is empty`() {
        assertEquals("", UpdateNotificationText.summary(emptyList(), more))
    }

    @Test
    fun `version transition shows both versions`() {
        assertEquals(
            "17.9.0 → 17.12.0",
            UpdateNotificationText.versionTransition("17.9.0", "17.12.0", arrow, newVersion, available),
        )
    }

    @Test
    fun `version transition degrades gracefully`() {
        assertEquals(
            "Новая версия 17.12.0",
            UpdateNotificationText.versionTransition(null, "17.12.0", arrow, newVersion, available),
        )
        assertEquals(
            "Доступно обновление",
            UpdateNotificationText.versionTransition(null, null, arrow, newVersion, available),
        )
    }
}
