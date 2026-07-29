package com.phonesync.app.ui.archive

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonesync.app.data.local.LocalAssetEntity
import com.phonesync.app.data.repository.PhotoSyncRepository
import com.phonesync.app.media.MediaDeleteHelper
import com.phonesync.app.ui.status.formatBytes
import kotlinx.coroutines.launch

/**
 * Review backed_up assets still on device → archive API → delete local.
 * Never deletes local before server confirm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    repository: PhotoSyncRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val archivable by repository.observeArchivable().collectAsStateWithLifecycle(emptyList())
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingAfterConfirm by remember { mutableStateOf<List<LocalAssetEntity>>(emptyList()) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (MediaDeleteHelper.isDeleteGranted(result.resultCode)) {
            scope.launch {
                repository.markLocalDeletedAfterArchive(pendingAfterConfirm)
                message = "Freed ${pendingAfterConfirm.size} items from phone storage."
                pendingAfterConfirm = emptyList()
                selected = emptySet()
                busy = false
            }
        } else {
            message = "Delete cancelled — files remain on phone (still archived on PC)."
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Free up space") },
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
        ) {
            Text(
                "These items are verified on your PC. Archiving deletes them from this phone only after the server confirms.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (!MediaDeleteHelper.hasManageMedia(context)) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(MediaDeleteHelper.manageMediaSettingsIntent(context))
                    },
                ) {
                    Text("Grant MANAGE_MEDIA for quieter deletes")
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${selected.size} selected")
                OutlinedButton(
                    onClick = {
                        selected = if (selected.size == archivable.size) {
                            emptySet()
                        } else {
                            archivable.map { it.clientAssetId }.toSet()
                        }
                    },
                ) {
                    Text(if (selected.size == archivable.size) "Clear" else "Select all")
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(archivable, key = { it.clientAssetId }) { asset ->
                    ArchiveRow(
                        asset = asset,
                        checked = asset.clientAssetId in selected,
                        onToggle = {
                            selected = if (asset.clientAssetId in selected) {
                                selected - asset.clientAssetId
                            } else {
                                selected + asset.clientAssetId
                            }
                        },
                    )
                }
            }
            if (message != null) {
                Text(message!!, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }
            val selectedBytes = archivable
                .filter { it.clientAssetId in selected }
                .sumOf { it.sizeBytes }
            Button(
                enabled = selected.isNotEmpty() && !busy,
                onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        runCatching {
                            val toArchive = archivable.filter { it.clientAssetId in selected }
                            val confirmed = repository.archiveOnServer(toArchive)
                            if (confirmed.isEmpty()) {
                                message = "Nothing confirmed by server."
                                busy = false
                                return@launch
                            }
                            pendingAfterConfirm = confirmed
                            val uris = confirmed.map { Uri.parse(it.contentUri) }
                            val pendingIntent = MediaDeleteHelper.deleteUris(context, uris)
                            if (pendingIntent != null) {
                                deleteLauncher.launch(
                                    IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                                )
                            } else {
                                repository.markLocalDeletedAfterArchive(confirmed)
                                message = "Freed ${confirmed.size} items."
                                selected = emptySet()
                                busy = false
                            }
                        }.onFailure {
                            message = it.message ?: "Archive failed"
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Archive & delete local (${formatBytes(selectedBytes)})")
            }
        }
    }
}

@Composable
private fun ArchiveRow(
    asset: LocalAssetEntity,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(asset.displayName)
            Text(formatBytes(asset.sizeBytes), style = MaterialTheme.typography.bodySmall)
        }
    }
}
