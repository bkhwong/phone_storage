package com.phonesync.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phonesync.app.BuildConfig
import com.phonesync.app.ui.PhotoSyncViewModelFactory
import com.phonesync.app.ui.components.BackTopBar
import com.phonesync.app.ui.components.PillOutlinedButton
import com.phonesync.app.ui.components.SectionCard
import androidx.compose.foundation.shape.CircleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    factory: PhotoSyncViewModelFactory,
    onBatteryGuidance: () -> Unit,
    onUnpaired: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    var confirmUnpair by remember { mutableStateOf(false) }

    // Local, live-dragged slider position — onValueChangeFinished must use this rather than
    // the last-composed StateFlow value, which can be stale relative to the just-dragged
    // position by the time the drag gesture finishes.
    var sliderPosition by remember(state.intervalMinutes) { mutableFloatStateOf(state.intervalMinutes.toFloat()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopBar(title = "Settings", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            SectionCard(title = "Connection", icon = Icons.Default.CloudQueue) {
                Text(
                    state.serverUrl ?: "Not paired",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Sync interval", icon = Icons.Default.Timer) {
                Text(
                    "${sliderPosition.toInt()} minutes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    valueRange = 15f..240f,
                    steps = 14,
                    onValueChangeFinished = { viewModel.setSyncInterval(sliderPosition.toInt()) },
                )
                Text(
                    "How often Photo Sync checks for new photos in the background (15 min – 4 hours).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.NetworkCell, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Allow cellular sync", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Off = Wi-Fi only",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(checked = state.allowCellular, onCheckedChange = viewModel::setAllowCellular)
                }
            }

            PillOutlinedButton(
                text = "Battery optimization guidance",
                onClick = onBatteryGuidance,
                icon = Icons.Default.BatteryAlert,
            )

            SectionCard(title = "About", icon = Icons.Default.Info) {
                Text(
                    "Photo Sync v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { confirmUnpair = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Default.LinkOff, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Unpair this device")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair this device?") },
            text = {
                Text(
                    "This stops syncing and forgets the paired PC. Already-backed-up photos " +
                        "on the PC are not affected, but you'll need to pair again to resume.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUnpair = false
                        viewModel.unpair(onUnpaired)
                    },
                ) { Text("Unpair") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") }
            },
        )
    }
}
