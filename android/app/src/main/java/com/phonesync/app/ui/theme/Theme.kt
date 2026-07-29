package com.phonesync.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Always dark — Jake Xia palette with liquid-glass surfaces.
 * Light system setting is ignored so the brand look stays consistent.
 */
private val JakeGlassScheme = darkColorScheme(
    primary = JakeYellow,
    onPrimary = JakeInk,
    primaryContainer = JakeYellow.copy(alpha = 0.22f),
    onPrimaryContainer = JakeYellow,
    secondary = JakeWhite,
    onSecondary = JakeInk,
    secondaryContainer = JakeGlassStrong,
    onSecondaryContainer = JakeWhite,
    tertiary = JakeGlowBlue,
    onTertiary = JakeWhite,
    tertiaryContainer = JakeGlowBlue.copy(alpha = 0.35f),
    onTertiaryContainer = JakeGraySoft,
    background = JakeBlack,
    onBackground = JakeWhite,
    surface = Color(0xFF1C1C1C),
    onSurface = JakeWhite,
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = JakeGraySoft,
    surfaceContainerLowest = Color(0xFF101010),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF222222),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF333333),
    outline = JakeGlassBorder,
    outlineVariant = Color(0x33FFFFFF),
    error = JakeError,
    onError = JakeInk,
    errorContainer = JakeError.copy(alpha = 0.2f),
    onErrorContainer = JakeError,
)

@Composable
fun PhotoSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JakeGlassScheme,
        typography = PhotoSyncTypography,
        shapes = PhotoSyncShapes,
        content = content,
    )
}
