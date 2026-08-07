package com.phonesync.app.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phonesync.app.data.local.LocalAssetEntity
import com.phonesync.app.data.repository.StatusSnapshot
import com.phonesync.app.ui.PhotoSyncViewModelFactory
import com.phonesync.app.ui.common.completionFraction
import com.phonesync.app.ui.common.formatBytes
import com.phonesync.app.ui.common.formatRelativeTime
import com.phonesync.app.ui.components.EmptyHint
import com.phonesync.app.ui.components.IconActionTile
import com.phonesync.app.ui.components.ProgressRing
import com.phonesync.app.ui.components.SectionCard
import com.phonesync.app.ui.components.TonalIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    factory: PhotoSyncViewModelFactory,
    onArchive: () -> Unit,
    onBrowse: () -> Unit,
    onMigration: () -> Unit,
    onSettings: () -> Unit,
) {
    val viewModel: StatusViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Photo Sync") },
                actions = {
                    TonalIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = "Settings",
                        onClick = onSettings,
                    )
                    Spacer(Modifier.width(4.dp))
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { HeroStatusCard(state.snapshot) }
                if (state.snapshot.failedCount > 0) {
                    item { AttentionBanner(count = state.snapshot.failedCount, onFix = onArchive) }
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconActionTile(
                            icon = Icons.Default.Archive,
                            label = "Free space",
                            subtitle = if (state.snapshot.backedUpOnDeviceCount > 0) {
                                "${state.snapshot.backedUpOnDeviceCount} ready"
                            } else {
                                null
                            },
                            onClick = onArchive,
                            modifier = Modifier.weight(1f),
                        )
                        IconActionTile(
                            icon = Icons.Default.PhotoLibrary,
                            label = "Browse library",
                            onClick = onBrowse,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    IconActionTile(
                        icon = Icons.Default.Storage,
                        label = "Library migration",
                        subtitle = "For large first-time backups",
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
                if (state.pending.isEmpty()) {
                    item {
                        EmptyHint(
                            icon = Icons.Default.CheckCircle,
                            title = "All caught up",
                            body = "Nothing waiting — everything scanned so far is backed up.",
                        )
                    }
                } else {
                    items(state.pending, key = { it.clientAssetId }) { asset ->
                        PendingRow(asset)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStatusCard(status: StatusSnapshot) {
    val fraction = completionFraction(status.pendingCount, status.backedUpTotalCount)
    val (label, ringColor, statusIcon) = when {
        status.pcReachable == false -> Triple("PC offline", MaterialTheme.colorScheme.outline, Icons.Default.CloudOff)
        status.pendingCount == 0 -> Triple("All synced", MaterialTheme.colorScheme.secondary, Icons.Default.CloudDone)
        else -> Triple("Syncing…", MaterialTheme.colorScheme.primary, Icons.Default.CloudSync)
    }

    SectionCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ProgressRing(fraction = fraction, diameter = 108.dp, strokeWidth = 10.dp, progressColor = ringColor) {
                Icon(statusIcon, contentDescription = null, tint = ringColor, modifier = Modifier.size(34.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (status.pendingCount > 0) {
                        "${status.pendingCount} left · ${formatBytes(status.pendingBytes)}"
                    } else {
                        "Last checked just now"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Synced ${formatRelativeTime(status.lastSyncEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AttentionBanner(count: Int, onFix: () -> Unit) {
    SectionCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$count item(s) couldn't upload",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "They'll retry automatically on the next sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
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
            "${stateLabel(asset)} · ${formatBytes(asset.sizeBytes)}" +
                (asset.lastError?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun stateLabel(asset: LocalAssetEntity): String = when (asset.syncState.name) {
    "PENDING" -> "Waiting to upload"
    "UPLOADING" -> "Uploading…"
    "FAILED" -> "Failed — will retry"
    else -> asset.syncState.name.lowercase().replaceFirstChar { it.uppercase() }
}
