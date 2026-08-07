package com.phonesync.app.ui.battery

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phonesync.app.ui.PhotoSyncViewModelFactory
import com.phonesync.app.ui.components.BackTopBar
import com.phonesync.app.ui.components.PillButton
import com.phonesync.app.ui.components.PillOutlinedButton
import com.phonesync.app.ui.components.SectionCard
import com.phonesync.app.ui.components.StatusChip

/**
 * Samsung / OEM battery killers make WorkManager unreliable unless the user exempts the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryGuidanceScreen(
    factory: PhotoSyncViewModelFactory,
    onBack: () -> Unit,
) {
    val viewModel: BatteryViewModel = viewModel(factory = factory)
    val context = LocalContext.current

    fun isIgnoring(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    var ignoring by remember { mutableStateOf(isIgnoring()) }

    // The user grants this in a system Settings screen and comes right back — re-check on
    // every resume so the chip doesn't show stale "still restricted" after they've fixed it.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) ignoring = isIgnoring()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopBar(title = "Battery guidance", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                StatusChip(
                    label = if (ignoring) "Exempted — background sync should be reliable" else "Still restricted",
                    positive = ignoring,
                )
            }
            SectionCard {
                Text(
                    "Samsung and other manufacturers aggressively kill background apps to save " +
                        "battery. Background sync will be unreliable until Photo Sync is exempted " +
                        "from these restrictions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            GuidanceStep(number = 1, text = "Tap \"Request unrestricted battery\" and allow it when prompted.")
            GuidanceStep(number = 2, text = "In App info → Battery, choose Unrestricted (not Optimized).")
            GuidanceStep(number = 3, text = "Turn off \"Put app to sleep\" / \"Deep sleeping apps\" for Photo Sync, and allow auto-start if shown.")

            Spacer(Modifier.height(4.dp))
            PillButton(
                text = "Request unrestricted battery",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                    }
                },
            )
            PillOutlinedButton(
                text = "Open app battery settings",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        },
                    )
                },
            )
            PillOutlinedButton(
                text = "More Samsung-specific tips (dontkillmyapp.com)",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com/samsung")))
                },
                icon = Icons.AutoMirrored.Filled.OpenInNew,
            )
            Spacer(Modifier.height(4.dp))
            PillButton(
                text = "Done",
                onClick = {
                    viewModel.markSeen()
                    onBack()
                },
            )
        }
    }
}

@Composable
private fun GuidanceStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
