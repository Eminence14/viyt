package com.viyt.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.viyt.app.core.DownloadFormat
import com.viyt.app.core.Item
import com.viyt.app.core.asDuration
import com.viyt.app.download.DownloadService

/** What a card does when tapped: open a video, a playlist or a channel. */
class Actions(
    val open: (Item) -> Unit,
    val more: (Item) -> Unit,
)

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(YtChip)) {
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier, red: Boolean = false) {
    Text(
        text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.padding(6.dp)
            .background(if (red) YtRed else Color(0xCC000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
fun Thumbnail(item: Item, modifier: Modifier, corner: Dp = 0.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(YtChip)) {
        if (item.thumbnail != null) {
            AsyncImage(item.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        when {
            item.live -> Badge("LIVE", Modifier.align(Alignment.BottomEnd), red = true)
            item.kind == Item.Kind.VIDEO && item.durationSec != null -> Badge(item.durationSec.asDuration(), Modifier.align(Alignment.BottomEnd))
            item.kind == Item.Kind.PLAYLIST -> Row(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(item.meta?.substringBefore(" ") ?: "Playlist", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

private fun subtitle(item: Item): String =
    listOfNotNull(item.channel, item.meta).joinToString(" · ")

/** Home / search card: full-width thumbnail, avatar, two-line title, "Channel · views · age". */
@Composable
fun VideoCard(item: Item, a: Actions) {
    Column(Modifier.fillMaxWidth().clickable { a.open(item) }.padding(bottom = 18.dp)) {
        Thumbnail(item, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Row(Modifier.padding(start = 12.dp, top = 10.dp)) {
            Avatar(item.avatar, 36.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp, top = 1.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle(item), style = MaterialTheme.typography.bodySmall, color = YtSecondary, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            MoreButton { a.more(item) }
        }
    }
}

/** Library / playlist / up-next row: small thumbnail on the left. */
@Composable
fun CompactRow(item: Item, a: Actions) {
    Row(Modifier.fillMaxWidth().clickable { a.open(item) }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
        Thumbnail(item, Modifier.width(160.dp).aspectRatio(16f / 9f), corner = 8.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.channel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = YtSecondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            item.meta?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = YtSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.kind == Item.Kind.VIDEO) MoreButton { a.more(item) } else Spacer(Modifier.width(12.dp))
    }
}

@Composable
fun ChannelRow(item: Item, a: Actions) {
    Row(Modifier.fillMaxWidth().clickable { a.open(item) }.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(160.dp), contentAlignment = Alignment.Center) { Avatar(item.avatar ?: item.thumbnail, 88.dp) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.meta?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = YtSecondary) }
        }
    }
}

/** Vertical Shorts card (shelf on Home). */
@Composable
fun ShortCard(item: Item, width: Dp, onOpen: (Item) -> Unit) {
    Box(Modifier.width(width).aspectRatio(9f / 16f).clip(RoundedCornerShape(10.dp)).background(YtChip).clickable { onOpen(item) }) {
        AsyncImage(item.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Text(item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2,
                overflow = TextOverflow.Ellipsis, style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 6f)))
            item.meta?.let { Text(it, color = Color.White, fontSize = 12.sp, style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 6f))) }
        }
    }
}

/** Picks the right card for an item. [big] uses Home-style cards for videos. */
@Composable
fun ItemView(item: Item, a: Actions, big: Boolean) {
    when (item.kind) {
        Item.Kind.CHANNEL -> ChannelRow(item, a)
        Item.Kind.PLAYLIST -> CompactRow(item, a)
        Item.Kind.VIDEO -> if (big) VideoCard(item, a) else CompactRow(item, a)
    }
}

@Composable
fun MoreButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Outlined.MoreVert, "More", modifier = Modifier.size(20.dp))
    }
}

// ------------------------------------------------------------------ skeletons

@Composable
private fun pulse(): Color {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.5f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    return YtChip.copy(alpha = a)
}

@Composable
fun CardSkeletons(big: Boolean) {
    val c = pulse()
    Column(Modifier.fillMaxSize()) {
        repeat(if (big) 3 else 8) {
            if (big) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(c))
                Row(Modifier.padding(12.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(c))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Box(Modifier.fillMaxWidth(0.9f).height(14.dp).background(c, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(0.6f).height(12.dp).background(c, RoundedCornerShape(4.dp)))
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Box(Modifier.width(160.dp).aspectRatio(16f / 9f).background(c, RoundedCornerShape(8.dp)))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Box(Modifier.fillMaxWidth(0.95f).height(13.dp).background(c, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth(0.7f).height(13.dp).background(c, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(0.4f).height(11.dp).background(c, RoundedCornerShape(4.dp)))
                    }
                }
            }
        }
    }
}

@Composable
fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, textAlign = TextAlign.Center, color = YtSecondary)
                if (action != null) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onAction) { Text(action, color = YtText) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ sheets

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp))
    }
}

fun share(context: Context, url: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), "Share"))
}

/** The three-dot menu on a card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(item: Item, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var download by remember { mutableStateOf(false) }
    if (download) {
        DownloadSheet(item.title, item.url, onDismiss)
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = YtRaised, dragHandle = null) {
        Column(Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
            SheetRow(Icons.Outlined.Download, "Download video") { download = true }
            SheetRow(Icons.Outlined.Share, "Share") { share(context, item.url); onDismiss() }
        }
    }
}

/** YouTube-style "Download quality" sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(title: String, url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = YtRaised) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Download quality", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, color = YtSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            Spacer(Modifier.height(6.dp))
            DownloadFormat.entries.forEach { f ->
                Row(Modifier.fillMaxWidth().clickable {
                    DownloadService.enqueue(context, url, title, f)
                    android.widget.Toast.makeText(context, "Downloading · ${f.label}", android.widget.Toast.LENGTH_SHORT).show()
                    onDismiss()
                }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Download, null, modifier = Modifier.size(22.dp))
                    Text(f.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp))
                }
            }
        }
    }
}
