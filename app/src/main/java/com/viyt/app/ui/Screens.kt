package com.viyt.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.viyt.app.core.Chip
import com.viyt.app.core.Engine
import com.viyt.app.core.Feed
import com.viyt.app.core.Item
import com.viyt.app.core.LibraryFeed
import com.viyt.app.download.DownloadService
import com.viyt.app.download.DownloadTask
import com.viyt.app.download.Downloads
import kotlinx.coroutines.delay

// ------------------------------------------------------------------ feed state

/** Shows the last saved copy instantly, then refreshes from the network. */
@Stable
class FeedHolder(initial: Feed?) {
    var feed by mutableStateOf(initial)
    var error by mutableStateOf<String?>(null)
    var refreshing by mutableStateOf(false)
    var trigger by mutableIntStateOf(0)
    var fresh = false
    fun refresh() { fresh = true; refreshing = true; trigger++ }
}

@Composable
fun rememberFeed(key: Any, peekKey: String?, load: suspend (fresh: Boolean) -> Feed): FeedHolder {
    val account by Engine.accountVersion.collectAsState()
    val h = remember(key, account) { FeedHolder(peekKey?.let { Engine.peek(it) }) }
    LaunchedEffect(key, account, h.trigger) {
        try {
            h.feed = load(h.fresh)
            h.error = null
        } catch (t: Throwable) {
            h.error = Engine.friendly(t)
        }
        h.fresh = false
        h.refreshing = false
    }
    return h
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedList(
    h: FeedHolder,
    big: Boolean,
    a: Actions,
    empty: String = "Nothing here yet.",
    header: (LazyListScope.() -> Unit)? = null,
    body: (LazyListScope.(List<Item>) -> Unit)? = null,
) {
    PullToRefreshBox(isRefreshing = h.refreshing, onRefresh = { h.refresh() }, modifier = Modifier.fillMaxSize()) {
        val list = h.feed?.items
        when {
            list == null && h.error != null -> Message(h.error!!, "Retry") { h.refresh() }
            list == null -> Column { CardSkeletons(big) }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                header?.invoke(this)
                if (list.isEmpty()) item { Box(Modifier.fillMaxWidth().height(300.dp)) { Message(empty) } }
                else if (body != null) body(list)
                else items(list, key = { it.kind.name + it.id }) { ItemView(it, a, big) }
            }
        }
    }
}

// ------------------------------------------------------------------ top bar

@Composable
fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 30.dp, height = 21.dp).background(YtRed, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(17.dp))
        }
        Text("viyt", fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, modifier = Modifier.padding(start = 3.dp))
    }
}

@Composable
fun MainTopBar(onSearch: () -> Unit, onDownloads: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Logo()
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDownloads) { Icon(Icons.Outlined.Download, "Downloads") }
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Search") }
    }
}

@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun ChipRow(chips: List<Chip>, selected: Chip?, onSelect: (Chip) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips, key = { it.label }) { c ->
            val on = (selected?.label ?: chips.firstOrNull()?.label) == c.label
            Text(
                c.label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = if (on) YtBg else YtText,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) YtText else YtChip)
                    .clickable { onSelect(c) }.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ Home

@Composable
fun HomeScreen(a: Actions, openShort: (List<Item>, Int) -> Unit, onSearch: () -> Unit, onDownloads: () -> Unit) {
    val home = rememberFeed("home", "home") { fresh -> Engine.home(fresh) }
    var chip by remember { mutableStateOf<Chip?>(null) }
    val chips = home.feed?.chips.orEmpty()
    val chipFeed = chip?.takeIf { it.token != null || it.query != null }?.let { c ->
        rememberFeed("chip:${c.label}", "chip:${c.label}:${c.token ?: c.query}") { fresh -> Engine.chip(c, fresh) }
    }
    Column(Modifier.fillMaxSize()) {
        MainTopBar(onSearch, onDownloads)
        if (chipFeed != null) {
            FeedList(chipFeed, big = true, a = a, header = { item { ChipRow(chips, chip) { chip = it } } })
        } else {
            FeedList(home, big = true, a = a, header = { if (chips.isNotEmpty()) item { ChipRow(chips, chip) { chip = it } } }) { list ->
                val shorts = list.filter { it.short }
                val videos = list.filter { !it.short }
                videos.forEachIndexed { i, it ->
                    item(key = "v" + it.kind.name + it.id) { ItemView(it, a, big = true) }
                    if (i == 1 && shorts.size >= 2) item(key = "shorts-shelf") { ShortsShelf(shorts, openShort) }
                }
            }
        }
    }
}

@Composable
fun ShortsShelf(shorts: List<Item>, openShort: (List<Item>, Int) -> Unit) {
    Column(Modifier.padding(bottom = 18.dp)) {
        Row(Modifier.padding(start = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).background(YtRed, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
            Text("Shorts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shorts.size) { i -> ShortCard(shorts[i], 168.dp) { openShort(shorts, i) } }
        }
    }
}

// ------------------------------------------------------------------ Subscriptions

@Composable
fun SubscriptionsScreen(a: Actions, onSearch: () -> Unit, onDownloads: () -> Unit, onSignIn: () -> Unit) {
    val account by Engine.accountVersion.collectAsState()
    Column(Modifier.fillMaxSize()) {
        MainTopBar(onSearch, onDownloads)
        if (!Engine.signedIn) {
            SignInPrompt("Don't miss new videos", "Sign in to see updates from your favourite YouTube channels", onSignIn)
            return@Column
        }
        val channels = rememberFeed("channels$account", "lib:CHANNELS") { fresh -> Engine.library(LibraryFeed.CHANNELS, fresh) }
        val feed = rememberFeed("subs$account", "lib:SUBSCRIPTIONS") { fresh -> Engine.library(LibraryFeed.SUBSCRIPTIONS, fresh) }
        FeedList(feed, big = true, a = a, header = {
            val ch = channels.feed?.items.orEmpty()
            if (ch.isNotEmpty()) item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(ch, key = { it.id }) { c ->
                        Column(Modifier.width(64.dp).clickable { a.open(c) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(c.avatar ?: c.thumbnail, 56.dp)
                            Text(c.title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = YtSecondary,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        })
    }
}

@Composable
fun SignInPrompt(title: String, body: String, onSignIn: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, color = YtSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = onSignIn, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3EA6FF), contentColor = YtBg),
            shape = RoundedCornerShape(50)) { Text("Sign in", fontWeight = FontWeight.Medium) }
    }
}

// ------------------------------------------------------------------ You

@Composable
fun YouScreen(
    a: Actions,
    onSearch: () -> Unit,
    onLibrary: (LibraryFeed) -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    onSignIn: () -> Unit,
) {
    val account by Engine.accountVersion.collectAsState()
    val active = Downloads.tasks.collectAsState().value
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Search") }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF5E35B1)), contentAlignment = Alignment.Center) {
                        Text("V", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        if (Engine.signedIn) {
                            Text("Your YouTube account", style = MaterialTheme.typography.titleMedium)
                            Text("Signed in on this phone", color = YtSecondary, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("You're not signed in", style = MaterialTheme.typography.titleMedium)
                            Text("Sign in", color = Color(0xFF3EA6FF), modifier = Modifier.clickable(onClick = onSignIn).padding(top = 4.dp))
                        }
                    }
                }
            }
            if (Engine.signedIn) {
                item(key = "hist$account") { HistoryShelf(a) { onLibrary(LibraryFeed.HISTORY) } }
            }
            item {
                Spacer(Modifier.height(8.dp))
                LibRow(Icons.Outlined.Download, "Downloads", "${active.count { it.status == DownloadTask.Status.DONE }} saved" +
                    active.count { it.active }.let { if (it > 0) " · $it in progress" else "" }, onDownloads)
                if (Engine.signedIn) {
                    LibRow(Icons.Outlined.History, "History", null) { onLibrary(LibraryFeed.HISTORY) }
                    LibRow(Icons.AutoMirrored.Outlined.PlaylistPlay, "Playlists", null) { onLibrary(LibraryFeed.PLAYLISTS) }
                    LibRow(Icons.Outlined.Schedule, "Watch later", null) { onLibrary(LibraryFeed.WATCH_LATER) }
                    LibRow(Icons.Outlined.ThumbUp, "Liked videos", null) { onLibrary(LibraryFeed.LIKED) }
                }
                LibRow(Icons.Outlined.Settings, "Settings", null, onSettings)
            }
        }
    }
}

@Composable
private fun HistoryShelf(a: Actions, viewAll: () -> Unit) {
    val h = rememberFeed("you-history", "lib:HISTORY") { fresh -> Engine.library(LibraryFeed.HISTORY, fresh) }
    val recent = h.feed?.items.orEmpty().filter { it.kind == Item.Kind.VIDEO }.take(15)
    if (recent.isEmpty()) return
    Column(Modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("View all", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(50)).border().clickable(onClick = viewAll).padding(horizontal = 12.dp, vertical = 6.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(recent, key = { it.id }) { v ->
                Column(Modifier.width(150.dp).clickable { a.open(v) }) {
                    Thumbnail(v, Modifier.fillMaxWidth().aspectRatio(16f / 9f), corner = 8.dp)
                    Text(v.title, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    v.channel?.let { Text(it, fontSize = 12.sp, color = YtSecondary, maxLines = 1) }
                }
            }
        }
    }
}

private fun Modifier.border() = this.background(YtChip)

@Composable
private fun LibRow(icon: ImageVector, label: String, sub: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = 22.dp).weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = YtSecondary) }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = YtSecondary)
    }
}

// ------------------------------------------------------------------ library / playlist / channel pages

@Composable
fun ListPage(title: String, h: FeedHolder, a: Actions, onBack: () -> Unit, big: Boolean = false) {
    Column(Modifier.fillMaxSize()) {
        BackTopBar(title, onBack)
        FeedList(h, big = big, a = a)
    }
}

// ------------------------------------------------------------------ Downloads

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val tasks by Downloads.tasks.collectAsState()
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Downloads", onBack) {
            if (tasks.any { !it.active }) TextButton(onClick = { Downloads.clearFinished() }) { Text("Clear", color = YtText) }
        }
        if (tasks.isEmpty()) {
            Message("Videos you download appear here.\nTap ⋮ on any video, or Download on the watch page.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(tasks, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth().clickable(enabled = t.status == DownloadTask.Status.DONE && t.resultUri != null) {
                    val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(t.resultUri), t.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(i) }.onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
                }.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val id = com.viyt.app.core.InnerTube.videoId(t.url)
                    Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(YtChip)) {
                        if (id != null) AsyncImage("https://i.ytimg.com/vi/$id/mqdefault.jpg", null, modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val state = when (t.status) {
                            DownloadTask.Status.QUEUED -> "Waiting"
                            DownloadTask.Status.RUNNING -> (t.message ?: "Downloading") + if (t.progress > 0) " · ${(t.progress * 100).toInt()}%" else ""
                            DownloadTask.Status.DONE -> "Downloaded · ${t.format.label}"
                            DownloadTask.Status.FAILED -> "Failed: ${t.message}"
                            DownloadTask.Status.CANCELED -> "Canceled"
                        }
                        Text(state, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                            color = if (t.status == DownloadTask.Status.FAILED) MaterialTheme.colorScheme.error else YtSecondary,
                            modifier = Modifier.padding(top = 4.dp))
                        if (t.status == DownloadTask.Status.RUNNING) {
                            Spacer(Modifier.height(6.dp))
                            if (t.progress > 0f) LinearProgressIndicator(progress = { t.progress }, color = YtRed, trackColor = YtChip, modifier = Modifier.fillMaxWidth())
                            else LinearProgressIndicator(color = YtRed, trackColor = YtChip, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (t.active) IconButton(onClick = { DownloadService.cancel(context, t.id) }) { Icon(Icons.Outlined.Close, "Cancel") }
                    else Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Search

@Composable
fun SearchScreen(initial: String, a: Actions, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var query by rememberSaveable { mutableStateOf(initial) }
    var typing by rememberSaveable { mutableStateOf(initial.isEmpty()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(text, typing) {
        if (!typing || text.isBlank()) { suggestions = emptyList(); return@LaunchedEffect }
        delay(120)
        suggestions = Engine.suggest(text)
    }
    LaunchedEffect(typing) { if (typing) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Row(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(50)).background(YtChip).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = text, onValueChange = { text = it; typing = true },
                    singleLine = true, textStyle = TextStyle(color = YtText, fontSize = 16.sp),
                    cursorBrush = SolidColor(YtText),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) { query = text.trim(); typing = false } }),
                    modifier = Modifier.weight(1f).focusRequester(focus).clickable { typing = true },
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("Search YouTube", color = YtSecondary, fontSize = 16.sp)
                        inner()
                    },
                )
                if (text.isNotEmpty()) Icon(Icons.Outlined.Close, "Clear", modifier = Modifier.size(20.dp).clickable { text = ""; typing = true })
            }
        }
        if (typing || query.isEmpty()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(suggestions) { s ->
                    Row(Modifier.fillMaxWidth().clickable { text = s; query = s; typing = false }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null, tint = YtSecondary, modifier = Modifier.size(20.dp))
                        Text(s, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 18.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.NorthWest, "Use", tint = YtSecondary, modifier = Modifier.size(20.dp).clickable { text = "$s " })
                    }
                }
            }
        } else {
            val q = query
            if (q.startsWith("http")) {
                val id = com.viyt.app.core.InnerTube.videoId(q)
                LaunchedEffect(q) { if (id != null) a.open(Item(id, "", "https://www.youtube.com/watch?v=$id", null, null, "https://i.ytimg.com/vi/$id/hqdefault.jpg", Item.Kind.VIDEO)) }
                if (id == null) {
                    val h = rememberFeed("link:$q", "url:$q") { fresh -> Engine.list(q, fresh) }
                    FeedList(h, big = false, a = a)
                } else Message("Opening link…")
            } else {
                val h = rememberFeed("search:$q", "search:$q") { _ -> Engine.search(q) }
                FeedList(h, big = true, a = a, empty = "No results for \"$q\"")
            }
        }
    }
}

// ------------------------------------------------------------------ sign-in helper

@Composable
fun rememberSignIn(): () -> Unit {
    val context = LocalContext.current
    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Engine.signedIn) Engine.onAccountChanged()
    }
    return { login.launch(Intent(context, LoginActivity::class.java)) }
}
