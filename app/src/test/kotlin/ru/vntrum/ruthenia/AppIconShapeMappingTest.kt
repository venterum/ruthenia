/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia

import org.junit.Assert.assertEquals
import org.junit.Test
import com.vntrum.ruthenia.core.data.settings.AppIconShape
import com.vntrum.ruthenia.core.designsystem.component.RutheniaIconShape

/** Every stored icon shape has a design-system counterpart. */
class AppIconShapeMappingTest {

    @Test
    fun `all icon shapes are mapped`() {
        val mapped = AppIconShape.entries.map { it.toRutheniaIconShape() }

        assertEquals(AppIconShape.entries.size, mapped.size)
        assertEquals(RutheniaIconShape.entries.toSet(), mapped.toSet())
    }
}
