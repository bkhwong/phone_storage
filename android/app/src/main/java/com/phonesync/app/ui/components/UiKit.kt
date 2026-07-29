package com.phonesync.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonesync.app.ui.theme.JakeBlack
import com.phonesync.app.ui.theme.JakeGlass
import com.phonesync.app.ui.theme.JakeGlassBorder
import com.phonesync.app.ui.theme.JakeGlowBlue
import com.phonesync.app.ui.theme.JakeYellow

/** Ambient glow blobs so frosted panels read as liquid glass. */
@Composable
fun GlassScene(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JakeBlack),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-20).dp)
                .size(300.dp)
                .blur(100.dp)
                .background(JakeYellow.copy(alpha = 0.28f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-70).dp, y = 20.dp)
                .size(320.dp)
                .blur(110.dp)
                .background(JakeGlowBlue.copy(alpha = 0.45f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 60.dp)
                .size(340.dp)
                .blur(120.dp)
                .background(Color.White.copy(alpha = 0.14f), CircleShape),
        )
        content()
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.45f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.04f),
                    ),
                ),
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.7f),
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.08f),
                    ),
                ),
                shape = shape,
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun BrandMark(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Int = 72,
) {
    val shape = RoundedCornerShape((size * 0.28f).dp)
    Box(
        modifier = modifier
            .size(size.dp)
            .shadow(16.dp, shape)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.08f),
                    ),
                ),
            )
            .border(1.dp, JakeGlassBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = JakeYellow,
            modifier = Modifier.size((size * 0.42f).dp),
        )
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = JakeGlass,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassPanel(modifier = modifier, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val shape = MaterialTheme.shapes.large
    if (filled) {
        Card(
            onClick = onClick,
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = JakeYellow),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = shape,
        ) {
            TileContent(icon, label, onIcon = JakeBlack, onLabel = JakeBlack, iconBg = JakeBlack.copy(alpha = 0.12f))
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.1f),
                            ),
                        ),
                    ),
                    shape,
                ),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = shape,
        ) {
            TileContent(
                icon,
                label,
                onIcon = JakeYellow,
                onLabel = Color.White,
                iconBg = Color.White.copy(alpha = 0.12f),
            )
        }
    }
}

@Composable
private fun TileContent(
    icon: ImageVector,
    label: String,
    onIcon: Color,
    onLabel: Color,
    iconBg: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onIcon,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = onLabel,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatusChip(
    label: String,
    positive: Boolean?,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = when (positive) {
        true -> JakeYellow.copy(alpha = 0.2f) to JakeYellow
        false -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        null -> Color.White.copy(alpha = 0.1f) to Color(0xFFCBCBCB)
    }
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.35f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(fg),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}

@Composable
fun EmptyHint(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, JakeGlassBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = JakeYellow,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF888888),
        )
    }
}
