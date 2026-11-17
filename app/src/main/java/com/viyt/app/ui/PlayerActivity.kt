package com.viyt.app.ui

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color as AColor
import android.os.Bundle
import android.util.Rational
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionDisabled
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.viyt.app.core.Caption
import com.viyt.app.core.Engine
import com.viyt.app.core.InnerTube
import com.viyt.app.core.Item
import com.viyt.app.core.asDuration
import com.viyt.app.player.PlayerHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private val url = mutableStateOf("")
    private val loading = mutableStateOf(true)
    private val error = mutableStateOf<String?>(null)
    private val inPip = mutableStateOf(false)
    private val fullscreen = mutableStateOf(false)
    private val fallbackTitle = mutableStateOf<String?>(null)

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) = PlayerHolder.onTracksChanged(this@PlayerActivity)
        override fun onPlayerError(e: PlaybackException) {
            error.value = "Playback stopped (${e.errorCodeName}). Tap Retry, or pick a lower quality."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val player = PlayerHolder.get(this)
        player.addListener(listener)
        handle(intent)
        setContent { ViytTheme { PlayerScreen(this, player) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val u = intent.getStringExtra(EXTRA_URL)
        if (u == null) {
            // Opened from the notification: show whatever is playing.
            if (PlayerHolder.currentUrl.value == null) finish() else { url.value = PlayerHolder.currentUrl.value!!; loading.value = false }
            return
        }
        open(u, intent.getStringExtra(EXTRA_TITLE))
    }

    fun open(u: String, title: String?) {
        if (u == PlayerHolder.currentUrl.value) { url.value = u; loading.value = false; return }
        url.value = u
        fallbackTitle.value = title
        error.value = null
        loading.value = true
        lifecycleScope.launch {
            try {
                val s = Engine.resolve(u)
                PlayerHolder.load(this@PlayerActivity, u, s)
            } catch (t: Throwable) {
                error.value = Engine.friendly(t)
            } finally {
                loading.value = false
            }
        }
    }

    fun retry() {
        val u = url.value
        Engine.clearCache()
        PlayerHolder.stop()
        open(u, fallbackTitle.value)
    }

    fun changeQuality(h: Int) {
        val u = url.value
        val p = PlayerHolder.get(this)
        val pos = p.currentPosition
        Engine.prefs.streamHeight = h
        loading.value = true
        lifecycleScope.launch {
            try {
                PlayerHolder.load(this@PlayerActivity, u, Engine.resolve(u, h), pos, p.playWhenReady)
            } catch (t: Throwable) {
                Toast.makeText(this@PlayerActivity, Engine.friendly(t), Toast.LENGTH_LONG).show()
            } finally {
                loading.value = false
            }
        }
    }

    fun setFullscreen(on: Boolean) {
        fullscreen.value = on
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                               else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        immersive(on)
    }

    private fun immersive(on: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun enterPip() {
        runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }.onFailure { Toast.makeText(this, "Mini player isn't available here", Toast.LENGTH_SHORT).show() }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val p = PlayerHolder.get(this)
        if (p.isPlaying && !PlayerHolder.audioOnly.value) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val land = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (!inPip.value) {
            fullscreen.value = land
            immersive(land)
        }
    }

    override fun onDestroy() {
        PlayerHolder.get(this).removeListener(listener)
        // Closing the player stops playback unless Background play is on and something is playing.
        if (isFinishing && (!Engine.prefs.backgroundPlay || !PlayerHolder.get(this).isPlaying)) PlayerHolder.stop()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- UI

    @Composable
    private fun PlayerScreen(act: PlayerActivity, player: Player) {
        val stream by PlayerHolder.current.collectAsState()
        val isFull by fullscreen
        val pip by inPip
        var controls by remember { mutableStateOf(true) }
        var sheet by remember { mutableStateOf(false) }
        var dlSheet by remember { mutableStateOf(false) }

        // Poll the player 4x a second for the seek bar.
        var pos by remember { mutableLongStateOf(0L) }
        var dur by remember { mutableLongStateOf(0L) }
        var buf by remember { mutableLongStateOf(0L) }
        var playing by remember { mutableStateOf(false) }
        var buffering by remember { mutableStateOf(false) }
        var ended by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (true) {
                pos = player.currentPosition
                dur = player.duration.coerceAtLeast(0L)
                buf = player.bufferedPosition
                playing = player.isPlaying
                buffering = player.playbackState == Player.STATE_BUFFERING
                ended = player.playbackState == Player.STATE_ENDED
                delay(250)
            }
        }
        // Auto-hide controls after 3 s of playback.
        LaunchedEffect(controls, playing) {
            if (controls && playing) { delay(3000); controls = false }
        }

        BackHandler(enabled = isFull && !pip) { act.setFullscreen(false) }

        val title = stream?.title ?: fallbackTitle.value ?: ""

        Box(Modifier.fillMaxSize().background(if (isFull || pip) Color.Black else MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    (if (isFull || pip) Modifier.fillMaxSize() else Modifier.statusBarsPadding().fillMaxWidth().aspectRatio(16f / 9f))
                        .background(Color.Black)
                ) {
                    VideoSurface(player)
                    if (!pip) {
                        // Gestures: tap toggles controls, double-tap the sides to skip 10 s.
                        Box(Modifier.fillMaxSize().pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { controls = !controls },
                                onDoubleTap = { o ->
                                    if (o.x < size.width / 2) player.seekBack() else player.seekForward()
                                    controls = true
                                },
                            )
                        })
                        AnimatedVisibility(controls || !playing, enter = fadeIn(), exit = fadeOut()) {
                            Controls(
                                act, player, title, isFull, pos, dur, buf, playing, ended,
                                onSettings = { sheet = true },
                                onClose = { if (isFull) act.setFullscreen(false) else act.finish() },
                            )
                        }
                        if (loading.value || buffering) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center).size(46.dp), color = Color.White, strokeWidth = 3.dp)
                        }
                        // Thin progress line while the controls are hidden (inline only).
                        if (!controls && playing && !isFull && dur > 0) {
                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(pos.toFloat() / dur).height(2.dp).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
                if (!isFull && !pip) Details(act, title, stream?.channel, onDownload = { dlSheet = true })
            }
        }

        if (sheet) PlaybackSheet(act, player, onDismiss = { sheet = false })
        if (dlSheet) DownloadSheet(title, url.value, onDismiss = { dlSheet = false })
    }

    @Composable
    private fun VideoSurface(player: Player) {
        val fit by Engine.prefs.zoomState
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setShutterBackgroundColor(AColor.BLACK)
                    subtitleView?.apply {
                        setStyle(CaptionStyleCompat(AColor.WHITE, 0x99000000.toInt(), AColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_NONE, AColor.WHITE, null))
                        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                        setBottomPaddingFraction(0.06f)
                    }
                }
            },
            update = {
                it.resizeMode = if (fit) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    @Composable
    private fun Controls(
        act: PlayerActivity, player: Player, title: String, isFull: Boolean,
        pos: Long, dur: Long, buf: Long, playing: Boolean, ended: Boolean,
        onSettings: () -> Unit, onClose: () -> Unit,
    ) {
        val caption by PlayerHolder.caption.collectAsState()
        val stream by PlayerHolder.current.collectAsState()
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        val shown = if (dragging) (dragValue * dur).toLong() else pos

        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xAA000000), Color(0x33000000), Color(0xCC000000)))
            )
        ) {
            // Top row: back, title (fullscreen), captions, settings.
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopStart).padding(horizontal = 4.dp, vertical = if (isFull) 10.dp else 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White) }
                Text(
                    if (isFull) title else "", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                if (stream?.captions?.isNotEmpty() == true) {
                    IconButton(onClick = {
                        val s = stream!!
                        if (caption != null) PlayerHolder.selectCaption(act, null)
                        else PlayerHolder.selectCaption(act, s.captions.firstOrNull { !it.auto } ?: s.captions.first())
                    }) {
                        Icon(if (caption != null) Icons.Outlined.ClosedCaption else Icons.Outlined.ClosedCaptionDisabled,
                            "Captions", tint = Color.White)
                    }
                }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Playback settings", tint = Color.White) }
            }

            // Centre: skip back, play/pause, skip forward.
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isFull) 56.dp else 36.dp)) {
                IconButton(onClick = { player.seekBack() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Outlined.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Box(
                    Modifier.size(if (isFull) 72.dp else 62.dp).background(Color(0x33FFFFFF), CircleShape).clickable {
                        when {
                            ended -> { player.seekTo(0); player.play() }
                            player.isPlaying -> player.pause()
                            else -> player.play()
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        when { ended -> Icons.Filled.Replay; playing -> Icons.Filled.Pause; else -> Icons.Filled.PlayArrow },
                        "Play or pause", tint = Color.White, modifier = Modifier.size(if (isFull) 44.dp else 38.dp),
                    )
                }
                IconButton(onClick = { player.seekForward() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Outlined.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }

            // Bottom: time, fullscreen, seek bar.
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = if (isFull) 20.dp else 10.dp)
                .padding(bottom = if (isFull) 14.dp else 0.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${(shown / 1000).asDuration()} / ${(dur / 1000).asDuration()}",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp).weight(1f),
                    )
                    stream?.height?.let {
                        Text("${it}p", color = Color(0xCCFFFFFF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                    IconButton(onClick = { act.setFullscreen(!isFull) }) {
                        Icon(if (isFull) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, "Full screen", tint = Color.White)
                    }
                }
                Slider(
                    value = if (dur > 0) (if (dragging) dragValue else pos.toFloat() / dur) else 0f,
                    onValueChange = { dragging = true; dragValue = it },
                    onValueChangeFinished = { player.seekTo((dragValue * dur).toLong()); dragging = false },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color(0x55FFFFFF),
                    ),
                    modifier = Modifier.fillMaxWidth().height(if (isFull) 28.dp else 20.dp),
                )
            }
        }
    }

    @Composable
    private fun Details(act: PlayerActivity, title: String, channel: String?, onDownload: () -> Unit) {
        val audioOnly by PlayerHolder.audioOnly.collectAsState()
        val context = LocalContext.current
        val id = InnerTube.videoId(url.value)
        var next by remember(id) { mutableStateOf<List<Item>>(emptyList()) }
        LaunchedEffect(id) { if (id != null) next = Engine.upNext(id) }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3,
                        overflow = TextOverflow.Ellipsis)
                    channel?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    error.value?.let {
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { act.retry() }) { Text("Retry") }
                        }
                    }
                }
                // Action pills, scrollable.
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(Icons.Outlined.Download, "Download", onClick = onDownload)
                    Pill(Icons.Outlined.Headphones, if (audioOnly) "Audio only: on" else "Audio only", selected = audioOnly) {
                        PlayerHolder.applyAudioOnly(act, !audioOnly)
                    }
                    Pill(Icons.Outlined.PictureInPictureAlt, "Mini player") { act.enterPip() }
                    Pill(Icons.Outlined.Share, "Share") {
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url.value), "Share video"))
                    }
                }
                HorizontalDivider(Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.outline)
                if (next.isNotEmpty()) {
                    Text("Up next", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                }
            }
            items(next, key = { it.kind.name + it.id }) { item ->
                ItemRow(item, onOpen = { i ->
                    if (i.kind == Item.Kind.VIDEO) act.open(i.url, i.title)
                }, onDownload = {})
            }
        }
    }

    @Composable
    private fun Pill(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
        Row(
            Modifier
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(50))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }

    private enum class Page { MAIN, QUALITY, CAPTIONS }

    @kotlin.OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PlaybackSheet(act: PlayerActivity, player: Player, onDismiss: () -> Unit) {
        val stream by PlayerHolder.current.collectAsState()
        val caption by PlayerHolder.caption.collectAsState()
        val audioOnly by PlayerHolder.audioOnly.collectAsState()
        var page by remember { mutableStateOf(Page.MAIN) }
        var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
        var bg by remember { mutableStateOf(Engine.prefs.backgroundPlay) }
        val zoom by Engine.prefs.zoomState

        ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                when (page) {
                    Page.MAIN -> {
                        Text("Playback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        // Speed chips (like Facebook/TikTok's playback sheets).
                        Row(Modifier.padding(start = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Speed, null, modifier = Modifier.size(20.dp))
                            Text("Speed", modifier = Modifier.padding(start = 12.dp))
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { sp ->
                                FilterChip(selected = speed == sp, onClick = { speed = sp; player.setPlaybackSpeed(sp) },
                                    label = { Text(if (sp == 1f) "Normal" else "${sp}x") })
                            }
                        }
                        SheetRow(Icons.Outlined.HighQuality, "Quality", stream?.height?.let { "${it}p" } ?: "Auto") { page = Page.QUALITY }
                        SheetRow(Icons.Outlined.ClosedCaption, "Captions",
                            caption?.label ?: if (stream?.captions.isNullOrEmpty()) "None available" else "Off") {
                            if (!stream?.captions.isNullOrEmpty()) page = Page.CAPTIONS
                        }
                        ToggleRow(Icons.Outlined.Headphones, "Audio only", "Saves data; keeps the sound playing", audioOnly) {
                            PlayerHolder.applyAudioOnly(act, it)
                        }
                        ToggleRow(Icons.Outlined.AspectRatio, "Fill screen", "Crop to fill instead of fitting the video", zoom) { Engine.prefs.saveZoom(it) }
                        ToggleRow(Icons.Outlined.PictureInPictureAlt, "Background play",
                            "Keep playing after you leave the player", bg) { bg = it; Engine.prefs.backgroundPlay = it }
                    }
                    Page.QUALITY -> {
                        SubHeader("Quality") { page = Page.MAIN }
                        val heights = stream?.heights?.filter { it in 144..2160 }.orEmpty().ifEmpty { listOf(1080, 720, 480, 360) }
                        heights.forEach { h ->
                            Choice("${h}p" + when (h) { 1080 -> "  HD"; 720 -> "  HD"; 360 -> "  data saver"; else -> "" },
                                selected = stream?.height == h) { act.changeQuality(h); onDismiss() }
                        }
                    }
                    Page.CAPTIONS -> {
                        SubHeader("Captions") { page = Page.MAIN }
                        Choice("Off", selected = caption == null) { PlayerHolder.selectCaption(act, null); onDismiss() }
                        stream?.captions.orEmpty().forEach { c: Caption ->
                            Choice(c.label, selected = caption?.id == c.id) { PlayerHolder.selectCaption(act, c); onDismiss() }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SheetRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Text(label, modifier = Modifier.padding(start = 12.dp).weight(1f))
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun ToggleRow(icon: ImageVector, label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(label)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    @Composable
    private fun SubHeader(title: String, onBack: () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }

    @Composable
    private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (selected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, url: String, title: String?) {
            Engine.prefetch(url)
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_TITLE, title)
            )
        }
    }
}
