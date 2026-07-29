package com.phonesync.app.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonesync.app.data.local.LocalAssetEntity
import com.phonesync.app.data.prefs.SecurePrefs
import com.phonesync.app.data.repository.PhotoSyncRepository
import com.phonesync.app.data.repository.StatusSnapshot
import com.phonesync.app.sync.SyncWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    repository: PhotoSyncRepository,
    prefs: SecurePrefs,
    onArchive: () -> Unit,
    onBrowse: () -> Unit,
    onMigration: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending by repository.observePending().collectAsStateWithLifecycle(emptyList())
    val baseStatus by repository.observeStatus().collectAsStateWithLifecycle(
        StatusSnapshot(0, 0, 0, 0, null, false),
    )
    var status by remember { mutableStateOf(baseStatus) }
    val allowCellular by prefs.allowCellular.collectAsStateWithLifecycle(false)

    LaunchedEffect(baseStatus) {
        status = repository.enrichStatus(baseStatus)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Sync") },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                runCatching { repository.scanAndReconcileLocal() }
                                SyncWorker.enqueueNow(context, allowCellular)
                                status = repository.enrichStatus(repository.observeStatus().first())
                            }
                        },
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync now")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
        ) {
            StatusCard(status)
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(onClick = onArchive, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Archive, contentDescription = null)
                    Text(" Free space")
                }
                OutlinedButton(onClick = onBrowse, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Text(" Browse")
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onMigration, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Storage, contentDescription = null)
                Text(" Migration (large library)")
            }
            Spacer(Modifier.height(16.dp))
            Text("Pending uploads", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (pending.isEmpty()) {
                Text("Nothing waiting — all scanned media is backed up or idle.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pending.take(40), key = { it.clientAssetId }) { asset ->
                        PendingRow(asset)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(status: StatusSnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Text(
                    when (status.pcReachable) {
                        true -> "PC reachable"
                        false -> "PC offline"
                        null -> "PC status unknown"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text("Pending: ${status.pendingCount} (${formatBytes(status.pendingBytes)})")
            Text("Backed up still on phone: ${status.backedUpOnDeviceCount}")
            Text(
                "Last sync: " + if (status.lastSyncEpochMs > 0) {
                    DateFormat.getDateTimeInstance().format(Date(status.lastSyncEpochMs))
                } else {
                    "never"
                },
            )
        }
    }
}

@Composable
private fun PendingRow(asset: LocalAssetEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier = Modifier.padding(12.dp)) {
            Text(asset.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${asset.syncState.name.lowercase()} · ${formatBytes(asset.sizeBytes)}" +
                    (asset.lastError?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
