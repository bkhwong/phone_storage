package com.phonesync.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Square thumbnail tile with an optional selection checkmark (Google Photos / One UI Gallery
 * style: whole tile dims slightly + rounds down + shows a filled check in the corner) and an
 * optional video play badge. Used for both on-device (Archive) and server (Browse) grids.
 */
@Composable
fun SelectableThumbnail(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    selected: Boolean = false,
    isVideo: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = if (selected) MaterialTheme.shapes.medium else MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (selected) {
                        Modifier
                            .padding(6.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                    } else {
                        Modifier
                    },
                ),
        )
        if (isVideo) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
            )
        }
        if (selectable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f),
                    )
                    .border(
                        width = if (selected) 0.dp else 1.5.dp,
                        color = Color.White.copy(alpha = 0.9f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** Coil request for a local on-device [android.net.Uri] (no auth headers needed). */
@Composable
fun rememberLocalThumbRequest(uriString: String): ImageRequest {
    val context = LocalContext.current
    return ImageRequest.Builder(context)
        .data(uriString)
        .crossfade(150)
        .build()
}

/** Coil request for a server-hosted thumbnail, with the device-token auth header attached. */
@Composable
fun rememberAuthedThumbRequest(url: String, authHeader: Pair<String, String>?): ImageRequest {
    val context = LocalContext.current
    return ImageRequest.Builder(context)
        .data(url)
        .apply { authHeader?.let { (name, value) -> addHeader(name, value) } }
        .crossfade(150)
        .build()
}
