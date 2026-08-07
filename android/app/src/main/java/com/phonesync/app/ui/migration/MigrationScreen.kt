package com.phonesync.app.ui.migration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.phonesync.app.sync.MigrationWorker
import com.phonesync.app.ui.PhotoSyncViewModelFactory
import com.phonesync.app.ui.components.BackTopBar
import com.phonesync.app.ui.components.PillButton
import com.phonesync.app.ui.components.PillOutlinedButton
import com.phonesync.app.ui.components.ProgressRing
import com.phonesync.app.ui.components.SectionCard
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationScreen(
    factory: PhotoSyncViewModelFactory,
    onBack: () -> Unit,
) {
    val viewModel: MigrationViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val workInfoFlow = remember {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(MigrationWorker.UNIQUE)
            .map { it.firstOrNull() }
    }
    val workInfo by workInfoFlow.collectAsStateWithLifecycle(null)

    val running = workInfo?.state == WorkInfo.State.RUNNING || workInfo?.state == WorkInfo.State.ENQUEUED
    val progressData = workInfo?.progress
    val total = progressData?.getInt(MigrationWorker.KEY_TOTAL, 0) ?: 0
    val remaining = progressData?.getInt(MigrationWorker.KEY_REMAINING, 0) ?: state.pendingCount
    val fraction = if (total > 0) 1f - (remaining.toFloat() / total.toFloat()) else if (state.pendingCount == 0) 1f else 0f

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopBar(title = "Library migration", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard {
                Text(
                    "For a first-time backup of a large library (100 GB–1 TB+), start a migration " +
                        "here instead of waiting on background sync. Android may pause very long " +
                        "transfers — tap Continue to pick up right where it left off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    val icon = when {
                        state.pendingCount == 0 && !running -> Icons.Default.CloudDone
                        running -> Icons.Default.CloudUpload
                        else -> Icons.Default.PauseCircle
                    }
                    ProgressRing(
                        fraction = fraction,
                        diameter = 128.dp,
                        progressColor = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        migrationStatusLabel(running, state.pendingCount, remaining),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "${state.pendingCount} item(s) left to upload",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            PillButton(
                text = if (state.pendingCount > 0) "Continue migration" else "Scan & start migration",
                onClick = viewModel::start,
                enabled = !running && !state.starting,
                loading = state.starting,
            )
            PillOutlinedButton(
                text = "Pause",
                onClick = viewModel::cancel,
                enabled = running,
            )
            Text(
                "Tip: plug in power, stay on Wi-Fi, and turn off battery optimization for the smoothest migration " +
                    "(Settings → Battery optimization guidance).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun migrationStatusLabel(running: Boolean, pendingCount: Int, remaining: Int): String = when {
    pendingCount == 0 && !running -> "Library is up to date"
    running -> "Uploading — $remaining left"
    else -> "Paused — tap Continue to resume"
}
