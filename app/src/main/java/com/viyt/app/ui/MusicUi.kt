package com.viyt.app.ui

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.LibraryAddCheck
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.viyt.app.core.Engine
import com.viyt.app.core.Item
import com.viyt.app.core.LyricLine
import com.viyt.app.core.Lyrics
import com.viyt.app.core.Music
import com.viyt.app.core.MusicItem
import com.viyt.app.core.MusicPage
import com.viyt.app.core.Offline
import com.viyt.app.core.Shelf
import com.viyt.app.core.SyncedLyrics
import com.viyt.app.core.Track
import com.viyt.app.core.asDuration
import com.viyt.app.download.DownloadService
import com.viyt.app.download.Downloads
import com.viyt.app.player.MusicQueue
import com.viyt.app.player.PlayerHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Music-mode UI state shared with the rest of the app. */
object MusicUi {
    var on by mutableStateOf(false)
    var nowPlaying by mutableStateOf(false)
}

private val MusicBg = Color(0xFF030303)
private val SheetBg = Color(0xFF1D1D1D)

private sealed interface MRoute {
    data object Search : MRoute
    data class Page(val browseId: String, val title: String, val params: String? = null) : MRoute
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

@Composable
private fun <T> LoaderBox(l: Loader<T>, content: @Composable (T) -> Unit) {
    val v = l.value
    when {
        v == null && l.error != null -> Message(l.error!!, "Retry") { l.refresh() }
        v == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = YtText) }
        else -> content(v)
    }
}

// ------------------------------------------------------------------ actions

private class MActions(
    val ctx: Context,
    val open: (MusicItem) -> Unit,
    val more: (MusicItem, String?) -> Unit,
    val playFrom: (List<MusicItem>, Int) -> Unit,
)

private fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

private fun playSong(ctx: Context, item: MusicItem) {
    Watch.detach()
    MusicQueue.playRadio(ctx, item.toTrack())
    MusicUi.nowPlaying = true
}

private fun playTracks(ctx: Context, tracks: List<Track>, at: Int, shuffle: Boolean = false) {
    if (tracks.isEmpty()) return
    Watch.detach()
    MusicQueue.playList(ctx, tracks, at, shuffle)
    MusicUi.nowPlaying = true
}

private fun playList(ctx: Context, list: List<MusicItem>, at: Int, shuffle: Boolean = false) {
    val tracks = list.filter { it.playable }.map { it.toTrack() }
    val start = list.getOrNull(at)?.let { sel -> tracks.indexOfFirst { it.videoId == sel.id } }?.coerceAtLeast(0) ?: 0
    playTracks(ctx, tracks, start, shuffle)
}

private fun downloadAll(ctx: Context, items: List<MusicItem>, album: String?) {
    val songs = items.filter { it.playable && !Offline.has(it.id) }
    songs.forEach { DownloadService.enqueueSong(ctx, it.toTrack(), album) }
    toast(ctx, if (songs.isEmpty()) "Already downloaded" else "Downloading ${songs.size} songs")
}

// ------------------------------------------------------------------ the Music app

@Composable
fun MusicApp(onSwitch: () -> Unit, onSignIn: () -> Unit) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(MTab.HOME) }
    val stack = remember { mutableStateListOf<MRoute>() }
    var menu by remember { mutableStateOf<Pair<MusicItem, String?>?>(null) }
    val queue by MusicQueue.tracks.collectAsState()
    val mode by PlayerHolder.mode.collectAsState()
    val playing = mode == PlayerHolder.Mode.MUSIC && queue.isNotEmpty()

    val a = remember {
        MActions(
            ctx,
            open = { i -> if (i.playable) playSong(ctx, i) else { MusicUi.nowPlaying = false; stack.add(MRoute.Page(i.id, i.title)) } },
            more = { i, album -> menu = i to album },
            playFrom = { list, i -> playList(ctx, list, i) },
        )
    }

    androidx.activity.compose.BackHandler(enabled = MusicUi.nowPlaying) { MusicUi.nowPlaying = false }
    androidx.activity.compose.BackHandler(enabled = !MusicUi.nowPlaying && stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    androidx.activity.compose.BackHandler(enabled = !MusicUi.nowPlaying && stack.isEmpty() && tab != MTab.HOME) { tab = MTab.HOME }

    Box(Modifier.fillMaxSize().background(MusicBg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                val search = { stack.add(MRoute.Search) }
                when (val top = stack.lastOrNull()) {
                    MRoute.Search -> MusicSearch(a) { stack.removeAt(stack.lastIndex) }
                    is MRoute.Page -> PageScreen(top, a) { stack.removeAt(stack.lastIndex) }
                    null -> when (tab) {
                        MTab.HOME -> HomeTab(a, onSwitch, search)
                        MTab.EXPLORE -> ExploreTab(a, onSwitch, search) { id, title, params -> stack.add(MRoute.Page(id, title, params)) }
                        MTab.LIBRARY -> LibraryTab(a, onSwitch, search, onSignIn) { id, t -> stack.add(MRoute.Page(id, t)) }
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
        if (MusicUi.nowPlaying && playing) Box(Modifier.fillMaxSize()) { NowPlaying(a, onVideo = onSwitch) }
    }
    menu?.let { (item, album) -> SongMenu(item, album, a) { menu = null } }
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

@Composable
private fun ChipBar(labels: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels) { label ->
            val on = label == selected
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (on) MusicBg else YtText,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) YtText else Color(0x1AFFFFFF))
                    .clickable { onSelect(if (on) null else label) }.padding(horizontal = 12.dp, vertical = 7.dp))
        }
    }
}

// ------------------------------------------------------------------ Home

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTab(a: MActions, onSwitch: () -> Unit, onSearch: () -> Unit) {
    var chip by rememberSaveable { mutableStateOf<String?>(null) }
    val base = rememberLoader("mhome") { f -> Music.home(f) }
    val chips = base.value?.chips.orEmpty()
    val params = chips.firstOrNull { it.label == chip }?.params
    val l = if (params == null) base else rememberLoader("mhome:$params") { f -> Music.home(f, params) }
    Column(Modifier.fillMaxSize()) {
        MusicTopBar(onSwitch, onSearch)
        if (chips.isNotEmpty()) ChipBar(chips.map { it.label }, chip) { chip = it }
        PullToRefreshBox(isRefreshing = l.refreshing, onRefresh = { l.refresh() }, modifier = Modifier.fillMaxSize()) {
            LoaderBox(l) { home ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(home.shelves.size) { i -> ShelfView(home.shelves[i], a) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Explore

@Composable
private fun ExploreTab(a: MActions, onSwitch: () -> Unit, onSearch: () -> Unit, openPage: (String, String, String?) -> Unit) {
    val shelves = rememberLoader("mexplore") { f -> Music.explore(f) }
    val moods = rememberLoader("mmoods") { f -> Music.moods(f) }
    Column(Modifier.fillMaxSize()) {
        MusicTopBar(onSwitch, onSearch)
        LoaderBox(shelves) { list ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(list.size) { i -> ShelfView(list[i], a) }
                moods.value?.forEach { g ->
                    item {
                        Text(g.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 10.dp))
                        // Two rows of coloured buttons that scroll sideways, like YT Music.
                        val cols = g.moods.chunked(2)
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(cols.size) { c ->
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    cols[c].forEach { m ->
                                        Row(Modifier.width(170.dp).height(48.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF292929))
                                            .clickable { openPage(m.browseId, m.label, m.params) }) {
                                            Box(Modifier.width(6.dp).fillMaxHeight().background(Color(m.color.toInt())))
                                            Text(m.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ shelves and rows

@Composable
private fun ShelfView(s: Shelf, a: MActions) {
    Column(Modifier.padding(top = 20.dp)) {
        if (s.title.isNotEmpty()) Text(s.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        if (s.list) {
            val cols = s.items.chunked(4)
            LazyRow(contentPadding = PaddingValues(end = 8.dp)) {
                items(cols.size) { c ->
                    Column(Modifier.width(330.dp)) {
                        cols[c].forEach { item -> SongRow(item, a) { if (item.playable) playSong(a.ctx, item) else a.open(item) } }
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
    Column(Modifier.width(if (video) 250.dp else 150.dp).clickable { a.open(item) }, horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (video) 16f / 9f else 1f).clip(if (round) CircleShape else RoundedCornerShape(6.dp)).background(YtChip)) {
            AsyncImage(item.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = if (round) TextAlign.Center else TextAlign.Start, modifier = Modifier.padding(top = 8.dp))
        item.subtitle?.let { Text(it, fontSize = 13.sp, color = YtSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = if (round) TextAlign.Center else TextAlign.Start) }
    }
}

@Composable
private fun SongRow(item: MusicItem, a: MActions, number: Int? = null, album: String? = null, onClick: () -> Unit) {
    val offline by Offline.tracks.collectAsState()
    val now by MusicQueue.tracks.collectAsState()
    val idx by MusicQueue.index.collectAsState()
    val isCurrent = item.playable && now.getOrNull(idx)?.videoId == item.id
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        if (number != null) {
            Text("$number", color = if (isCurrent) YtText else YtSecondary, fontSize = 15.sp, modifier = Modifier.width(30.dp))
        } else {
            val round = item.kind == MusicItem.Kind.ARTIST
            Box(Modifier.size(52.dp).clip(if (round) CircleShape else RoundedCornerShape(4.dp)).background(YtChip)) {
                AsyncImage(item.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (isCurrent) Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White) }
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(item.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.playable && offline.any { it.videoId == item.id }) Icon(Icons.Filled.DownloadDone, "Downloaded", tint = YtSecondary, modifier = Modifier.size(14.dp).padding(end = 3.dp))
                val sub = listOfNotNull(item.subtitle, item.duration).joinToString(" • ")
                if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = YtSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.playable) IconButton(onClick = { a.more(item, album) }) { Icon(Icons.Outlined.MoreVert, "More", modifier = Modifier.size(20.dp)) }
        else Spacer(Modifier.width(12.dp))
    }
}

// ------------------------------------------------------------------ album / playlist / artist / mood page

@Composable
private fun PageScreen(r: MRoute.Page, a: MActions, onBack: () -> Unit) {
    val l = rememberLoader("mpage:${r.browseId}:${r.params}") { f -> Music.page(r.browseId, r.params, f) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            if (r.params != null) Text(r.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        LoaderBox(l) { page ->
            if (r.params != null) LazyColumn(Modifier.fillMaxSize()) { items(page.shelves.size) { ShelfView(page.shelves[it], a) } }
            else PageContent(page, r.title, a)
        }
    }
}

@Composable
private fun PageContent(page: MusicPage, fallbackTitle: String, a: MActions) {
    val ctx = a.ctx
    val scope = rememberCoroutineScope()
    val artist = page.kind == MusicItem.Kind.ARTIST
    val album = page.kind == MusicItem.Kind.ALBUM
    val tracks = page.tracks
    var subscribed by remember(page) { mutableStateOf(page.subscribed) }
    var saved by remember(page) { mutableStateOf(false) }
    var showDesc by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.then(if (artist) Modifier.fillMaxWidth().aspectRatio(1.25f) else Modifier.padding(top = 4.dp).size(240.dp))
                    .clip(RoundedCornerShape(if (artist) 0.dp else 8.dp)).background(YtChip)) {
                    AsyncImage(page.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (artist) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to MusicBg)))
                }
                Text(page.title.ifEmpty { fallbackTitle }, fontSize = if (artist) 32.sp else 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
                page.subtitle?.let { Text(it, fontSize = 13.sp, color = YtSecondary, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp)) }
                page.description?.let { d ->
                    Text(d, fontSize = 13.sp, color = YtSecondary, maxLines = if (showDesc) 40 else 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).clickable { showDesc = !showDesc })
                }
                Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (artist && page.channelId != null) {
                        RoundAction(if (subscribed) Icons.Filled.CheckCircle else Icons.Outlined.Add, if (subscribed) "Subscribed" else "Subscribe") {
                            if (!Engine.signedIn) { toast(ctx, "Sign in from Library to subscribe"); return@RoundAction }
                            val target = !subscribed; subscribed = target
                            scope.launch { runCatching { Engine.subscribe(page.channelId, target) }.onFailure { subscribed = !target; toast(ctx, Engine.friendly(it)) } }
                        }
                    }
                    if (!artist && page.playlistId != null) {
                        RoundAction(if (saved) Icons.Outlined.LibraryAddCheck else Icons.Outlined.LibraryAdd, if (saved) "Saved" else "Save") {
                            if (!Engine.signedIn) { toast(ctx, "Sign in from Library to save"); return@RoundAction }
                            val target = !saved; saved = target
                            scope.launch { runCatching { Music.savePlaylist(page.playlistId, target) }.onFailure { saved = !target; toast(ctx, Engine.friendly(it)) } }
                        }
                    }
                    if (tracks.isNotEmpty()) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(YtText).clickable { a.playFrom(tracks, 0) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, "Play", tint = MusicBg, modifier = Modifier.size(38.dp))
                        }
                        RoundAction(Icons.Outlined.Shuffle, "Shuffle") { playList(ctx, tracks, 0, shuffle = true) }
                        if (!artist) RoundAction(Icons.Outlined.Download, "Download") { downloadAll(ctx, tracks, if (album) page.title else null) }
                    }
                }
            }
        }
        page.shelves.forEach { s ->
            if (s.list) {
                if (s.title.isNotEmpty()) item { Text(s.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)) }
                itemsIndexed(s.items) { i, it ->
                    SongRow(it, a, number = if (album) i + 1 else null, album = if (album) page.title else null) { if (it.playable) a.playFrom(s.items, i) else a.open(it) }
                }
            } else item { ShelfView(s, a) }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x1AFFFFFF)), contentAlignment = Alignment.Center) { Icon(icon, label, modifier = Modifier.size(22.dp)) }
        Text(label, fontSize = 11.sp, color = YtSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

// ------------------------------------------------------------------ Library

private val LIB_CHIPS = listOf("Playlists", "Songs", "Albums", "Artists", "Downloaded")

@Composable
private fun LibraryTab(a: MActions, onSwitch: () -> Unit, onSearch: () -> Unit, onSignIn: () -> Unit, openPage: (String, String) -> Unit) {
    var chip by rememberSaveable { mutableStateOf(if (Engine.signedIn) "Playlists" else "Downloaded") }
    var newPlaylist by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        MusicTopBar(onSwitch, onSearch)
        ChipBar(LIB_CHIPS, chip) { chip = it ?: chip }
        when {
            chip == "Downloaded" -> DownloadedList(a)
            !Engine.signedIn -> SignInPrompt("Your music library",
                "Sign in to see your playlists, liked songs, albums and artists. Downloaded songs work without signing in.", onSignIn)
            else -> {
                val id = when (chip) { "Songs" -> "FEmusic_liked_videos"; "Albums" -> "FEmusic_liked_albums"; "Artists" -> "FEmusic_library_corpus_track_artists"; else -> "FEmusic_liked_playlists" }
                val l = rememberLoader("mlib:$id") { f -> Music.page(id, null, f) }
                LoaderBox(l) { page ->
                    val items = page.shelves.flatMap { it.items }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                        if (chip == "Playlists") item {
                            LibEntry(Icons.Outlined.Add, "New playlist") { newPlaylist = true }
                            LibEntry(Icons.Outlined.ThumbUp, "Liked music") { openPage("VLLM", "Liked music") }
                            LibEntry(Icons.Outlined.History, "History") { openPage("FEmusic_history", "History") }
                        }
                        if (chip == "Songs" && items.isNotEmpty()) item { PlayShuffleRow(items) { s -> playList(a.ctx, items, 0, s) } }
                        itemsIndexed(items) { i, it -> SongRow(it, a) { if (it.playable) a.playFrom(items, i) else a.open(it) } }
                    }
                }
                if (newPlaylist) NewPlaylistSheet(null) { newPlaylist = false; l.refresh() }
            }
        }
    }
}

@Composable
private fun PlayShuffleRow(items: List<MusicItem>, play: (Boolean) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(YtText).clickable { play(false) }.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayArrow, null, tint = MusicBg, modifier = Modifier.size(20.dp)); Text("Play all", color = MusicBg, fontWeight = FontWeight.Medium)
        }
        Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0x1AFFFFFF)).clickable { play(true) }.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shuffle, null, modifier = Modifier.size(18.dp)); Text("  Shuffle", fontWeight = FontWeight.Medium)
        }
        Text("${items.count { it.playable }} songs", color = YtSecondary, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
    }
}

@Composable
private fun DownloadedList(a: MActions) {
    val offline by Offline.tracks.collectAsState()
    val active = Downloads.tasks.collectAsState().value.count { it.song != null && it.active }
    if (offline.isEmpty()) {
        Message(if (active > 0) "Downloading $active songs…" else "Songs you download appear here and play without internet.\nTap ⋮ on a song, or Download on an album or playlist.")
        return
    }
    val items = offline.map { MusicItem(MusicItem.Kind.SONG, it.videoId, it.title, listOfNotNull(it.artist, it.album).joinToString(" • ").ifEmpty { null }, it.thumb, it.duration) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            PlayShuffleRow(items) { s -> playTracks(a.ctx, offline.map { it.toTrack() }, 0, s) }
            if (active > 0) Text("Downloading $active more…", color = YtSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }
        itemsIndexed(items) { i, it -> SongRow(it, a) { playTracks(a.ctx, offline.map { o -> o.toTrack() }, i) } }
    }
}

@Composable
private fun LibEntry(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x1AFFFFFF)), contentAlignment = Alignment.Center) { Icon(icon, null) }
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
            Row(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x1AFFFFFF)).padding(horizontal = 14.dp),
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
            ChipBar(Music.Filter.entries.map { it.label }, filter.label) { lbl -> Music.Filter.entries.firstOrNull { it.label == lbl }?.let { filter = it } }
            val q = query
            val f = filter
            val l = rememberLoader("msearch:${f.name}:$q") { _ -> Music.search(q, f) }
            LoaderBox(l) { results ->
                if (results.isEmpty()) Message("No results for \"$q\"")
                else LazyColumn(Modifier.fillMaxSize()) { items(results) { r -> SongRow(r, a) { a.open(r) } } }
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
    Column(Modifier.fillMaxWidth().background(SheetBg)) {
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

/** Dark colour from the album art, for the now-playing background (as YT Music does). */
@Composable
private fun artColor(url: String?): Color {
    val ctx = LocalContext.current
    var c by remember { mutableStateOf(Color(0xFF2A2A2A)) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        runCatching {
            val res = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).size(96).build())
            val bmp = (res.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching
            val p = Palette.from(bmp).generate()
            val sw = p.darkVibrantSwatch ?: p.darkMutedSwatch ?: p.dominantSwatch ?: return@runCatching
            c = Color(sw.rgb).let { Color(it.red * 0.75f, it.green * 0.75f, it.blue * 0.75f) }
        }
    }
    return animateColorAsState(c, tween(600), label = "art").value
}

private enum class NpTab { NONE, QUEUE, LYRICS, RELATED }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlaying(a: MActions, onVideo: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { PlayerHolder.get(ctx) }
    val tracks by MusicQueue.tracks.collectAsState()
    val idx by MusicQueue.index.collectAsState()
    val offline by Offline.tracks.collectAsState()
    val t = tracks.getOrNull(idx) ?: return
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var shuffle by remember { mutableStateOf(player.shuffleModeEnabled) }
    var repeat by remember { mutableIntStateOf(player.repeatMode) }
    var tab by remember { mutableStateOf(NpTab.NONE) }
    var liked by remember(t.videoId) { mutableStateOf(false) }
    var addTo by remember { mutableStateOf(false) }
    LaunchedEffect(t.videoId) { runCatching { liked = Music.trackMeta(t.videoId).liked } }
    LaunchedEffect(Unit) {
        while (true) {
            pos = player.currentPosition; dur = player.duration.coerceAtLeast(0L)
            playing = player.playWhenReady; buffering = player.playbackState == Player.STATE_BUFFERING
            delay(250)
        }
    }
    val bg = artColor(t.thumb)
    val item = MusicItem(MusicItem.Kind.SONG, t.videoId, t.title, t.artist, t.thumb, t.duration)

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, MusicBg)))
            .statusBarsPadding().navigationBarsPadding().clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { MusicUi.nowPlaying = false }) { Icon(Icons.Outlined.KeyboardArrowDown, "Close") }
            Spacer(Modifier.weight(1f))
            Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0x33000000)).padding(3.dp)) {
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
            IconButton(onClick = { a.more(item, null) }) { Icon(Icons.Outlined.MoreVert, "More") }
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(YtChip)) {
                AsyncImage(Music.big(t.thumb, 1080), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(horizontal = 28.dp)) {
            Text(t.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
            Text(t.artist.orEmpty(), fontSize = 16.sp, color = Color(0xCCFFFFFF), maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NpPill(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, if (liked) "Liked" else "Like") {
                    if (!Engine.signedIn) { toast(ctx, "Sign in from Library to like songs"); return@NpPill }
                    val target = !liked; liked = target
                    scope.launch { runCatching { Music.likeSong(t.videoId, target) }.onFailure { liked = !target; toast(ctx, Engine.friendly(it)) } }
                }
                NpPill(Icons.AutoMirrored.Outlined.PlaylistAdd, "Save") { if (Engine.signedIn) addTo = true else toast(ctx, "Sign in from Library to save to playlists") }
                val isOff = offline.any { it.videoId == t.videoId }
                NpPill(if (isOff) Icons.Filled.DownloadDone else Icons.Outlined.Download, if (isOff) "Downloaded" else "Download") {
                    if (!isOff) { DownloadService.enqueueSong(ctx, t, null); toast(ctx, "Downloading") }
                }
                NpPill(Icons.Outlined.Share, "Share") { share(ctx, "https://music.youtube.com/watch?v=${t.videoId}") }
            }
            Spacer(Modifier.height(10.dp))
            Slider(
                value = drag ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { player.seekTo((it * dur).toLong()) }; drag = null },
                colors = SliderDefaults.colors(thumbColor = YtText, activeTrackColor = YtText, inactiveTrackColor = Color(0x33FFFFFF)),
            )
            Row {
                Text(((drag?.let { (it * dur).toLong() } ?: pos) / 1000).asDuration(), fontSize = 12.sp, color = Color(0xB3FFFFFF))
                Spacer(Modifier.weight(1f))
                Text(if (dur > 0) (dur / 1000).asDuration() else t.duration.orEmpty(), fontSize = 12.sp, color = Color(0xB3FFFFFF))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(NpTab.QUEUE to "UP NEXT", NpTab.LYRICS to "LYRICS", NpTab.RELATED to "RELATED").forEach { (tb, label) ->
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { tab = tb }.padding(horizontal = 14.dp, vertical = 14.dp))
            }
        }
    }
    if (tab != NpTab.NONE) NpSheet(tab, t, a, onTab = { tab = it }) { tab = NpTab.NONE }
    if (addTo) AddToPlaylistSheet(t.videoId) { addTo = false }
}

@Composable
private fun NpPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0x26FFFFFF)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 5.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NpSheet(tab: NpTab, t: Track, a: MActions, onTab: (NpTab) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF181818), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().fillMaxHeight(0.88f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(NpTab.QUEUE to "UP NEXT", NpTab.LYRICS to "LYRICS", NpTab.RELATED to "RELATED").forEach { (tb, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onTab(tb) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = if (tb == tab) YtText else YtSecondary)
                        Box(Modifier.padding(top = 6.dp).width(48.dp).height(2.dp).background(if (tb == tab) YtText else Color.Transparent))
                    }
                }
            }
            HorizontalDivider(color = YtDivider)
            when (tab) {
                NpTab.QUEUE -> QueueList()
                NpTab.LYRICS -> LyricsView(t)
                NpTab.RELATED -> RelatedView(t, a)
                NpTab.NONE -> {}
            }
        }
    }
}

// ------------------------------------------------------------------ Up next (drag to reorder)

@Composable
private fun QueueList() {
    val ctx = LocalContext.current
    val tracks by MusicQueue.tracks.collectAsState()
    val idx by MusicQueue.index.collectAsState()
    val rowPx = with(LocalDensity.current) { 64.dp.toPx() }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { state.scrollToItem((idx - 1).coerceAtLeast(0)) }
    LazyColumn(Modifier.fillMaxSize(), state = state) {
        itemsIndexed(tracks, key = { i, tr -> "$i-${tr.videoId}" }) { i, tr ->
            val isDragged = i == dragging
            Row(Modifier.fillMaxWidth().height(64.dp)
                .offset { IntOffset(0, if (isDragged) dragOffset.toInt() else 0) }
                .background(when { isDragged -> Color(0xFF2E2E2E); i == idx -> Color(0x1AFFFFFF); else -> Color.Transparent })
                .clickable { MusicQueue.jump(ctx, i) }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(YtChip)) {
                    AsyncImage(tr.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (i == idx) Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White) }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(tr.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (i == idx) FontWeight.Bold else FontWeight.Normal)
                    Text(listOfNotNull(tr.artist, tr.duration).joinToString(" • "), fontSize = 13.sp, color = YtSecondary, maxLines = 1)
                }
                if (i != idx) IconButton(onClick = { MusicQueue.remove(ctx, i) }) { Icon(Icons.Outlined.Close, "Remove", modifier = Modifier.size(18.dp), tint = YtSecondary) }
                // Drag handle: hold and drag up or down to move the song.
                Box(Modifier.size(48.dp).pointerInput(i, tracks.size) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = i; dragOffset = 0f },
                        onDragEnd = { dragging = -1; dragOffset = 0f },
                        onDragCancel = { dragging = -1; dragOffset = 0f },
                    ) { change, dy ->
                        change.consume()
                        dragOffset += dy
                        val cur = dragging
                        if (cur >= 0) {
                            if (dragOffset > rowPx * 0.6f && cur < tracks.size - 1) { MusicQueue.move(ctx, cur, cur + 1); dragging = cur + 1; dragOffset -= rowPx }
                            else if (dragOffset < -rowPx * 0.6f && cur > 0) { MusicQueue.move(ctx, cur, cur - 1); dragging = cur - 1; dragOffset += rowPx }
                        }
                    }
                }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.DragHandle, "Reorder", tint = YtSecondary) }
            }
        }
    }
}

// ------------------------------------------------------------------ Lyrics

@Composable
private fun LyricsView(t: Track) {
    val ctx = LocalContext.current
    val player = remember { PlayerHolder.get(ctx) }
    var synced by remember(t.videoId) { mutableStateOf<List<LyricLine>?>(null) }
    var plain by remember(t.videoId) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(t.videoId) { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(t.videoId) {
        loading = true
        val durSec = player.duration.takeIf { it > 0 }?.div(1000) ?: t.duration?.split(":")?.fold(0L) { acc, p -> acc * 60 + (p.toLongOrNull() ?: 0) }
        val meta = runCatching { Music.trackMeta(t.videoId) }.getOrNull()
        plain = meta?.lyricsId?.let { runCatching { Music.lyrics(it) }.getOrNull() }
        synced = runCatching { SyncedLyrics.find(t.title, t.artist, durSec) }.getOrNull()
        loading = false
    }
    LaunchedEffect(Unit) { while (true) { pos = player.currentPosition; delay(200) } }

    val lines = synced
    val text = plain
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = YtText) }
        lines != null -> {
            val state = rememberLazyListState()
            val current = lines.indexOfLast { it.ms <= pos + 300 }.coerceAtLeast(0)
            LaunchedEffect(current) { runCatching { state.animateScrollToItem((current - 3).coerceAtLeast(0)) } }
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
                itemsIndexed(lines) { i, line ->
                    Text(line.text.ifEmpty { "♪" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp,
                        color = when { i == current -> YtText; i < current -> Color(0x80FFFFFF); else -> Color(0x59FFFFFF) },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { player.seekTo(line.ms) }.padding(vertical = 8.dp))
                }
                item { Text("Synced lyrics from LRCLIB", fontSize = 12.sp, color = YtSecondary, modifier = Modifier.padding(top = 20.dp)) }
            }
        }
        text != null -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
            item {
                Text(text.text, fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
                text.source?.let { Text(it, fontSize = 12.sp, color = YtSecondary, modifier = Modifier.padding(top = 20.dp)) }
            }
        }
        else -> Message("Lyrics aren't available for this song.")
    }
}

// ------------------------------------------------------------------ Related

@Composable
private fun RelatedView(t: Track, a: MActions) {
    val l = rememberLoader("related:${t.videoId}") { _ ->
        val id = Music.trackMeta(t.videoId).relatedId
        if (id == null) emptyList() else Music.related(id)
    }
    LoaderBox(l) { shelves ->
        if (shelves.isEmpty()) Message("Nothing related found.")
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { items(shelves.size) { ShelfView(shelves[it], a) } }
    }
}

// ------------------------------------------------------------------ song menu, playlists, sleep timer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongMenu(item: MusicItem, album: String?, a: MActions, onDismiss: () -> Unit) {
    val ctx = a.ctx
    val scope = rememberCoroutineScope()
    val offline by Offline.tracks.collectAsState()
    var addTo by remember { mutableStateOf(false) }
    var sleep by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(item.id) { if (Engine.signedIn) runCatching { liked = Music.trackMeta(item.id).liked } }
    if (addTo) { AddToPlaylistSheet(item.id, onDismiss); return }
    if (sleep) { SleepSheet(onDismiss); return }
    val isOff = offline.any { it.videoId == item.id }
    val isCurrent = MusicQueue.current?.videoId == item.id
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetBg) {
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
            MenuItem(Icons.AutoMirrored.Outlined.QueueMusic, "Add to queue") { Watch.detach(); MusicQueue.addToQueue(ctx, item.toTrack()); toast(ctx, "Added to queue"); onDismiss() }
            if (Engine.signedIn) {
                MenuItem(if (liked == true) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, if (liked == true) "Remove from liked songs" else "Add to liked songs") {
                    val target = liked != true
                    scope.launch { runCatching { Music.likeSong(item.id, target) }.onSuccess { toast(ctx, if (target) "Added to liked songs" else "Removed from liked songs") }.onFailure { toast(ctx, Engine.friendly(it)) } }
                    onDismiss()
                }
                MenuItem(Icons.AutoMirrored.Outlined.PlaylistAdd, "Save to playlist") { addTo = true }
            }
            if (isOff) MenuItem(Icons.Outlined.Delete, "Remove download") { Offline.remove(item.id); toast(ctx, "Download removed"); onDismiss() }
            else {
                MenuItem(Icons.Outlined.Download, "Download") { DownloadService.enqueueSong(ctx, item.toTrack(), album); toast(ctx, "Downloading"); onDismiss() }
                MenuItem(Icons.Outlined.Download, "Download as MP3") { DownloadService.enqueueSong(ctx, item.toTrack(), album, mp3 = true); toast(ctx, "Downloading"); onDismiss() }
            }
            item.artistId?.let { id -> MenuItem(Icons.Outlined.Person, "Go to artist") { MusicUi.nowPlaying = false; a.open(MusicItem(MusicItem.Kind.ARTIST, id, item.artist ?: "", null, null)); onDismiss() } }
            if (isCurrent) MenuItem(Icons.Outlined.Bedtime, "Sleep timer") { sleep = true }
            MenuItem(Icons.Outlined.Share, "Share") { share(ctx, "https://music.youtube.com/watch?v=${item.id}"); onDismiss() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(videoId: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = rememberLoader("myplaylists") { _ -> Music.myPlaylists() }
    var creating by remember { mutableStateOf(false) }
    if (creating) { NewPlaylistSheet(videoId, onDismiss); return }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetBg) {
        Column(Modifier.navigationBarsPadding().fillMaxHeight(0.7f)) {
            Text("Save to playlist", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            LibEntry(Icons.Outlined.Add, "New playlist") { creating = true }
            LoaderBox(l) { lists ->
                LazyColumn(Modifier.fillMaxSize()) {
                    items(lists) { p ->
                        Row(Modifier.fillMaxWidth().clickable {
                            scope.launch {
                                runCatching { Music.addToPlaylist(p.id, videoId) }
                                    .onSuccess { toast(ctx, "Saved to ${p.title}") }.onFailure { toast(ctx, Engine.friendly(it)) }
                            }
                            onDismiss()
                        }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)).background(YtChip)) {
                                AsyncImage(p.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(p.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                p.subtitle?.let { Text(it, fontSize = 12.sp, color = YtSecondary, maxLines = 1) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewPlaylistSheet(videoId: String?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDone, containerColor = SheetBg) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("New playlist", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x1AFFFFFF)).padding(14.dp)) {
                BasicTextField(name, { name = it }, singleLine = true, textStyle = TextStyle(color = YtText, fontSize = 16.sp), cursorBrush = SolidColor(YtText),
                    modifier = Modifier.fillMaxWidth(), decorationBox = { inner -> if (name.isEmpty()) Text("Playlist name", color = YtSecondary, fontSize = 16.sp); inner() })
            }
            Text("Private · only you can see it", fontSize = 12.sp, color = YtSecondary)
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.End) {
                Text("Create", color = if (name.isBlank()) YtSecondary else MusicBg, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (name.isBlank()) Color(0x1AFFFFFF) else YtText)
                        .clickable(enabled = name.isNotBlank()) {
                            val title = name.trim()
                            scope.launch {
                                runCatching { Music.createPlaylist(title, videoId) }
                                    .onSuccess { toast(ctx, "Created \"$title\"") }.onFailure { toast(ctx, Engine.friendly(it)) }
                                onDone()
                            }
                        }.padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val at by MusicQueue.sleepAt.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetBg) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text("Sleep timer", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            val status = when (val s = at) { null -> "Off"; -1L -> "Stops at the end of this song"; else -> "Stops in ${((s - System.currentTimeMillis()) / 60_000 + 1)} min" }
            Text(status, fontSize = 13.sp, color = YtSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            HorizontalDivider(color = YtDivider, modifier = Modifier.padding(vertical = 8.dp))
            listOf(5, 10, 15, 30, 45, 60).forEach { m -> MenuItem(Icons.Outlined.Bedtime, "$m minutes") { MusicQueue.sleep(ctx, m); toast(ctx, "Music stops in $m minutes"); onDismiss() } }
            MenuItem(Icons.Outlined.Bedtime, "End of song") { MusicQueue.sleep(ctx, -1); toast(ctx, "Music stops after this song"); onDismiss() }
            if (at != null) MenuItem(Icons.Outlined.Close, "Turn off timer") { MusicQueue.sleep(ctx, null); onDismiss() }
        }
    }
}
