package com.phonesync.app.ui.permissions

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phonesync.app.ui.components.BrandMark
import com.phonesync.app.ui.components.PillButton

/**
 * Shown instead of silently landing on a broken Home/Pairing screen when photo/video
 * permissions haven't been granted (or were permanently denied). Previously the app would
 * proceed regardless and just silently fail to find any media to back up.
 */
@Composable
fun PermissionGateScreen(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(icon = Icons.Default.PhotoLibrary, size = 84)
            Spacer(Modifier.height(24.dp))
            Text(
                "Photos & videos access needed",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Photo Sync only reads your media library to find new photos and videos to " +
                    "back up — it never reads anything else on your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            if (permanentlyDenied) {
                PillButton(
                    text = "Open app settings",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            },
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Permission was denied. Enable Photos and videos under Permissions to continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                PillButton(text = "Grant access", onClick = onRequestPermission)
            }
        }
    }
}
