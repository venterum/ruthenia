// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * The app theme built on Material 3 Expressive.
 *
 * Differences from the classic [androidx.compose.material3.MaterialTheme]:
 *  • motion — spring physics from [MotionScheme.expressive] instead of fixed
 *    durations;
 *  • shape — an enlarged expressive radius scale;
 *  • typography — a branded display font in the display/headline styles
 *    ([RutheniaTypography]);
 *  • color — Material You dynamic colors (Android 12+) with a branded
 *    fallback palette.
 *
 * Additionally, the theme sets [LocalContentColor] = `onBackground`: without this,
 * `Text` outside `Surface`/`Scaffold` (for example, a catalog header) would inherit
 * black and would not switch in the dark theme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RutheniaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accent: RutheniaAccent = RutheniaAccent.TEAL,
    typography: Typography = RutheniaTypography,
    content: @Composable () -> Unit,
) {
    val colorScheme = rutheniaColorScheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        accent = accent,
    )

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = RutheniaShapes,
        typography = typography,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colorScheme.onBackground,
            content = content,
        )
    }
}

@Composable
private fun rutheniaColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    accent: RutheniaAccent,
): ColorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    else -> rutheniaAccentScheme(accent = accent, darkTheme = darkTheme)
}
