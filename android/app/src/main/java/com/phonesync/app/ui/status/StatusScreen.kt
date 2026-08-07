package com.phonesync.app.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonesync.app.data.local.LocalAssetEntity
import com.phonesync.app.data.prefs.SecurePrefs
import com.phonesync.app.data.repository.PhotoSyncRepository
import com.phonesync.app.data.repository.StatusSnapshot
import com.phonesync.app.sync.SyncWorker
import com.phonesync.app.ui.components.EmptyHint
import com.phonesync.app.ui.components.IconActionTile
import com.phonesync.app.ui.components.SectionCard
import com.phonesync.app.ui.components.StatusChip
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(baseStatus) {
        status = repository.enrichStatus(baseStatus)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
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
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { StatusCard(status) }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconActionTile(
                        icon = Icons.Default.Archive,
                        label = "Free space",
                        onClick = onArchive,
                        modifier = Modifier.weight(1f),
                    )
                    IconActionTile(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Browse",
                        onClick = onBrowse,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                IconActionTile(
                    icon = Icons.Default.Storage,
                    label = "Migration (large library)",
                    onClick = onMigration,
                    modifier = Modifier.fillMaxWidth(),
                    filled = true,
                )
            }
            item {
                Text(
                    "Pending uploads",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (pending.isEmpty()) {
                item {
                    EmptyHint(
                        icon = Icons.Default.CheckCircle,
                        title = "All clear",
                        body = "Nothing waiting — scanned media is backed up or idle.",
                    )
                }
            } else {
                items(pending.take(40), key = { it.clientAssetId }) { asset ->
                    PendingRow(asset)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(status: StatusSnapshot) {
    val label = when (status.pcReachable) {
        true -> "PC reachable"
        false -> "PC offline"
        null -> "PC status unknown"
    }
    val statusIcon = when (status.pcReachable) {
        true -> Icons.Default.CloudUpload
        false -> Icons.Default.CloudOff
        null -> Icons.AutoMirrored.Filled.HelpOutline
    }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatusChip(label = label, positive = status.pcReachable)
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        StatLine("Pending", "${status.pendingCount} (${formatBytes(status.pendingBytes)})")
        StatLine("Still on phone", "${status.backedUpOnDeviceCount} backed up")
        StatLine(
            "Last sync",
            if (status.lastSyncEpochMs > 0) {
                DateFormat.getDateTimeInstance().format(Date(status.lastSyncEpochMs))
            } else {
                "never"
            },
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PendingRow(asset: LocalAssetEntity) {
    SectionCard {
        Text(
            asset.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "${asset.syncState.name.lowercase()} · ${formatBytes(asset.sizeBytes)}" +
                (asset.lastError?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
