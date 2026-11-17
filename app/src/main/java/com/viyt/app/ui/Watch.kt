package com.viyt.app.ui

import android.content.Context
import android.graphics.Color as AColor
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionDisabled
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.viyt.app.core.Caption
import com.viyt.app.core.Comment
import com.viyt.app.core.Engine
import com.viyt.app.core.InnerTube
import com.viyt.app.core.Item
import com.viyt.app.core.WatchInfo
import com.viyt.app.core.asDuration
import com.viyt.app.player.PlayerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The video that's open (full watch page or mini player). */
object Watch {
    var item by mutableStateOf<Item?>(null)
    var expanded by mutableStateOf(true)
    var fullscreen by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var firstFrame by mutableStateOf(false)
    var info by mutableStateOf<WatchInfo?>(null)
    private var job: Job? = null

    fun open(ctx: Context, scope: CoroutineScope, it: Item) {
        Engine.prefetch(it.url)
        expanded = true
        if (item?.url == it.url && PlayerHolder.currentUrl.value == it.url) return
        item = it
        error = null
        info = null
        firstFrame = false
        loading = true
        val id = InnerTube.videoId(it.url)
        job?.cancel()
        job = scope.launch {
            // Watch-page details and the stream load in parallel.
            if (id != null) launch {
                runCatching { Engine.watch(id) }.onSuccess { w ->
                    if (item?.url == it.url) {
                        info = w
                        if (item?.title.isNullOrEmpty()) item = it.copy(title = w.title, channel = w.channel)
                    }
                }
            }
            try {
                val s = Engine.resolve(it.url)
                if (item?.url == it.url) PlayerHolder.load(ctx, it.url, s)
            } catch (t: Throwable) {
                error = Engine.friendly(t)
            } finally {
                loading = false
            }
        }
    }

    fun retry(ctx: Context, scope: CoroutineScope) {
        val it = item ?: return
        Engine.forgetStream(it.url)
        PlayerHolder.stop()
        item = null
        open(ctx, scope, it)
    }

    fun changeQuality(ctx: Context, scope: CoroutineScope, h: Int) {
        val it = item ?: return
        val p = PlayerHolder.get(ctx)
        val pos = p.currentPosition
        Engine.prefs.streamHeight = h
        loading = true
        scope.launch {
            try { PlayerHolder.load(ctx, it.url, Engine.resolve(it.url, h), pos, p.playWhenReady) }
            catch (t: Throwable) { Toast.makeText(ctx, Engine.friendly(t), Toast.LENGTH_LONG).show() }
            finally { loading = false }
        }
    }

    fun close() {
        job?.cancel()
        item = null
        info = null
        fullscreen = false
        PlayerHolder.stop()
    }
}

// ------------------------------------------------------------------ the video surface

@Composable
fun VideoSurface(player: Player) {
    val zoom by Engine.prefs.zoomState
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setShutterBackgroundColor(AColor.BLACK)
                setKeepContentOnPlayerReset(false)
                subtitleView?.apply {
                    setStyle(CaptionStyleCompat(AColor.WHITE, 0xBF080808.toInt(), AColor.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_NONE, AColor.WHITE, null))
                    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setBottomPaddingFraction(0.08f)
                }
            }
        },
        update = { it.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
        modifier = Modifier.fillMaxSize(),
    )
}

// ------------------------------------------------------------------ player with YouTube-style controls

@Composable
fun PlayerBox(
    player: Player,
    surface: @Composable () -> Unit,
    modifier: Modifier,
    onCollapse: () -> Unit,
    onFullscreen: (Boolean) -> Unit,
    onNext: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stream by PlayerHolder.current.collectAsState()
    val caption by PlayerHolder.caption.collectAsState()
    var controls by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var buf by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var seekFlash by remember { mutableIntStateOf(0) } // -n rewind, +n forward
    var dragDown by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            pos = player.currentPosition
            dur = player.duration.coerceAtLeast(0L)
            buf = player.bufferedPosition
            playing = player.isPlaying
            buffering = player.playbackState == Player.STATE_BUFFERING
            ended = player.playbackState == Player.STATE_ENDED
            delay(200)
        }
    }
    LaunchedEffect(controls, playing) { if (controls && playing) { delay(3000); controls = false } }
    LaunchedEffect(seekFlash) { if (seekFlash != 0) { delay(700); seekFlash = 0 } }

    val full = Watch.fullscreen
    Box(
        modifier.background(Color.Black)
            .pointerInput(full) {
                detectTapGestures(
                    onTap = { controls = !controls },
                    onDoubleTap = { o ->
                        if (o.x < size.width / 2) { player.seekBack(); seekFlash = if (seekFlash < 0) seekFlash - 1 else -1 }
                        else { player.seekForward(); seekFlash = if (seekFlash > 0) seekFlash + 1 else 1 }
                    },
                )
            }
            .pointerInput(full) {
                // Swipe down: minimise (or leave full screen). Swipe up: full screen.
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragDown > 120f) { if (full) onFullscreen(false) else onCollapse() }
                        else if (dragDown < -120f && !full) onFullscreen(true)
                        dragDown = 0f
                    },
                    onDragCancel = { dragDown = 0f },
                ) { _, dy -> dragDown += dy }
            }
    ) {
        surface()
        // Thumbnail until the first frame shows, so the page never looks empty.
        val item = Watch.item
        if (!Watch.firstFrame && item?.thumbnail != null) {
            AsyncImage(item.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (Watch.loading || buffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(44.dp), color = Color.White, strokeWidth = 3.dp)
        }
        Watch.error?.let { err ->
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(err, color = Color.White, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("Tap to retry", color = YtSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)
                    .clickable { Watch.retry(context, scope) })
            }
        }
        if (seekFlash != 0) SeekFlash(seekFlash)

        androidx.compose.animation.AnimatedVisibility(visible = controls && Watch.error == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x80000000))) {
                // Top bar
                Row(Modifier.fillMaxWidth().align(Alignment.TopStart).padding(horizontal = 4.dp, vertical = if (full) 12.dp else 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (full) onFullscreen(false) else onCollapse() }) {
                        Icon(Icons.Outlined.KeyboardArrowDown, "Minimise", tint = Color.White)
                    }
                    Text(if (full) item?.title.orEmpty() else "", color = Color.White, fontSize = 16.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    AutoplayToggle()
                    if (stream?.captions?.isNotEmpty() == true) {
                        IconButton(onClick = {
                            val s = stream!!
                            if (caption != null) PlayerHolder.selectCaption(context, null)
                            else PlayerHolder.selectCaption(context, s.captions.firstOrNull { !it.auto } ?: s.captions.first())
                        }) { Icon(if (caption != null) Icons.Outlined.ClosedCaption else Icons.Outlined.ClosedCaptionDisabled, "Captions", tint = Color.White) }
                    }
                    IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Settings, "Settings", tint = Color.White) }
                }
                // Centre controls
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (full) 64.dp else 40.dp)) {
                    CenterButton(Icons.Filled.SkipPrevious, "Previous", 36.dp, enabled = true) { player.seekTo(0) }
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0x55000000)).clickable {
                        when { ended -> { player.seekTo(0); player.play() }; player.isPlaying -> player.pause(); else -> player.play() }
                    }, contentAlignment = Alignment.Center) {
                        Icon(when { ended -> Icons.Filled.Replay; playing -> Icons.Filled.Pause; else -> Icons.Filled.PlayArrow },
                            "Play or pause", tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                    CenterButton(Icons.Filled.SkipNext, "Next", 36.dp, enabled = onNext != null) { onNext?.invoke() }
                }
                // Bottom: time and full screen
                Row(Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(start = 14.dp, end = 4.dp, bottom = if (full) 34.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text((pos / 1000).asDuration(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(" / ${(dur / 1000).asDuration()}", color = Color(0xFFCCCCCC), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onFullscreen(!full) }) {
                        Icon(if (full) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, "Full screen", tint = Color.White)
                    }
                }
            }
        }
        // Red progress bar along the bottom edge (scrubber shows with the controls).
        SeekBar(
            pos, dur, buf, showThumb = controls,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(horizontal = if (full) 16.dp else 0.dp).padding(bottom = if (full) 20.dp else 0.dp),
            onSeek = { player.seekTo(it); controls = true },
        )
    }
    if (settings) PlaybackSheet(onDismiss = { settings = false })
}

@Composable
private fun CenterButton(icon: ImageVector, desc: String, size: androidx.compose.ui.unit.Dp, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(52.dp)) {
        Icon(icon, desc, tint = if (enabled) Color.White else Color(0x66FFFFFF), modifier = Modifier.size(size))
    }
}

@Composable
private fun AutoplayToggle() {
    var on by remember { mutableStateOf(Engine.prefs.autoplay) }
    Switch(
        checked = on, onCheckedChange = { on = it; Engine.prefs.autoplay = it },
        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFDDDDDD), checkedThumbColor = YtBg, uncheckedTrackColor = Color(0x66FFFFFF),
            uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent),
        thumbContent = { Icon(if (on) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, modifier = Modifier.size(12.dp)) },
        modifier = Modifier.padding(end = 4.dp).height(24.dp),
    )
}

@Composable
private fun BoxScope.SeekFlash(n: Int) {
    val left = n < 0
    Box(Modifier.fillMaxHeight().fillMaxWidth(0.4f).align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
        .clip(if (left) RoundedCornerShape(topEnd = 400.dp, bottomEnd = 400.dp) else RoundedCornerShape(topStart = 400.dp, bottomStart = 400.dp))
        .background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (left) Icons.Outlined.FastRewind else Icons.Outlined.FastForward, null, tint = Color.White)
            Text("${kotlin.math.abs(n) * 10} seconds", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
fun SeekBar(pos: Long, dur: Long, buf: Long, showThumb: Boolean, modifier: Modifier, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val frac = dragging ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val bufFrac = if (dur > 0) (buf.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Box(
        modifier.height(if (showThumb) 24.dp else 3.dp)
            .pointerInput(dur) {
                detectTapGestures { o -> if (dur > 0) onSeek((o.x / size.width * dur).toLong()) }
            }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging?.let { if (dur > 0) onSeek((it * dur).toLong()) }; dragging = null },
                    onDragCancel = { dragging = null },
                ) { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(if (showThumb) 24.dp else 3.dp)) {
            val h = 3.dp.toPx()
            val y = if (showThumb) size.height / 2 - h / 2 else size.height - h
            drawRect(Color(0x4DFFFFFF), Offset(0f, y), Size(size.width, h))
            drawRect(Color(0x80FFFFFF), Offset(0f, y), Size(size.width * bufFrac, h))
            drawRect(YtRed, Offset(0f, y), Size(size.width * frac, h))
            if (showThumb) drawCircle(YtRed, radius = if (dragging != null) 9.dp.toPx() else 6.5.dp.toPx(), center = Offset(size.width * frac, y + h / 2))
        }
    }
}

// ------------------------------------------------------------------ watch page

@Composable
fun WatchPage(
    player: Player,
    surface: @Composable () -> Unit,
    a: Actions,
    onCollapse: () -> Unit,
    onFullscreen: (Boolean) -> Unit,
    onPip: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = Watch.info
    val item = Watch.item ?: return
    val next = info?.related?.firstOrNull { it.kind == Item.Kind.VIDEO && !it.short }
    var description by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf(false) }
    val audioOnly by PlayerHolder.audioOnly.collectAsState()

    Column(Modifier.fillMaxSize().background(YtBg)) {
        PlayerBox(player, surface, Modifier.fillMaxWidth().aspectRatio(16f / 9f), onCollapse, onFullscreen,
            onNext = next?.let { n -> { Watch.open(context, scope, n) } })
        val list = rememberLazyListState()
        LaunchedEffect(item.url) { list.scrollToItem(0) }
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth().clickable { description = true }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(info?.title?.ifEmpty { null } ?: item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 6.dp)) {
                        Text(listOfNotNull(info?.views, info?.date).joinToString("  ").ifEmpty { item.meta ?: "" }, fontSize = 12.sp, color = YtSecondary)
                        Text("  ...more", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            item(key = "channel") { ChannelBar(item, info) }
            item(key = "actions") {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { LikePill(info) }
                    item { Pill(Icons.Outlined.Share, "Share") { share(context, item.url) } }
                    item { Pill(Icons.Outlined.Download, "Download") { download = true } }
                    item { Pill(Icons.Outlined.Headphones, if (audioOnly) "Audio only · on" else "Audio only", audioOnly) { PlayerHolder.applyAudioOnly(context, !audioOnly) } }
                    item { Pill(Icons.Outlined.PictureInPictureAlt, "Mini player") { onPip() } }
                }
            }
            item(key = "comments") {
                if (info != null && (info.commentsToken != null || info.commentCount != null)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(12.dp)).background(YtRaised)
                        .clickable(enabled = info.commentsToken != null) { comments = true }.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Comments", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("  ${info.commentCount.orEmpty()}", color = YtSecondary, fontSize = 13.sp)
                        }
                        info.commentTeaser?.let { t ->
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(t.avatar, 24.dp)
                                Text(t.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                    }
                }
            }
            val related = info?.related.orEmpty().let { r -> if (Engine.prefs.hideShorts) r.filter { !it.short } else r }
            if (info == null) item(key = "rel-skel") { CardSkeletons(big = true) }
            items(related, key = { "rel" + it.kind.name + it.id }) { r ->
                ItemView(r, Actions(open = { i -> if (i.kind == Item.Kind.VIDEO) Watch.open(context, scope, i) else a.open(i) }, more = a.more), big = true)
            }
        }
    }
    if (description && info != null) DescriptionSheet(info) { description = false }
    val token = info?.commentsToken
    if (comments && token != null) CommentsSheet(token, info?.commentCount) { comments = false }
    if (download) DownloadSheet(item.title, item.url) { download = false }
}

@Composable
private fun ChannelBar(item: Item, info: WatchInfo?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var subscribed by remember(info?.videoId) { mutableStateOf(info?.subscribed ?: false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(info?.avatar ?: item.avatar, 36.dp)
        Text(info?.channel ?: item.channel.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false))
        info?.subscribers?.let { Text("  ${it.substringBefore(" ")}", color = YtSecondary, fontSize = 12.sp, maxLines = 1) }
        Spacer(Modifier.weight(1f))
        val channelId = info?.channelId
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(if (subscribed) YtChip else YtText).clickable(enabled = channelId != null) {
                if (!Engine.signedIn) { Toast.makeText(context, "Sign in from the You tab to subscribe", Toast.LENGTH_SHORT).show(); return@clickable }
                val target = !subscribed
                subscribed = target
                scope.launch {
                    runCatching { Engine.subscribe(channelId!!, target) }.onFailure {
                        subscribed = !target
                        Toast.makeText(context, Engine.friendly(it), Toast.LENGTH_SHORT).show()
                    }
                }
            }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (subscribed) Icon(Icons.Outlined.NotificationsNone, null, modifier = Modifier.size(18.dp).padding(end = 2.dp))
            Text(if (subscribed) "Subscribed" else "Subscribe", color = if (subscribed) YtText else YtBg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LikePill(info: WatchInfo?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var liked by remember(info?.videoId) { mutableStateOf(info?.liked ?: false) }
    Row(Modifier.clip(RoundedCornerShape(50)).background(YtChip), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clickable {
            val id = info?.videoId ?: return@clickable
            if (!Engine.signedIn) { Toast.makeText(context, "Sign in from the You tab to like videos", Toast.LENGTH_SHORT).show(); return@clickable }
            val target = !liked
            liked = target
            scope.launch { runCatching { Engine.like(id, target) }.onFailure { liked = !target; Toast.makeText(context, Engine.friendly(it), Toast.LENGTH_SHORT).show() } }
        }.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Like", modifier = Modifier.size(18.dp))
            Text(info?.likes ?: "Like", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        }
        Box(Modifier.width(1.dp).height(20.dp).background(Color(0x33FFFFFF)))
        Box(Modifier.clickable { }.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)) {
            Icon(Icons.Outlined.ThumbDown, "Dislike", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(if (selected) YtChipPressed else YtChip).clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
    }
}

// ------------------------------------------------------------------ mini player

@Composable
fun MiniPlayer(player: Player, surface: @Composable () -> Unit, onExpand: () -> Unit, onClose: () -> Unit) {
    val item = Watch.item ?: return
    var playing by remember { mutableStateOf(player.isPlaying) }
    var frac by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            playing = player.isPlaying || player.playWhenReady && player.playbackState == Player.STATE_BUFFERING
            val d = player.duration
            frac = if (d > 0) player.currentPosition.toFloat() / d else 0f
            delay(400)
        }
    }
    Column(Modifier.fillMaxWidth().background(YtRaised)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).background(YtRed))
        }
        Row(Modifier.fillMaxWidth().height(62.dp).clickable(onClick = onExpand), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(110.dp).fillMaxHeight().background(Color.Black)) {
                surface()
                if (!Watch.firstFrame && item.thumbnail != null) AsyncImage(item.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Watch.info?.channel ?: item.channel.orEmpty(), fontSize = 12.sp, color = YtSecondary, maxLines = 1)
            }
            IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play or pause")
            }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") }
        }
    }
}

// ------------------------------------------------------------------ sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DescriptionSheet(info: WatchInfo, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = YtRaised, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Description", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = YtDivider)
            Text(info.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(info.likes ?: "–", "Likes")
                Stat(info.views?.substringBefore(" ") ?: "–", "Views")
                Stat(info.date ?: "–", "Published")
            }
            info.description?.let { SelectionContainer { Text(it, fontSize = 14.sp, lineHeight = 20.sp) } }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, fontSize = 12.sp, color = YtSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(token: String, count: String?, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var next by remember { mutableStateOf<String?>(token) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val state = rememberLazyListState()
    suspend fun more() {
        val t = next ?: return
        if (loading) return
        loading = true
        try { val (c, n) = Engine.comments(t); list = list + c; next = n } catch (e: Throwable) { error = Engine.friendly(e) }
        loading = false
    }
    LaunchedEffect(Unit) { more() }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (last >= list.size - 4) more()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = YtRaised, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().fillMaxHeight(0.92f)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Comments", style = MaterialTheme.typography.titleMedium)
                Text("  ${count.orEmpty()}", color = YtSecondary)
            }
            HorizontalDivider(Modifier.padding(top = 12.dp), color = YtDivider)
            LazyColumn(Modifier.fillMaxSize(), state = state) {
                items(list.size) { i -> CommentRow(list[i]) }
                if (loading || (list.isEmpty() && error == null)) item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = YtText, modifier = Modifier.size(28.dp)) }
                }
                error?.let { e -> item { Text(e, color = YtSecondary, modifier = Modifier.padding(16.dp)) } }
            }
        }
    }
}

@Composable
private fun CommentRow(c: Comment) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Avatar(c.avatar, 32.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(listOfNotNull(c.author, c.time).joinToString(" · "), fontSize = 12.sp, color = YtSecondary)
            Text(c.text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ThumbUp, null, modifier = Modifier.size(15.dp), tint = YtSecondary)
                Text(" ${c.likes.orEmpty()}", fontSize = 12.sp, color = YtSecondary)
                Spacer(Modifier.width(20.dp))
                Icon(Icons.Outlined.ThumbDown, null, modifier = Modifier.size(15.dp), tint = YtSecondary)
                c.replies?.takeIf { it != "0" }?.let { Text("     $it replies", fontSize = 12.sp, color = Color(0xFF3EA6FF), fontWeight = FontWeight.Medium) }
            }
        }
    }
}

private enum class SettingsPage { MAIN, QUALITY, CAPTIONS, SPEED }

/** YouTube's player "Settings" sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = PlayerHolder.get(context)
    val stream by PlayerHolder.current.collectAsState()
    val caption by PlayerHolder.caption.collectAsState()
    val audioOnly by PlayerHolder.audioOnly.collectAsState()
    val zoom by Engine.prefs.zoomState
    var page by remember { mutableStateOf(SettingsPage.MAIN) }
    var loop by remember { mutableStateOf(player.repeatMode == Player.REPEAT_MODE_ONE) }
    var bg by remember { mutableStateOf(Engine.prefs.backgroundPlay) }
    val speed = player.playbackParameters.speed

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = YtRaised, dragHandle = null) {
        Column(Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
            when (page) {
                SettingsPage.MAIN -> {
                    MenuRow(Icons.Outlined.Tune, "Quality", stream?.height?.let { "${it}p" } ?: "Auto") { page = SettingsPage.QUALITY }
                    MenuRow(Icons.Outlined.ClosedCaption, "Captions",
                        caption?.label ?: if (stream?.captions.isNullOrEmpty()) "Unavailable" else "Off") { if (!stream?.captions.isNullOrEmpty()) page = SettingsPage.CAPTIONS }
                    MenuRow(Icons.Outlined.SlowMotionVideo, "Playback speed", if (speed == 1f) "Normal" else "${speed}x") { page = SettingsPage.SPEED }
                    SwitchRow(Icons.Outlined.Headphones, "Audio only", audioOnly) { PlayerHolder.applyAudioOnly(context, it) }
                    SwitchRow(Icons.Outlined.Repeat, "Loop video", loop) {
                        loop = it; player.repeatMode = if (it) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    }
                    SwitchRow(Icons.Outlined.AspectRatio, "Zoom to fill", zoom) { Engine.prefs.saveZoom(it) }
                    SwitchRow(Icons.Outlined.PictureInPictureAlt, "Background play", bg) { bg = it; Engine.prefs.backgroundPlay = it }
                }
                SettingsPage.QUALITY -> {
                    SubHeader("Quality for current video") { page = SettingsPage.MAIN }
                    val hs = stream?.heights?.filter { it in 144..2160 }.orEmpty().ifEmpty { listOf(1080, 720, 480, 360) }
                    hs.forEach { h -> Choice("${h}p" + if (h >= 720) " HD" else "", stream?.height == h) { Watch.changeQuality(context, scope, h); onDismiss() } }
                }
                SettingsPage.CAPTIONS -> {
                    SubHeader("Captions") { page = SettingsPage.MAIN }
                    Choice("Turn off captions", caption == null) { PlayerHolder.selectCaption(context, null); onDismiss() }
                    stream?.captions.orEmpty().forEach { c: Caption -> Choice(c.label, caption?.id == c.id) { PlayerHolder.selectCaption(context, c); onDismiss() } }
                }
                SettingsPage.SPEED -> {
                    SubHeader("Playback speed") { page = SettingsPage.MAIN }
                    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                        Choice(if (s == 1f) "Normal" else "${s}x", speed == s) { player.setPlaybackSpeed(s); onDismiss() }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 15.sp, modifier = Modifier.padding(start = 20.dp))
        Text("  ·  $value", fontSize = 15.sp, color = YtSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = YtSecondary)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 15.sp, modifier = Modifier.padding(start = 20.dp).weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF3EA6FF), checkedThumbColor = Color.White))
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(color = YtDivider)
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(40.dp)) { if (selected) Icon(Icons.Outlined.Check, null) }
        Text(label, fontSize = 15.sp)
    }
}
