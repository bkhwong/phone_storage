package com.phonesync.app.ui.battery

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.phonesync.app.data.prefs.SecurePrefs

/**
 * Samsung / OEM battery killers make WorkManager unreliable unless the user exempts the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryGuidanceScreen(
    prefs: SecurePrefs,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.getSystemService(PowerManager::class.java)
    val ignoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery guidance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Samsung and other OEMs aggressively kill background apps. " +
                    "Hourly sync will be unreliable until Photo Sync is exempted.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (ignoring) "System battery optimization: exempted ✓"
                else "System battery optimization: still restricted",
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                                )
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open battery optimization settings")
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("App info (Samsung: Battery → Unrestricted)")
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com/samsung")),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open dontkillmyapp.com (Samsung)")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Also: Settings → Apps → Photo Sync → Battery → Unrestricted; " +
                    "disable Sleeping apps / Deep sleeping for this package; " +
                    "allow auto-start if shown.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    prefs.setBatteryGuidanceSeen(true)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
        }
    }
}
