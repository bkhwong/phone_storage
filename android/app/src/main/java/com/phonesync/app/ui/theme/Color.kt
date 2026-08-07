package com.phonesync.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Hand-authored fallback palette for API 26-30, where dynamic (wallpaper-derived) color is not
 * available. On API 31+ the app uses [androidx.compose.material3.dynamicLightColorScheme] /
 * [androidx.compose.material3.dynamicDarkColorScheme] instead — see Theme.kt — so this is only
 * ever seen on older devices/emulators. Tonal values below follow a blue seed color, structured
 * the same way Material Theme Builder output is structured (light containers use high-value
 * tones, dark-theme containers use low-value tones), so it still reads as a proper Material You
 * scheme rather than a flat brand color substituted into every slot.
 */
private val SeedPrimaryLight = Color(0xFF3963C4)
private val SeedOnPrimaryLight = Color(0xFFFFFFFF)
private val SeedPrimaryContainerLight = Color(0xFFDCE1FF)
private val SeedOnPrimaryContainerLight = Color(0xFF001947)
private val SeedSecondaryLight = Color(0xFF565F71)
private val SeedOnSecondaryLight = Color(0xFFFFFFFF)
private val SeedSecondaryContainerLight = Color(0xFFDAE2F9)
private val SeedOnSecondaryContainerLight = Color(0xFF131C2B)
private val SeedTertiaryLight = Color(0xFF6C5677)
private val SeedOnTertiaryLight = Color(0xFFFFFFFF)
private val SeedTertiaryContainerLight = Color(0xFFF4D9FF)
private val SeedOnTertiaryContainerLight = Color(0xFF261430)
private val SeedBackgroundLight = Color(0xFFF9F9FF)
private val SeedOnBackgroundLight = Color(0xFF1A1B20)
private val SeedSurfaceVariantLight = Color(0xFFE1E2EC)
private val SeedOnSurfaceVariantLight = Color(0xFF44474E)
private val SeedOutlineLight = Color(0xFF75777F)
private val SeedOutlineVariantLight = Color(0xFFC5C6D0)

private val SeedPrimaryDark = Color(0xFFB0C6FF)
private val SeedOnPrimaryDark = Color(0xFF002E69)
private val SeedPrimaryContainerDark = Color(0xFF1D4694)
private val SeedOnPrimaryContainerDark = Color(0xFFDBE1FF)
private val SeedSecondaryDark = Color(0xFFBEC6DC)
private val SeedOnSecondaryDark = Color(0xFF283141)
private val SeedSecondaryContainerDark = Color(0xFF3E4759)
private val SeedOnSecondaryContainerDark = Color(0xFFDAE2F9)
private val SeedTertiaryDark = Color(0xFFD8BDE3)
private val SeedOnTertiaryDark = Color(0xFF3B2947)
private val SeedTertiaryContainerDark = Color(0xFF533F5E)
private val SeedOnTertiaryContainerDark = Color(0xFFF4D9FF)
private val SeedBackgroundDark = Color(0xFF111318)
private val SeedOnBackgroundDark = Color(0xFFE2E2E9)
private val SeedSurfaceVariantDark = Color(0xFF44474E)
private val SeedOnSurfaceVariantDark = Color(0xFFC5C6D0)
private val SeedOutlineDark = Color(0xFF8E9099)
private val SeedOutlineVariantDark = Color(0xFF44474E)

val StaticLightColorScheme: ColorScheme = lightColorScheme(
    primary = SeedPrimaryLight,
    onPrimary = SeedOnPrimaryLight,
    primaryContainer = SeedPrimaryContainerLight,
    onPrimaryContainer = SeedOnPrimaryContainerLight,
    secondary = SeedSecondaryLight,
    onSecondary = SeedOnSecondaryLight,
    secondaryContainer = SeedSecondaryContainerLight,
    onSecondaryContainer = SeedOnSecondaryContainerLight,
    tertiary = SeedTertiaryLight,
    onTertiary = SeedOnTertiaryLight,
    tertiaryContainer = SeedTertiaryContainerLight,
    onTertiaryContainer = SeedOnTertiaryContainerLight,
    background = SeedBackgroundLight,
    onBackground = SeedOnBackgroundLight,
    surface = SeedBackgroundLight,
    onSurface = SeedOnBackgroundLight,
    surfaceVariant = SeedSurfaceVariantLight,
    onSurfaceVariant = SeedOnSurfaceVariantLight,
    outline = SeedOutlineLight,
    outlineVariant = SeedOutlineVariantLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E9),
)

val StaticDarkColorScheme: ColorScheme = darkColorScheme(
    primary = SeedPrimaryDark,
    onPrimary = SeedOnPrimaryDark,
    primaryContainer = SeedPrimaryContainerDark,
    onPrimaryContainer = SeedOnPrimaryContainerDark,
    secondary = SeedSecondaryDark,
    onSecondary = SeedOnSecondaryDark,
    secondaryContainer = SeedSecondaryContainerDark,
    onSecondaryContainer = SeedOnSecondaryContainerDark,
    tertiary = SeedTertiaryDark,
    onTertiary = SeedOnTertiaryDark,
    tertiaryContainer = SeedTertiaryContainerDark,
    onTertiaryContainer = SeedOnTertiaryContainerDark,
    background = SeedBackgroundDark,
    onBackground = SeedOnBackgroundDark,
    surface = SeedBackgroundDark,
    onSurface = SeedOnBackgroundDark,
    surfaceVariant = SeedSurfaceVariantDark,
    onSurfaceVariant = SeedOnSurfaceVariantDark,
    outline = SeedOutlineDark,
    outlineVariant = SeedOutlineVariantDark,
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
)
