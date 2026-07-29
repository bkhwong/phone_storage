package com.phonesync.app.ui.migration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.phonesync.app.data.prefs.SecurePrefs
import com.phonesync.app.data.repository.PhotoSyncRepository
import com.phonesync.app.sync.MigrationWorker
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationScreen(
    repository: PhotoSyncRepository,
    prefs: SecurePrefs,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingCount by repository.observePendingCount().collectAsStateWithLifecycle(0)
    val allowCellular by prefs.allowCellular.collectAsStateWithLifecycle(false)

    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(MigrationWorker.UNIQUE)
        .map { it.firstOrNull() }
        .collectAsStateWithLifecycle(null)

    val running = workInfos?.state == WorkInfo.State.RUNNING ||
        workInfos?.state == WorkInfo.State.ENQUEUED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library migration") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "For the first 100GB–1TB copy, start a user-initiated migration. " +
                    "Android may pause long transfers — use Continue to resume. " +
                    "Uploads are chunked and resume from the last offset.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Pending items: $pendingCount", style = MaterialTheme.typography.titleMedium)
            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Migration worker: ${workInfos?.state}")
            }
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = !running,
                onClick = {
                    scope.launch {
                        repository.scanAndReconcileLocal()
                        MigrationWorker.enqueue(context, allowCellular)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (pendingCount > 0) "Continue migration" else "Scan & start migration")
            }
            OutlinedButton(
                enabled = running,
                onClick = { MigrationWorker.cancel(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Pause / cancel")
            }
            Text(
                "Tip: plug in power, stay on Wi‑Fi, and disable battery optimization " +
                    "(see Settings → Samsung battery guidance).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
