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
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
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
    partialMediaAccess: Boolean = false,
    onRequestFullMediaAccess: () -> Unit = {},
) {
    val viewModel: StatusViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dismissMessage()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            isRefreshing = state.refreshing || state.retryingFailed,
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
                if (partialMediaAccess) {
                    item {
                        PartialAccessBanner(onAllowAll = onRequestFullMediaAccess)
                    }
                }
                if (state.snapshot.failedCount > 0) {
                    item {
                        FailedUploadsBanner(
                            count = state.snapshot.failedCount,
                            retrying = state.retryingFailed,
                            onRetry = viewModel::retryFailed,
                        )
                    }
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
private fun PartialAccessBanner(onAllowAll: () -> Unit) {
    SectionCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Only selected photos are syncing",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "Allow all photos & videos so new shots are backed up automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                TextButton(onClick = onAllowAll) {
                    Text("Allow all")
                }
            }
        }
    }
}

@Composable
private fun FailedUploadsBanner(
    count: Int,
    retrying: Boolean,
    onRetry: () -> Unit,
) {
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
                    "Tap Retry to requeue them now, or pull to refresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = onRetry, enabled = !retrying) {
                    Text(if (retrying) "Retrying…" else "Retry now")
                }
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
    "FAILED" -> "Failed — tap Retry above"
    else -> asset.syncState.name.lowercase().replaceFirstChar { it.uppercase() }
}
