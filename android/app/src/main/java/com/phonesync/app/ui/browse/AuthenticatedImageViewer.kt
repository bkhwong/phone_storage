package com.phonesync.app.ui.browse

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.phonesync.app.data.remote.AssetDto
import com.phonesync.app.ui.common.formatBytes

@Composable
fun AuthenticatedImageViewer(
    asset: AssetDto,
    imageUrl: String,
    authHeader: Pair<String, String>?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isVideo = asset.mimeType?.startsWith("video/") == true

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (isVideo) {
                VideoPlaceholder(asset)
            } else {
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }

                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .apply { authHeader?.let { (n, v) -> addHeader(n, v) } }
                    .build()
                val painter = rememberAsyncImagePainter(request)

                Image(
                    painter = painter,
                    contentDescription = asset.originalFilename ?: "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
                )
                if (painter.state is AsyncImagePainter.State.Loading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        asset.originalFilename ?: "Untitled",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        formatBytes(asset.sizeBytes),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
private fun VideoPlaceholder(asset: AssetDto) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(
            Icons.Default.PlayCircle,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(72.dp),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
        Text(
            asset.originalFilename ?: "Video",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(
            "Video preview isn't supported in-app yet. Open your PC library to watch it.",
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
