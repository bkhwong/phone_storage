package com.phonesync.app.ui.archive

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phonesync.app.data.local.LocalAssetEntity
import com.phonesync.app.media.MediaDeleteHelper
import com.phonesync.app.ui.PhotoSyncViewModelFactory
import com.phonesync.app.ui.common.formatBytes
import com.phonesync.app.ui.components.BackTopBar
import com.phonesync.app.ui.components.EmptyHint
import com.phonesync.app.ui.components.PillButton
import com.phonesync.app.ui.components.SectionCard
import com.phonesync.app.ui.components.SelectableThumbnail
import com.phonesync.app.ui.components.rememberLocalThumbRequest
import com.phonesync.app.media.MediaKind

/**
 * Review backed_up assets still on device → archive on server → delete local.
 * Never deletes local before server confirm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    factory: PhotoSyncViewModelFactory,
    onBack: () -> Unit,
) {
    val viewModel: ArchiveViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pendingConfirmed by remember { mutableStateOf<List<LocalAssetEntity>>(emptyList()) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (MediaDeleteHelper.isDeleteGranted(result.resultCode)) {
            viewModel.onDeleteFinished(pendingConfirmed, failedCount = 0)
        } else {
            viewModel.onDeleteCancelled()
        }
        pendingConfirmed = emptyList()
    }

    fun performDelete(confirmed: List<LocalAssetEntity>) {
        val uriToAsset = confirmed.associateBy { Uri.parse(it.contentUri) }
        when (val outcome = MediaDeleteHelper.deleteUris(context, uriToAsset.keys.toList())) {
            is MediaDeleteHelper.DeleteOutcome.Immediate -> {
                val deleted = outcome.deletedUris.mapNotNull { uriToAsset[it] }
                viewModel.onDeleteFinished(deleted, outcome.failedUris.size)
            }
            is MediaDeleteHelper.DeleteOutcome.RequiresConfirmation -> {
                pendingConfirmed = confirmed
                deleteLauncher.launch(MediaDeleteHelper.intentSenderRequest(outcome.pendingIntent))
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopBar(title = "Free up space", onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!MediaDeleteHelper.hasManageMedia(context)) {
                Box(modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp)) {
                    SectionCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            "Tip: grant \"Manage media\" for quieter, one-tap deletes without a confirmation popup each time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        androidx.compose.material3.TextButton(
                            onClick = { context.startActivity(MediaDeleteHelper.manageMediaSettingsIntent(context)) },
                        ) { Text("Open settings") }
                    }
                }
            }

            if (state.archivable.isEmpty()) {
                EmptyHint(
                    icon = Icons.Default.CheckCircle,
                    title = "Nothing to free up yet",
                    body = "Once photos finish backing up, they'll show up here so you can clear space on your phone.",
                    modifier = Modifier.padding(top = 48.dp),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.selected.isEmpty()) "${state.archivable.size} ready to free up" else "${state.selected.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    androidx.compose.material3.TextButton(onClick = viewModel::toggleAll) {
                        Text(if (state.allSelected) "Clear" else "Select all")
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.archivable, key = { it.clientAssetId }) { asset ->
                        SelectableThumbnail(
                            model = rememberLocalThumbRequest(asset.contentUri),
                            contentDescription = asset.displayName,
                            selectable = true,
                            selected = asset.clientAssetId in state.selected,
                            isVideo = asset.mediaKind == MediaKind.VIDEO,
                            onClick = { viewModel.toggle(asset.clientAssetId) },
                        )
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                    if (state.message != null) {
                        Text(
                            state.message!!,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    PillButton(
                        text = if (state.busy) {
                            "Working…"
                        } else {
                            "Free up ${formatBytes(state.selectedBytes)}"
                        },
                        onClick = { viewModel.archiveOnServer(::performDelete) },
                        enabled = state.selected.isNotEmpty() && !state.busy,
                        loading = state.busy,
                    )
                }
            }
        }
    }
}
