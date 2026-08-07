package com.phonesync.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Hand-authored fallback palette for API 26-30, where dynamic (wallpaper-derived) color is not
 * available. On API 31+ the app uses [androidx.compose.material3.dynamicLightColorScheme] /
 * [androidx.compose.material3.dynamicDarkColorScheme] instead (see Theme.kt) — real Galaxy
 * hardware (One UI's Material You integration) always has this, so this palette is mostly a
 * brand-consistent fallback for older devices/emulators/previews.
 *
 * Three intentional hues instead of a single brand color dropped into every slot:
 * indigo (sync/primary action), teal (backed up / success), amber (storage & attention).
 */
private val PrimaryLight = Color(0xFF3457D6)
private val OnPrimaryLight = Color(0xFFFFFFFF)
private val PrimaryContainerLight = Color(0xFFDBE1FF)
private val OnPrimaryContainerLight = Color(0xFF00174A)
private val SecondaryLight = Color(0xFF4C6358)
private val OnSecondaryLight = Color(0xFFFFFFFF)
private val SecondaryContainerLight = Color(0xFFCEE9DA)
private val OnSecondaryContainerLight = Color(0xFF082017)
private val TertiaryLight = Color(0xFF8A5000)
private val OnTertiaryLight = Color(0xFFFFFFFF)
private val TertiaryContainerLight = Color(0xFFFFDDB3)
private val OnTertiaryContainerLight = Color(0xFF2C1600)
private val BackgroundLight = Color(0xFFFBF8FF)
private val OnBackgroundLight = Color(0xFF1B1B1F)
private val SurfaceVariantLight = Color(0xFFE2E1EC)
private val OnSurfaceVariantLight = Color(0xFF45464F)
private val OutlineLight = Color(0xFF767680)
private val OutlineVariantLight = Color(0xFFC6C5D0)

private val PrimaryDark = Color(0xFFB6C4FF)
private val OnPrimaryDark = Color(0xFF042C7D)
private val PrimaryContainerDark = Color(0xFF1A3E9E)
private val OnPrimaryContainerDark = Color(0xFFDBE1FF)
private val SecondaryDark = Color(0xFFB2CCBE)
private val OnSecondaryDark = Color(0xFF1F352A)
private val SecondaryContainerDark = Color(0xFF354B40)
private val OnSecondaryContainerDark = Color(0xFFCEE9DA)
private val TertiaryDark = Color(0xFFFFB868)
private val OnTertiaryDark = Color(0xFF4A2800)
private val TertiaryContainerDark = Color(0xFF693C00)
private val OnTertiaryContainerDark = Color(0xFFFFDDB3)
private val BackgroundDark = Color(0xFF111318)
private val OnBackgroundDark = Color(0xFFE4E2E9)
private val SurfaceVariantDark = Color(0xFF46464F)
private val OnSurfaceVariantDark = Color(0xFFC7C5D0)
private val OutlineDark = Color(0xFF90909A)
private val OutlineVariantDark = Color(0xFF46464F)

val StaticLightColorScheme: ColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = BackgroundLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3FA),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE3E2E9),
)

val StaticDarkColorScheme: ColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = BackgroundDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F2025),
    surfaceContainerHigh = Color(0xFF292A30),
    surfaceContainerHighest = Color(0xFF34353B),
)

/** Fixed accents that don't flip with dynamic color — used sparingly for status dots/rings. */
val SuccessGreen = Color(0xFF2E7D4F)
val SuccessGreenContainer = Color(0xFFB7F1CB)
