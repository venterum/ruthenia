// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Branded fallback palette. Used when Material You dynamic colors are
 * unavailable (Android < 12) or disabled by the user.
 *
 * The set is built around a teal seed color and follows the tonal roles of
 * Material 3 Expressive: containers, inverse, outline.
 */
internal val RutheniaLightColorScheme = lightColorScheme(
    primary = Color(0xFF006A6A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6FF6F6),
    onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E7),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4B607C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF041C35),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE5E4),
    onSurfaceVariant = Color(0xFF3F4948),
    surfaceDim = Color(0xFFDAE5E4),
    surfaceBright = Color(0xFFFAFDFC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4FBF9),
    surfaceContainer = Color(0xFFEEF6F4),
    surfaceContainerHigh = Color(0xFFE8F0EF),
    surfaceContainerHighest = Color(0xFFE2EAE9),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC9C8),
    inverseSurface = Color(0xFF2D3131),
    inverseOnSurface = Color(0xFFEFF1F0),
    inversePrimary = Color(0xFF4CDADA),
    scrim = Color(0xFF000000),
)

internal val RutheniaDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CDADA),
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF004F4F),
    onPrimaryContainer = Color(0xFF6FF6F6),
    secondary = Color(0xFFB0CCCB),
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFB3C8E8),
    onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863),
    onTertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191C1C),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF191C1C),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C8),
    surfaceDim = Color(0xFF111414),
    surfaceBright = Color(0xFF343938),
    surfaceContainerLowest = Color(0xFF0C0F0F),
    surfaceContainerLow = Color(0xFF191C1C),
    surfaceContainer = Color(0xFF1D2020),
    surfaceContainerHigh = Color(0xFF272B2B),
    surfaceContainerHighest = Color(0xFF323636),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4948),
    inverseSurface = Color(0xFFE0E3E2),
    inverseOnSurface = Color(0xFF191C1C),
    inversePrimary = Color(0xFF006A6A),
    scrim = Color(0xFF000000),
)

/**
 * Accent palette selectable in settings when Material You dynamic colors are
 * disabled. Each palette contains only the color roles of the
 * primary/secondary/tertiary families: neutral surfaces remain shared, so
 * switching the accent does not "repaint" the whole interface.
 */
enum class RutheniaAccent {
    TEAL,
    INDIGO,
    VIOLET,
    GREEN,
    AMBER,
}

internal fun rutheniaAccentScheme(
    accent: RutheniaAccent,
    darkTheme: Boolean,
): androidx.compose.material3.ColorScheme = when (accent) {
    RutheniaAccent.TEAL -> if (darkTheme) RutheniaDarkColorScheme else RutheniaLightColorScheme
    RutheniaAccent.INDIGO -> if (darkTheme) IndigoDarkColorScheme else IndigoLightColorScheme
    RutheniaAccent.VIOLET -> if (darkTheme) VioletDarkColorScheme else VioletLightColorScheme
    RutheniaAccent.GREEN -> if (darkTheme) GreenDarkColorScheme else GreenLightColorScheme
    RutheniaAccent.AMBER -> if (darkTheme) AmberDarkColorScheme else AmberLightColorScheme
}

/** The accent's primary color — for preview dots in settings. */
@Composable
fun RutheniaAccent.primaryColor(darkTheme: Boolean = isSystemInDarkTheme()): Color =
    rutheniaAccentScheme(this, darkTheme).primary

/** The accent's primary container — the face of the device token. */
@Composable
fun RutheniaAccent.primaryContainerColor(darkTheme: Boolean = isSystemInDarkTheme()): Color =
    rutheniaAccentScheme(this, darkTheme).primaryContainer

/** The accent's on-primary-container — the motif of the device token. */
@Composable
fun RutheniaAccent.onPrimaryContainerColor(darkTheme: Boolean = isSystemInDarkTheme()): Color =
    rutheniaAccentScheme(this, darkTheme).onPrimaryContainer

/** Maps a persisted accent name to the enum, with a safe fallback. */
fun rutheniaAccentByName(name: String): RutheniaAccent =
    RutheniaAccent.entries.firstOrNull { it.name == name } ?: RutheniaAccent.TEAL

private val IndigoLightColorScheme = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF77536D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F1),
    onTertiaryContainer = Color(0xFF2D1228),
)

private val IndigoDarkColorScheme = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF293CA0),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434559),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFE6BAD7),
    onTertiary = Color(0xFF44263D),
    tertiaryContainer = Color(0xFF5D3C55),
    onTertiaryContainer = Color(0xFFFFD7F1),
)

private val VioletLightColorScheme = lightColorScheme(
    primary = Color(0xFF6C4C9C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEFDBFF),
    onPrimaryContainer = Color(0xFF270055),
    secondary = Color(0xFF675A74),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEDCFA),
    onSecondaryContainer = Color(0xFF221533),
    tertiary = Color(0xFF81525F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF33111D),
)

private val VioletDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD8B9FF),
    onPrimary = Color(0xFF3B2169),
    primaryContainer = Color(0xFF533B81),
    onPrimaryContainer = Color(0xFFEFDBFF),
    secondary = Color(0xFFD1C2DE),
    onSecondary = Color(0xFF372C43),
    secondaryContainer = Color(0xFF4E425B),
    onSecondaryContainer = Color(0xFFEEDCFA),
    tertiary = Color(0xFFF4B7C7),
    onTertiary = Color(0xFF4A2532),
    tertiaryContainer = Color(0xFF643B49),
    onTertiaryContainer = Color(0xFFFFD9E2),
)

private val GreenLightColorScheme = lightColorScheme(
    primary = Color(0xFF3D6937),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBEF0AF),
    onPrimaryContainer = Color(0xFF002200),
    secondary = Color(0xFF55624F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E7CF),
    onSecondaryContainer = Color(0xFF131F10),
    tertiary = Color(0xFF386663),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBBECE8),
    onTertiaryContainer = Color(0xFF00201E),
)

private val GreenDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA2D394),
    onPrimary = Color(0xFF0F390D),
    primaryContainer = Color(0xFF265121),
    onPrimaryContainer = Color(0xFFBEF0AF),
    secondary = Color(0xFFBCCBB4),
    onSecondary = Color(0xFF273423),
    secondaryContainer = Color(0xFF3D4A38),
    onSecondaryContainer = Color(0xFFD8E7CF),
    tertiary = Color(0xFF9FD0CC),
    onTertiary = Color(0xFF003734),
    tertiaryContainer = Color(0xFF1F4E4B),
    onTertiaryContainer = Color(0xFFBBECE8),
)

private val AmberLightColorScheme = lightColorScheme(
    primary = Color(0xFF7A5900),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDF9B),
    onPrimaryContainer = Color(0xFF261A00),
    secondary = Color(0xFF6A5D3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2E0BC),
    onSecondaryContainer = Color(0xFF231B04),
    tertiary = Color(0xFF4A6548),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCEBC7),
    onTertiaryContainer = Color(0xFF072107),
)

private val AmberDarkColorScheme = darkColorScheme(
    primary = Color(0xFFEFC048),
    onPrimary = Color(0xFF402D00),
    primaryContainer = Color(0xFF5C4300),
    onPrimaryContainer = Color(0xFFFFDF9B),
    secondary = Color(0xFFD5C5A3),
    onSecondary = Color(0xFF39301A),
    secondaryContainer = Color(0xFF51462E),
    onSecondaryContainer = Color(0xFFF2E0BC),
    tertiary = Color(0xFFB0CFAC),
    onTertiary = Color(0xFF1C361B),
    tertiaryContainer = Color(0xFF334D31),
    onTertiaryContainer = Color(0xFFCCEBC7),
)
