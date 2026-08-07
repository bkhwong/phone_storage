package com.phonesync.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material You theming: on API 31+ colors are derived from the device wallpaper via
 * [dynamicLightColorScheme]/[dynamicDarkColorScheme] — the same mechanism One UI itself uses
 * to theme its own system apps — and follow the system light/dark setting via
 * [isSystemInDarkTheme] instead of forcing one mode. Devices below API 31 (dynamic color isn't
 * available there) fall back to the hand-authored static schemes in Color.kt.
 */
@Composable
fun PhotoSyncTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) StaticDarkColorScheme else StaticLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PhotoSyncTypography,
        shapes = PhotoSyncShapes,
        content = content,
    )
}
