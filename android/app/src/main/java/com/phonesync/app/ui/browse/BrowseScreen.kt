package com.phonesync.app.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phonesync.app.data.remote.AssetDto
import com.phonesync.app.ui.PhotoSyncViewModelFactory
import com.phonesync.app.ui.components.BackTopBar
import com.phonesync.app.ui.components.EmptyHint
import com.phonesync.app.ui.components.SelectableThumbnail
import com.phonesync.app.ui.components.rememberAuthedThumbRequest
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    factory: PhotoSyncViewModelFactory,
    onBack: () -> Unit,
) {
    val viewModel: BrowseViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val gridState = rememberLazyGridState()
    var viewerAsset by remember { mutableStateOf<AssetDto?>(null) }
    val auth = viewModel.authHeader()

    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, total) ->
                if (total > 0 && last >= total - 6) viewModel.loadMore()
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopBar(title = "Library on PC", onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrowseFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
            if (state.error != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::retry) { Text("Retry") }
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.items.isEmpty() && !state.loading && state.error == null) {
                    EmptyHint(
                        icon = Icons.Default.PhotoLibrary,
                        title = "Nothing here yet",
                        body = "Photos and videos backed up to your PC will show up here.",
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(100.dp),
                        state = gridState,
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(state.items, key = { it.id }) { asset ->
                            Box {
                                SelectableThumbnail(
                                    model = rememberAuthedThumbRequest(viewModel.thumbnailUrl(asset.id), auth),
                                    contentDescription = asset.originalFilename,
                                    isVideo = asset.mimeType?.startsWith("video/") == true,
                                    onClick = { viewerAsset = asset },
                                )
                                if (asset.state.equals("archived", ignoreCase = true)) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.requestDiscard(asset) },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Discard from PC library",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.loading && state.items.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }

    viewerAsset?.let { asset ->
        AuthenticatedImageViewer(
            asset = asset,
            imageUrl = viewModel.originalUrl(asset.id),
            authHeader = auth,
            onDismiss = { viewerAsset = null },
        )
    }

    if (state.discardTarget != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDiscard,
            title = { Text("Discard forever?") },
            text = {
                Text("Removes this item from the PC library. This cannot be undone from the phone.")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDiscard) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDiscard) { Text("Cancel") }
            },
        )
    }
}
