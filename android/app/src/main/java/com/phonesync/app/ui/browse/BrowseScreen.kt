package com.phonesync.app.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.phonesync.app.data.remote.AssetDto
import com.phonesync.app.data.repository.PhotoSyncRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    repository: PhotoSyncRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf<String?>("archived") }
    var items by remember { mutableStateOf<List<AssetDto>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discardTarget by remember { mutableStateOf<AssetDto?>(null) }
    var viewerUrl by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val auth = remember { repository.authHeader() }

    fun load(reset: Boolean) {
        scope.launch {
            loading = true
            error = null
            runCatching {
                val (page, next) = repository.browseAssets(
                    state = filter,
                    cursor = if (reset) null else cursor,
                )
                items = if (reset) page else items + page
                cursor = next
            }.onFailure {
                error = it.message ?: "Browse failed (is PC online?)"
            }
            loading = false
        }
    }

    LaunchedEffect(filter) {
        items = emptyList()
        cursor = null
        load(reset = true)
    }

    LaunchedEffect(gridState, cursor, loading) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, total) ->
                if (!loading && cursor != null && total > 0 && last >= total - 6) {
                    load(reset = false)
                }
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Library on PC") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text("All") },
                )
                FilterChip(
                    selected = filter == "backed_up",
                    onClick = { filter = "backed_up" },
                    label = { Text("Backed up") },
                )
                FilterChip(
                    selected = filter == "archived",
                    onClick = { filter = "archived" },
                    label = { Text("Archived") },
                )
            }
            if (error != null) {
                Text(
                    error!!,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    state = gridState,
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items, key = { it.id }) { asset ->
                        val thumbUrl = repository.thumbnailUrl(asset.id)
                        val request = ImageRequest.Builder(context)
                            .data(thumbUrl)
                            .apply {
                                auth?.let { (name, value) -> addHeader(name, value) }
                            }
                            .crossfade(true)
                            .build()
                        Box {
                            AsyncImage(
                                model = request,
                                contentDescription = asset.originalFilename,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        viewerUrl = repository.originalUrl(asset.id)
                                    },
                            )
                            if (asset.state.equals("archived", ignoreCase = true)) {
                                IconButton(
                                    onClick = { discardTarget = asset },
                                    modifier = Modifier.align(Alignment.TopEnd),
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Discard",
                                        tint = androidx.compose.ui.graphics.Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
                if (loading && items.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }

    viewerUrl?.let { url ->
        AuthenticatedImageViewer(
            url = url,
            authHeader = auth,
            onDismiss = { viewerUrl = null },
        )
    }

    discardTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { discardTarget = null },
            title = { Text("Discard forever?") },
            text = {
                Text("Removes this item from the PC library. This cannot be undone from the phone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching {
                                repository.discardServerAsset(target.id, local = null)
                                items = items.filterNot { it.id == target.id }
                            }.onFailure { error = it.message }
                            discardTarget = null
                        }
                    },
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { discardTarget = null }) { Text("Cancel") }
            },
        )
    }
}
