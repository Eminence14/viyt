package com.viyt.app.ui

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.viyt.app.core.Engine
import com.viyt.app.core.Item
import com.viyt.app.player.PlayerHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A separate player for Shorts, so swiping Shorts never disturbs the video in the mini player. */
object ShortsPlayer {
    private var exo: ExoPlayer? = null
    var renderedUrl by mutableStateOf<String?>(null)

    fun get(ctx: Context): ExoPlayer = exo ?: ExoPlayer.Builder(ctx.applicationContext)
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(5_000, 30_000, 500, 1_000).build())
        .build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { renderedUrl = currentMediaItem?.mediaId }
            })
        }.also { exo = it }

    fun pause() { exo?.pause() }
}

private const val SHORTS_HEIGHT = 720

@Composable
fun ShortsScreen(queue: List<Item>?, start: Int, a: Actions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loaded = if (queue == null) rememberFeed("shorts", "shorts") { fresh -> Engine.shorts(fresh) } else null
    val list = queue ?: loaded?.feed?.items.orEmpty()
    val player = remember { ShortsPlayer.get(context) }

    // Pause the main video while Shorts are on screen, like YouTube.
    LaunchedEffect(Unit) { PlayerHolder.get(context).pause() }
    DisposableEffect(Unit) { onDispose { ShortsPlayer.pause() } }

    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            val err = loaded?.error
            if (err != null) Message(err, "Retry") { loaded?.refresh() }
            else CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val pager = rememberPagerState(initialPage = start.coerceIn(0, list.size - 1)) { list.size }
    LaunchedEffect(pager.settledPage, list) {
        val item = list.getOrNull(pager.settledPage) ?: return@LaunchedEffect
        runCatching {
            val s = Engine.resolve(item.url, SHORTS_HEIGHT)
            player.setMediaSource(PlayerHolder.source(item.url, s, withCaptions = false))
            player.prepare()
            player.play()
        }
        // Get the next two ready so swiping feels instant.
        for (k in 1..2) list.getOrNull(pager.settledPage + k)?.let { n -> scope.launch { runCatching { Engine.resolve(n.url, SHORTS_HEIGHT) } } }
    }

    VerticalPager(state = pager, modifier = Modifier.fillMaxSize().background(Color.Black), beyondViewportPageCount = 1) { page ->
        val item = list[page]
        val current = page == pager.settledPage
        ShortPage(item, current, player, a)
    }
}

@Composable
private fun ShortPage(item: Item, current: Boolean, player: ExoPlayer, a: Actions) {
    val context = LocalContext.current
    var paused by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(current) {
        paused = false
        while (current) {
            val d = player.duration
            progress = if (d > 0) player.currentPosition.toFloat() / d else 0f
            delay(150)
        }
    }
    Box(Modifier.fillMaxSize().pointerInput(current) {
        detectTapGestures(onTap = {
            if (!current) return@detectTapGestures
            if (player.isPlaying) { player.pause(); paused = true } else { player.play(); paused = false }
        })
    }) {
        if (current) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.player = player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!current || ShortsPlayer.renderedUrl != item.url) {
            AsyncImage(item.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (paused) {
            Box(Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.35f).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
        // Right-hand actions
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ShortAction(Icons.Outlined.ThumbUp, "Like") {}
            ShortAction(Icons.Outlined.ThumbDown, "Dislike") {}
            ShortAction(Icons.Outlined.Share, "Share") { share(context, item.url) }
            ShortAction(Icons.Outlined.Download, "Download") { a.more(item) }
        }
        // Title
        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 80.dp, bottom = 28.dp)) {
            item.channel?.let { Text(it, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp) }
            Text(item.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            item.meta?.let { Text(it, color = Color(0xCCFFFFFF), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Color.White))
        }
    }
}

@Composable
private fun ShortAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
