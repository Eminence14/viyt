package com.viyt.app.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.viyt.app.core.Engine
import com.viyt.app.core.Music
import com.viyt.app.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The music queue. Tracks are ExoPlayer media items, so shuffle, repeat, next/previous,
 * the notification and the lock screen all work natively. A radio keeps topping itself up.
 */
object MusicQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tracks = MutableStateFlow<List<Track>>(emptyList())
    val index = MutableStateFlow(0)
    private var radioToken: String? = null
    @Volatile private var loadingMore = false
    private var listening = false

    val current: Track? get() = tracks.value.getOrNull(index.value)

    /** Called by the player when ExoPlayer needs a track's audio. Blocking is fine here (loader thread). */
    fun audioUrl(videoId: String): String = runBlocking {
        val s = Engine.resolve("https://www.youtube.com/watch?v=$videoId", 144)
        s.audioUrl ?: s.videoUrl
    }

    private fun item(t: Track): MediaItem = MediaItem.Builder()
        .setUri("viyt://audio/${t.videoId}")
        .setMediaId(t.videoId)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist)
                .apply { t.thumb?.let { setArtworkUri(Uri.parse(it)) } }
                .build()
        )
        .build()

    private fun attach(ctx: Context) {
        if (listening) return
        listening = true
        PlayerHolder.get(ctx).addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (PlayerHolder.mode.value != PlayerHolder.Mode.MUSIC) return
                val p = PlayerHolder.get(ctx)
                index.value = p.currentMediaItemIndex
                if (p.mediaItemCount - p.currentMediaItemIndex <= 3) more(ctx)
                // Warm up the next track's link so the change is instant.
                tracks.value.getOrNull(p.currentMediaItemIndex + 1)?.let { n -> scope.launch { runCatching { audioUrl(n.videoId) } } }
            }
        })
    }

    private fun start(ctx: Context, list: List<Track>, at: Int) {
        attach(ctx)
        val p = PlayerHolder.get(ctx)
        PlayerHolder.mode.value = PlayerHolder.Mode.MUSIC
        PlayerHolder.current.value = null
        PlayerHolder.currentUrl.value = null
        tracks.value = list
        index.value = at
        p.setMediaItems(list.map(::item), at, 0L)
        p.repeatMode = Player.REPEAT_MODE_OFF
        p.prepare()
        p.play()
        ctx.startService(Intent(ctx, PlaybackService::class.java))
    }

    /** Play a song and keep going with an endless mix based on it. */
    fun playRadio(ctx: Context, seed: Track) {
        radioToken = null
        // Block the automatic top-up while the radio's first page loads, so it isn't fetched twice.
        loadingMore = true
        start(ctx, listOf(seed), 0)
        scope.launch {
            try {
                runCatching { Music.radio(seed.videoId) }.onSuccess { r ->
                    radioToken = r.next
                    append(ctx, r.tracks.filter { it.videoId != seed.videoId })
                }
            } finally { loadingMore = false }
        }
    }

    /** Play a fixed list (album, playlist), optionally shuffled, then continue with a mix. */
    fun playList(ctx: Context, list: List<Track>, at: Int = 0, shuffle: Boolean = false) {
        if (list.isEmpty()) return
        radioToken = null
        val order = if (shuffle) list.shuffled() else list
        start(ctx, order, if (shuffle) 0 else at)
    }

    fun playNext(ctx: Context, t: Track) {
        if (PlayerHolder.mode.value != PlayerHolder.Mode.MUSIC || tracks.value.isEmpty()) { playRadio(ctx, t); return }
        val p = PlayerHolder.get(ctx)
        val at = p.currentMediaItemIndex + 1
        p.addMediaItem(at, item(t))
        tracks.value = tracks.value.toMutableList().apply { add(at.coerceAtMost(size), t) }
    }

    fun addToQueue(ctx: Context, t: Track) {
        if (PlayerHolder.mode.value != PlayerHolder.Mode.MUSIC || tracks.value.isEmpty()) { playRadio(ctx, t); return }
        PlayerHolder.get(ctx).addMediaItem(item(t))
        tracks.value = tracks.value + t
    }

    /**
     * Swap the playing song between audio and its music video, in place, keeping the position.
     * The video plays on the ViVibe player screen; the queue is unchanged.
     */
    suspend fun showVideo(ctx: Context, videoId: String, video: Boolean) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        val stream = if (video) Engine.resolve(url, minOf(Engine.prefs.streamHeight, 720)) else null
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            val p = PlayerHolder.get(ctx)
            val i = p.currentMediaItemIndex
            val t = tracks.value.getOrNull(i)
            if (t == null || t.videoId != videoId) return@withContext
            val pos = p.currentPosition
            val play = p.playWhenReady
            if (stream != null) {
                p.addMediaSource(i + 1, PlayerHolder.source(url, stream, withCaptions = false))
            } else p.addMediaItem(i + 1, item(t))
            p.seekTo(i + 1, pos)
            p.removeMediaItem(i)
            index.value = p.currentMediaItemIndex
            p.playWhenReady = play
        }
    }

    fun jump(ctx: Context, i: Int) {
        val p = PlayerHolder.get(ctx)
        if (i in 0 until p.mediaItemCount) { p.seekTo(i, 0L); p.play() }
    }

    fun remove(ctx: Context, i: Int) {
        val p = PlayerHolder.get(ctx)
        if (i !in 0 until p.mediaItemCount || i == p.currentMediaItemIndex) return
        p.removeMediaItem(i)
        tracks.value = tracks.value.toMutableList().apply { removeAt(i) }
        index.value = p.currentMediaItemIndex
    }

    /** Drag-to-reorder in Up next. */
    fun move(ctx: Context, from: Int, to: Int) {
        val p = PlayerHolder.get(ctx)
        val n = p.mediaItemCount
        if (from !in 0 until n || to !in 0 until n || from == to) return
        p.moveMediaItem(from, to)
        tracks.value = tracks.value.toMutableList().apply { add(to, removeAt(from)) }
        index.value = p.currentMediaItemIndex
    }

    // ------------------------------------------------------------------ sleep timer

    /** When the music stops: epoch ms, -1 = at the end of the current song, null = off. */
    val sleepAt = MutableStateFlow<Long?>(null)
    private val handler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private var sleepTask: Runnable? = null
    private var endOfSongListener: Player.Listener? = null

    fun sleep(ctx: Context, minutes: Int?) {
        val p = PlayerHolder.get(ctx)
        sleepTask?.let { handler.removeCallbacks(it) }
        endOfSongListener?.let { p.removeListener(it) }
        sleepTask = null; endOfSongListener = null
        when {
            minutes == null -> sleepAt.value = null
            minutes < 0 -> {
                sleepAt.value = -1L
                val l = object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) { p.pause(); p.removeListener(this); sleepAt.value = null }
                    }
                }
                endOfSongListener = l
                p.addListener(l)
            }
            else -> {
                sleepAt.value = System.currentTimeMillis() + minutes * 60_000L
                val r = Runnable { p.pause(); sleepAt.value = null }
                sleepTask = r
                handler.postDelayed(r, minutes * 60_000L)
            }
        }
    }

    private fun append(ctx: Context, more: List<Track>) {
        android.os.Handler(ctx.mainLooper).post {
            if (PlayerHolder.mode.value != PlayerHolder.Mode.MUSIC) return@post
            // De-duplicate on the main thread, where the queue is changed, so two top-ups can't race.
            val existing = tracks.value.mapTo(HashSet()) { it.videoId }
            val fresh = more.filter { existing.add(it.videoId) }
            if (fresh.isEmpty()) return@post
            PlayerHolder.get(ctx).addMediaItems(fresh.map(::item))
            tracks.value = tracks.value + fresh
        }
    }

    /** Top up the queue near its end: continue the radio, or start one from the last track. */
    private fun more(ctx: Context) {
        if (loadingMore) return
        val last = tracks.value.lastOrNull() ?: return
        loadingMore = true
        scope.launch {
            try {
                val token = radioToken
                val page = if (token != null) Music.radioMore(token) else Music.radio(last.videoId)
                radioToken = page.next
                append(ctx, page.tracks)
            } catch (_: Throwable) {
            } finally { loadingMore = false }
        }
    }

    /** A video took over the player. */
    fun onVideoStarted() {
        tracks.value = emptyList()
        radioToken = null
    }
}
