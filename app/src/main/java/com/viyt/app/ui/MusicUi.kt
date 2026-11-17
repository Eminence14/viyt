package com.viyt.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.viyt.app.core.DownloadFormat
import com.viyt.app.core.Engine
import com.viyt.app.core.Item
import com.viyt.app.core.Music
import com.viyt.app.core.MusicItem
import com.viyt.app.core.MusicPage
import com.viyt.app.core.Shelf
import com.viyt.app.core.Track
import com.viyt.app.core.asDuration
import com.viyt.app.download.DownloadService
import com.viyt.app.player.MusicQueue
import com.viyt.app.player.PlayerHolder
import kotlinx.coroutines.delay

/** Music-mode UI state shared with the rest of the app. */
object MusicUi {
    var on by mutableStateOf(false)
    var nowPlaying by mutableStateOf(false)
}

private val MusicBg = Color(0xFF030303)

private sealed interface MRoute {
    data object Search : MRoute
    data class Page(val browseId: String, val title: String) : MRoute
}

private enum class MTab(val label: String, val on: ImageVector, val off: ImageVector) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    EXPLORE("Explore", Icons.Filled.Explore, Icons.Outlined.Explore),
    LIBRARY("Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
}

// ------------------------------------------------------------------ loading helper

@Stable
class Loader<T>(initial: T?) {
    var value by mutableStateOf(initial)
    var error by mutableStateOf<String?>(null)
    var refreshing by mutableStateOf(false)
    var trigger by mutableIntStateOf(0)
    var fresh = false
    fun refresh() { fresh = true; refreshing = true; trigger++ }
}

@Composable
fun <T> rememberLoader(key: Any, load: suspend (fresh: Boolean) -> T): Loader<T> {
    val account by Engine.accountVersion.collectAsState()
    val l = remember(key, account) { Loader<T>(null) }
    LaunchedEffect(key, account, l.trigger) {
        try { l.value = load(l.fresh); l.error = null } catch (t: Throwable) { l.error = Engine.friendly(t) }
        l.fresh = false
        l.refreshing = false
    }
    return l
}

// ------------------------------------------------------------------ actions

private class MActions(
    val ctx: Context,
    val open: (MusicItem) -> Unit,
    val more: (MusicItem) -> Unit,
    /** Play [items] starting at [i] (album/playlist rows), or a radio for a single song. */
    val playFrom: (List<MusicItem>, Int) -> Unit,
)

private fun playSong(ctx: Context, item: MusicItem) {
    Watch.detach()
    MusicQueue.playRadio(ctx, item.toTrack())
    MusicUi.nowPlaying = true
}

private fun playList(ctx: Context, list: List<MusicItem>, at: Int, shuffle: Boolean = false) {
    val tracks = list.filter { it.playable }.map { it.toTrack() }
    if (tracks.isEmpty()) return
    Watch.detach()
    val start = list.getOrNull(at)?.let { sel -> tracks.indexOfFirst { it.videoId == sel.id } }?.coerceAtLeast(0) ?: 0
    MusicQueue.playList(ctx, tracks, start, shuffle)
    MusicUi.nowPlaying = true
}

// ------------------------------------------------------------------ the Music app

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicApp(onSwitch: () -> Unit, onSignIn: () -> Unit) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(MTab.HOME) }
    val stack = remember { mutableStateListOf<MRoute>() }
    var menu by remember { mutableStateOf<MusicItem?>(null) }
    val queue by MusicQueue.tracks.collectAsState()
    val mode by PlayerHolder.mode.collectAsState()
    val playing = mode == PlayerHolder.Mode.MUSIC && queue.isNotEmpty()

    val a = remember {
        MActions(
            ctx,
            open = { i -> if (i.playable) playSong(ctx, i) else stack.add(MRoute.Page(i.id, i.title)) },
            more = { menu = it },
            playFrom = { list, i -> playList(ctx, list, i) },
        )
    }

    androidx.activity.compose.BackHandler(enabled = MusicUi.nowPlaying) { MusicUi.nowPlaying = false }
    androidx.activity.compose.BackHandler(enabled = !MusicUi.nowPlaying && stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    androidx.activity.compose.BackHandler(enabled = !MusicUi.nowPlaying && stack.isEmpty() && tab != MTab.HOME) { tab = MTab.HOME }

    Box(Modifier.fillMaxSize().background(MusicBg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                when (val top = stack.lastOrNull()) {
                    MRoute.Search -> MusicSearch(a) { stack.removeAt(stack.lastIndex) }
                    is MRoute.Page -> PageScreen(top.browseId, top.title, a) { stack.removeAt(stack.lastIndex) }
                    null -> when (tab) {
                        MTab.HOME -> ShelvesScreen("music-home", a, onSwitch, { stack.add(MRoute.Search) }) { f -> Music.home(f) }
                        MTab.EXPLORE -> ShelvesScreen("music-explore", a, onSwitch, { stack.add(MRoute.Search) }) { f -> Music.explore(f) }
                        MTab.LIBRARY -> LibraryScreen(a, onSwitch, { stack.add(MRoute.Search) }, onSignIn) { id, t -> stack.add(MRoute.Page(id, t)) }
                    }
                }
            }
            if (playing) MusicMini { MusicUi.nowPlaying = true }
            Column(Modifier.fillMaxWidth().background(Color(0xFF0D0D0D)).navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(54.dp)) {
                    MTab.entries.forEach { t ->
                        Column(Modifier.weight(1f).height(54.dp).clickable(remember { MutableInteractionSource() }, null) { tab = t; stack.clear() },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(if (t == tab) t.on else t.off, t.label, tint = YtText, modifier = Modifier.size(24.dp))
                            Text(t.label, fontSize = 11.sp, color = YtText)
                        }
                    }
                }
            }
        }
        if (MusicUi.nowPlaying && playing) {
            Box(Modifier.fillMaxSize()) { NowPlaying(a, onVideo = onSwitch) }
        }
    }
    menu?.let { SongMenu(it, a) { menu = null } }
}

// ------------------------------------------------------------------ top bar

@Composable
fun MusicLogo(onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(YtRed), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(17.dp))
        }
        Text("Music", fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, modifier = Modifier.padding(start = 5.dp))
        Icon(Icons.Outlined.KeyboardArrowDown, "Switch to videos", tint = YtSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MusicTopBar(onSwitch: () -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(54.dp).padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        MusicLogo(onSwitch)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Search music") }
    }
}

// ------------------------------------------------------------------ shelves (Home, Explore, Artist)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShelvesScreen(key: String, a: MActions, onSwitch: () -> Unit, onSearch: () -> Unit, load: suspend (Boolean) -> List<Shelf>) {
    val l = rememberLoader(key, load)
    Column(Modifier.fillMaxSize()) {
        MusicTopBar(onSwitch, onSearch)
        PullToRefreshBox(isRefreshing = l.refreshing, onRefresh = { l.refresh() }, modifier = Modifier.fillMaxSize()) {
            val shelves = l.value
            when {
                shelves == null && l.error != null -> Message(l.error!!, "Retry") { l.refresh() }
                shelves == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = YtText) }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(shelves.size) { i -> ShelfView(shelves[i], a) }
                }
            }
        }
    }
}

@Composable
private fun ShelfView(s: Shelf, a: MActions) {
    Column(Modifier.padding(top = 18.dp)) {
        if (s.title.isNotEmpty()) Text(s.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
        if (s.list) {
            // "Quick picks" style: columns of four song rows that scroll sideways.
            val cols = s.items.chunked(4)
            LazyRow(contentPadding = PaddingValues(end = 8.dp)) {
                items(cols.size) { c ->
                    Column(Modifier.width(320.dp)) {
                        cols[c].forEach { item -> SongRow(item, a, onClick = { if (item.playable) playSong(a.ctx, item) else a.open(item) }) }
                    }
                }
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(s.items) { CardItem(it, a) }
            }
        }
    }
}

@Composable
private fun CardItem(item: MusicItem, a: MActions) {
    val round = item.kind == MusicItem.Kind.ARTIST
    val video = item.kind == MusicItem.Kind.VIDEO
    val w = if (video) 240.dp else 150.dp
    Column(Modifier.width(w).clickable { a.open(item) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (video) 16f / 9f else 1f).clip(if (round) CircleShape else RoundedCornerShape(6.dp)).background(YtChip)) {
            AsyncImage(item.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp))
        item.subtitle?.let { Text(it, fontSize = 13.sp, color = YtSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun SongRow(item: MusicItem, a: MActions, number: Int? = null, highlighted: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (number != null) {
            Text("$number", color = YtSecondary, fontSize = 15.sp, modifier = Modifier.width(30.dp))
        } else {
            val round = item.kind == MusicItem.Kind.ARTIST
            Box(Modifier.size(52.dp).clip(if (round) CircleShape else RoundedCornerShape(4.dp)).background(YtChip)) {
                AsyncImage(item.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(item.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (highlighted) YtRed else YtText)
            val sub = listOfNotNull(item.subtitle, item.duration).joinToString(" • ")
            if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = YtSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.playable) IconButton(onClick = { a.more(item) }) { Icon(Icons.Outlined.MoreVert, "More", modifier = Modifier.size(20.dp)) }
        else Spacer(Modifier.width(12.dp))
    }
}

// ------------------------------------------------------------------ album / playlist / artist page

@Composable
private fun PageScreen(browseId: String, fallbackTitle: String, a: MActions, onBack: () -> Unit) {
    val l = rememberLoader("mpage:$browseId") { f -> Music.page(browseId, f) }
    val page = l.value
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        }
        when {
            page == null && l.error != null -> Message(l.error!!, "Retry") { l.refresh() }
            page == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = YtText) }
            else -> PageContent(page, fallbackTitle, a)
        }
    }
}

@Composable
private fun PageContent(page: MusicPage, fallbackTitle: String, a: MActions) {
    val artist = page.kind == MusicItem.Kind.ARTIST
    val tracks = page.tracks
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.then(if (artist) Modifier.fillMaxWidth().aspectRatio(1.3f) else Modifier.padding(top = 4.dp).size(230.dp))
                    .clip(if (artist) RoundedCornerShape(0.dp) else RoundedCornerShape(8.dp)).background(YtChip)) {
                    AsyncImage(page.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (artist) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to MusicBg)))
                }
                Text(page.title.ifEmpty { fallbackTitle }, fontSize = if (artist) 30.sp else 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
                page.subtitle?.let { Text(it, fontSize = 13.sp, color = YtSecondary, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp)) }
                if (tracks.isNotEmpty()) Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.clip(RoundedCornerShape(50)).background(YtText).clickable { a.playFrom(tracks, 0) }
                        .padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, null, tint = MusicBg, modifier = Modifier.size(22.dp))
                        Text("Play", color = MusicBg, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
                    }
                    Row(Modifier.clip(RoundedCornerShape(50)).background(YtChip).clickable { playList(a.ctx, tracks, 0, shuffle = true) }
                        .padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Shuffle, null, modifier = Modifier.size(20.dp))
                        Text("Shuffle", fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        page.shelves.forEach { s ->
            if (s.list) {
                if (s.title.isNotEmpty()) item { Text(s.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)) }
                itemsIndexed(s.items) { i, it ->
                    SongRow(it, a, number = if (page.kind == MusicItem.Kind.ALBUM) i + 1 else null) {
                        if (it.playable) a.playFrom(s.items, i) else a.open(it)
                    }
                }
            } else item { ShelfView(s, a) }
        }
    }
}

// ------------------------------------------------------------------ Library

@Composable
private fun LibraryScreen(a: MActions, onSwitch: () -> Unit, onSearch: () -> Unit, onSignIn: () -> Unit, openPage: (String, String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        MusicTopBar(onSwitch, onSearch)
        if (!Engine.signedIn) {
            SignInPrompt("Your music library", "Sign in to see your liked songs, playlists and listening history", onSignIn)
            return@Column
        }
        val l = rememberLoader("music-lib") { f -> Music.page("FEmusic_liked_playlists", f) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                LibEntry(Icons.Outlined.ThumbUp, "Liked music") { openPage("VLLM", "Liked music") }
                LibEntry(Icons.Outlined.History, "History") { openPage("FEmusic_history", "History") }
                Text("Playlists", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp))
            }
            val lists = l.value?.shelves?.flatMap { it.items }.orEmpty().filter { !it.playable }
            if (l.value == null) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = YtText) } }
            items(lists) { p -> SongRow(p, a) { a.open(p) } }
        }
    }
}

@Composable
private fun LibEntry(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)).background(YtChip), contentAlignment = Alignment.Center) { Icon(icon, null) }
        Text(label, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp))
    }
}

// ------------------------------------------------------------------ Search

@Composable
private fun MusicSearch(a: MActions, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var typing by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf(Music.Filter.SONGS) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(text, typing) {
        if (!typing || text.isBlank()) { suggestions = emptyList(); return@LaunchedEffect }
        delay(120)
        suggestions = Engine.suggest(text)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Row(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(YtChip).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = text, onValueChange = { text = it; typing = true }, singleLine = true,
                    textStyle = TextStyle(color = YtText, fontSize = 16.sp), cursorBrush = SolidColor(YtText),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) { query = text.trim(); typing = false } }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner -> if (text.isEmpty()) Text("Search songs, albums, artists", color = YtSecondary, fontSize = 16.sp); inner() },
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
                        Text(s, fontSize = 16.sp, modifier = Modifier.padding(start = 18.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Music.Filter.entries) { f ->
                    val on = f == filter
                    Text(f.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (on) MusicBg else YtText,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) YtText else YtChip).clickable { filter = f }
                            .padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
            val q = query
            val f = filter
            val l = rememberLoader("msearch:${f.name}:$q") { _ -> Music.search(q, f) }
            val results = l.value
            when {
                results == null && l.error != null -> Message(l.error!!, "Retry") { l.refresh() }
                results == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = YtText) }
                results.isEmpty() -> Message("No results for \"$q\"")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(results) { r -> SongRow(r, a) { a.open(r) } }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ mini player

@Composable
fun MusicMini(onExpand: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember { PlayerHolder.get(ctx) }
    val tracks by MusicQueue.tracks.collectAsState()
    val idx by MusicQueue.index.collectAsState()
    val t = tracks.getOrNull(idx) ?: return
    var playing by remember { mutableStateOf(false) }
    var frac by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            playing = player.playWhenReady
            val d = player.duration
            frac = if (d > 0) player.currentPosition.toFloat() / d else 0f
            delay(400)
        }
    }
    Column(Modifier.fillMaxWidth().background(Color(0xFF1D1D1D))) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).background(YtText))
        }
        Row(Modifier.fillMaxWidth().height(62.dp).clickable(onClick = onExpand).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(YtChip)) {
                AsyncImage(t.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                t.artist?.let { Text(it, fontSize = 12.sp, color = YtSecondary, maxLines = 1) }
            }
            IconButton(onClick = { if (player.playWhenReady) player.pause() else player.play() }) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play or pause")
            }
            IconButton(onClick = { player.seekToNextMediaItem() }) { Icon(Icons.Filled.SkipNext, "Next") }
        }
    }
}

// ------------------------------------------------------------------ now playing

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NowPlaying(a: MActions, onVideo: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { PlayerHolder.get(ctx) }
    val tracks by MusicQueue.tracks.collectAsState()
    val idx by MusicQueue.index.collectAsState()
    val t = tracks.getOrNull(idx) ?: return
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var shuffle by remember { mutableStateOf(player.shuffleModeEnabled) }
    var repeat by remember { mutableIntStateOf(player.repeatMode) }
    var queueOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            pos = player.currentPosition; dur = player.duration.coerceAtLeast(0L)
            playing = player.playWhenReady; buffering = player.playbackState == Player.STATE_BUFFERING
            delay(250)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), MusicBg)))
            .statusBarsPadding().navigationBarsPadding().clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { MusicUi.nowPlaying = false }) { Icon(Icons.Outlined.KeyboardArrowDown, "Close") }
            Spacer(Modifier.weight(1f))
            // Song / Video switch, like YouTube Music.
            Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).padding(3.dp)) {
                Text("Song", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MusicBg,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(YtText).padding(horizontal = 16.dp, vertical = 6.dp))
                Text("Video", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                    val at = player.currentPosition
                    MusicUi.nowPlaying = false
                    MusicUi.on = false
                    onVideo()
                    Watch.open(ctx, scope, Item(t.videoId, t.title, "https://www.youtube.com/watch?v=${t.videoId}", t.artist, null,
                        "https://i.ytimg.com/vi/${t.videoId}/hqdefault.jpg", Item.Kind.VIDEO), startMs = at)
                }.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { a.more(MusicItem(MusicItem.Kind.SONG, t.videoId, t.title, t.artist, t.thumb, t.duration)) }) {
                Icon(Icons.Outlined.MoreVert, "More")
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(YtChip)) {
                AsyncImage(Music.big(t.thumb, 1080), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(horizontal = 28.dp)) {
            Text(t.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
            Text(t.artist.orEmpty(), fontSize = 16.sp, color = YtSecondary, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(14.dp))
            Slider(
                value = drag ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { player.seekTo((it * dur).toLong()) }; drag = null },
                colors = SliderDefaults.colors(thumbColor = YtText, activeTrackColor = YtText, inactiveTrackColor = Color(0x33FFFFFF)),
            )
            Row {
                Text(((drag?.let { (it * dur).toLong() } ?: pos) / 1000).asDuration(), fontSize = 12.sp, color = YtSecondary)
                Spacer(Modifier.weight(1f))
                Text(if (dur > 0) (dur / 1000).asDuration() else t.duration.orEmpty(), fontSize = 12.sp, color = YtSecondary)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { shuffle = !shuffle; player.shuffleModeEnabled = shuffle }) {
                    Icon(Icons.Outlined.Shuffle, "Shuffle", tint = if (shuffle) YtText else Color(0x80FFFFFF))
                }
                IconButton(onClick = { if (player.currentPosition > 3000) player.seekTo(0) else player.seekToPreviousMediaItem() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", modifier = Modifier.size(38.dp))
                }
                Box(Modifier.size(72.dp).clip(CircleShape).background(YtText).clickable { if (player.playWhenReady) player.pause() else player.play() },
                    contentAlignment = Alignment.Center) {
                    if (buffering) CircularProgressIndicator(color = MusicBg, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                    else Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play or pause", tint = MusicBg, modifier = Modifier.size(42.dp))
                }
                IconButton(onClick = { player.seekToNextMediaItem() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = {
                    repeat = when (repeat) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
                    player.repeatMode = repeat
                }) {
                    Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat, "Repeat",
                        tint = if (repeat == Player.REPEAT_MODE_OFF) Color(0x80FFFFFF) else YtText)
                }
            }
        }
        Row(Modifier.fillMaxWidth().clickable { queueOpen = true }.padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.QueueMusic, null, modifier = Modifier.size(20.dp))
            Text("UP NEXT", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (queueOpen) QueueSheet { queueOpen = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val tracks by MusicQueue.tracks.collectAsState()
    val idx by MusicQueue.index.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1D1D1D), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().fillMaxHeight(0.85f)) {
            Text("Up next", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            Text("Playing from your mix · new songs keep coming", fontSize = 12.sp, color = YtSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            HorizontalDivider(color = YtDivider, modifier = Modifier.padding(top = 8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(tracks, key = { i, t -> "$i-${t.videoId}" }) { i, t ->
                    Row(Modifier.fillMaxWidth().background(if (i == idx) Color(0x22FFFFFF) else Color.Transparent)
                        .clickable { MusicQueue.jump(ctx, i) }.padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(YtChip)) {
                            AsyncImage(t.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (i == idx) Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(t.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (i == idx) FontWeight.Bold else FontWeight.Normal)
                            Text(listOfNotNull(t.artist, t.duration).joinToString(" • "), fontSize = 13.sp, color = YtSecondary, maxLines = 1)
                        }
                        if (i != idx) IconButton(onClick = { MusicQueue.remove(ctx, i) }) { Icon(Icons.Outlined.Close, "Remove", modifier = Modifier.size(18.dp), tint = YtSecondary) }
                        else Spacer(Modifier.width(48.dp))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ song menu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongMenu(item: MusicItem, a: MActions, onDismiss: () -> Unit) {
    val ctx = a.ctx
    val url = "https://music.youtube.com/watch?v=${item.id}"
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1D1D1D)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(YtChip)) {
                    AsyncImage(item.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(item.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.artist?.let { Text(it, fontSize = 13.sp, color = YtSecondary) }
                }
            }
            HorizontalDivider(color = YtDivider, modifier = Modifier.padding(vertical = 8.dp))
            MenuItem(Icons.Outlined.Radio, "Start radio") { playSong(ctx, item); onDismiss() }
            MenuItem(Icons.AutoMirrored.Outlined.PlaylistPlay, "Play next") { Watch.detach(); MusicQueue.playNext(ctx, item.toTrack()); toast(ctx, "Plays next"); onDismiss() }
            MenuItem(Icons.AutoMirrored.Outlined.PlaylistAdd, "Add to queue") { Watch.detach(); MusicQueue.addToQueue(ctx, item.toTrack()); toast(ctx, "Added to queue"); onDismiss() }
            MenuItem(Icons.Outlined.Download, "Download song (M4A)") {
                DownloadService.enqueue(ctx, "https://www.youtube.com/watch?v=${item.id}", listOfNotNull(item.artist, item.title).joinToString(" - "), DownloadFormat.M4A)
                toast(ctx, "Downloading"); onDismiss()
            }
            MenuItem(Icons.Outlined.Download, "Download song (MP3)") {
                DownloadService.enqueue(ctx, "https://www.youtube.com/watch?v=${item.id}", listOfNotNull(item.artist, item.title).joinToString(" - "), DownloadFormat.MP3)
                toast(ctx, "Downloading"); onDismiss()
            }
            item.artistId?.let { id -> MenuItem(Icons.Outlined.Person, "Go to artist") { MusicUi.nowPlaying = false; a.open(MusicItem(MusicItem.Kind.ARTIST, id, item.artist ?: "", null, null)); onDismiss() } }
            MenuItem(Icons.Outlined.Share, "Share") { share(ctx, url); onDismiss() }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 15.sp, modifier = Modifier.padding(start = 20.dp))
    }
}

private fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
