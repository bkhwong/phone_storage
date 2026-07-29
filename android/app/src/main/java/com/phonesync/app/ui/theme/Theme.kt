package com.phonesync.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF2D6A9F)
private val BlueDark = Color(0xFF1A3F63)
private val Sand = Color(0xFFF3F0E8)
private val Ink = Color(0xFF1A2332)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = BlueDark,
    background = Sand,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7EB6E0),
    onPrimary = Ink,
    secondary = Color(0xFFA8C5DC),
    background = Color(0xFF121820),
    surface = Color(0xFF1A2332),
    onBackground = Color(0xFFE8EEF4),
    onSurface = Color(0xFFE8EEF4),
)

@Composable
fun PhotoSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
