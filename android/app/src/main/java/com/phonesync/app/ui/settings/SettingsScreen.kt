package com.phonesync.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonesync.app.data.prefs.SecurePrefs
import com.phonesync.app.data.repository.PhotoSyncRepository
import com.phonesync.app.sync.SyncWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: PhotoSyncRepository,
    prefs: SecurePrefs,
    onBatteryGuidance: () -> Unit,
    onUnpaired: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val interval by prefs.syncIntervalMinutes.collectAsStateWithLifecycle(60)
    val cellular by prefs.allowCellular.collectAsStateWithLifecycle(false)
    val serverUrl by prefs.serverBaseUrl.collectAsStateWithLifecycle(null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Server: ${serverUrl ?: "—"}")
            Text("Sync interval: $interval minutes")
            Slider(
                value = interval.toFloat(),
                onValueChange = { prefs.setSyncIntervalMinutes(it.toInt()) },
                valueRange = 15f..240f,
                steps = 14,
                onValueChangeFinished = {
                    SyncWorker.enqueuePeriodic(context, interval, cellular)
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow cellular sync")
                    Text("Off = Wi‑Fi (unmetered) only")
                }
                Switch(
                    checked = cellular,
                    onCheckedChange = {
                        prefs.setAllowCellular(it)
                        SyncWorker.enqueuePeriodic(context, interval, it)
                    },
                )
            }
            OutlinedButton(onClick = onBatteryGuidance, modifier = Modifier.fillMaxWidth()) {
                Text("Samsung battery optimization guidance")
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    scope.launch {
                        repository.unpair()
                        onUnpaired()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Unpair this device")
            }
        }
    }
}
