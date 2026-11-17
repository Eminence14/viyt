package com.viyt.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.viyt.app.core.DownloadFormat
import com.viyt.app.core.Engine
import com.viyt.app.core.Item
import com.viyt.app.core.asDuration
import com.viyt.app.download.DownloadService

/** Loads a list with a suspend function and shows loading, error, empty or the rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemList(
    key: String,
    load: suspend (fresh: Boolean) -> List<Item>,
    onOpen: (Item) -> Unit,
    onDownload: (Item) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    var items by remember(key) { mutableStateOf<List<Item>?>(null) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var refreshing by remember(key) { mutableStateOf(false) }
    var nonce by remember(key) { mutableIntStateOf(0) }
    var fresh by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, nonce) {
        error = null
        try {
            items = load(fresh)
        } catch (t: Throwable) {
            error = Engine.friendly(t)
        }
        refreshing = false
        fresh = false
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true; fresh = true; nonce++ },
        modifier = Modifier.fillMaxSize(),
    ) {
        val list = items
        when {
            error != null && list == null -> Message(error!!, "Try again") { nonce++ }
            list == null -> Loading()
            list.isEmpty() -> Message("Nothing here yet.", "Refresh") { fresh = true; nonce++ }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (header != null) item { header() }
                items(list, key = { it.kind.name + it.id }) { ItemRow(it, onOpen, onDownload) }
            }
        }
    }
}

@Composable
fun ItemRow(item: Item, onOpen: (Item) -> Unit, onDownload: (Item) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(item) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(156.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (item.thumbnail != null) {
                AsyncImage(
                    model = item.thumbnail, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            }
            val badge = when (item.kind) {
                Item.Kind.VIDEO -> item.durationSec?.asDuration()
                Item.Kind.PLAYLIST -> "Playlist"
                Item.Kind.CHANNEL -> "Channel"
            }
            if (badge != null) {
                Text(
                    badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                item.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
            )
            if (item.channel != null) {
                Text(
                    item.channel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        if (item.kind == Item.Kind.VIDEO) {
            IconButton(onClick = { onDownload(item) }) {
                Icon(Icons.Outlined.Download, contentDescription = "Download")
            }
        } else {
            Icon(
                Icons.Outlined.PlaylistPlay, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Talking to YouTube", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    // LazyColumn wrapper keeps pull-to-refresh working on empty/error states.
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (action != null) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onAction) { Text(action) }
                }
            }
        }
    }
}

/** Bottom sheet listing download formats (and optionally "Play"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(
    title: String,
    url: String,
    showPlay: Boolean = false,
    onPlay: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            title, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
        )
        if (showPlay) {
            Button(onClick = { onPlay(); onDismiss() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text("Play now")
            }
        }
        DownloadFormat.entries.forEach { f ->
            ListItem(
                headlineContent = { Text(f.label) },
                leadingContent = { Icon(Icons.Outlined.Download, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    DownloadService.enqueue(context, url, title, f)
                    onDismiss()
                }
            )
        }
        Spacer(Modifier.size(24.dp))
    }
}
