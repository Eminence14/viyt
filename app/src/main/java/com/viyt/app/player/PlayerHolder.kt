package com.viyt.app.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import com.viyt.app.core.Caption
import com.viyt.app.core.Stream
import com.viyt.app.core.InnerTube
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * One player for the whole app. The screen draws it; PlaybackService keeps it alive in the
 * background and puts its controls on the lock screen and in the notification shade.
 */
@OptIn(UnstableApi::class)
object PlayerHolder {

    private var exo: ExoPlayer? = null

    /** What is loaded right now. */
    val current = MutableStateFlow<Stream?>(null)
    val currentUrl = MutableStateFlow<String?>(null)
    val caption = MutableStateFlow<Caption?>(null)
    val audioOnly = MutableStateFlow(false)

    fun get(context: Context): ExoPlayer = exo ?: build(context.applicationContext).also { exo = it }

    private fun build(ctx: Context): ExoPlayer {
        // Start playing after 0.7 s of buffer instead of the default 2.5 s.
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 700, 1_500)
            .build()
        return ExoPlayer.Builder(ctx)
            .setLoadControl(load)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
    }

    /** Build the media source for a stream (video + separate audio, merged; captions side-loaded). */
    fun source(url: String, s: Stream, withCaptions: Boolean = true): MediaSource {
        val headers = s.headers
        val ds = OkHttpDataSource.Factory(InnerTube.http)
            .setUserAgent(headers["User-Agent"] ?: InnerTube.PLAYER_UA)
            .setDefaultRequestProperties(headers - "User-Agent")
        val factory = DefaultMediaSourceFactory(ds)
        val thumb = s.videoId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
        val meta = MediaMetadata.Builder()
            .setTitle(s.title)
            .setArtist(s.channel)
            .apply { if (thumb != null) setArtworkUri(Uri.parse(thumb)) }
            .build()
        val subs = if (!withCaptions) emptyList() else s.captions.map {
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                .setMimeType(MimeTypes.TEXT_VTT).setLanguage(it.lang).setLabel(it.label).setId(it.id).build()
        }
        val videoItem = MediaItem.Builder()
            .setUri(s.videoUrl).setMediaId(url).setMediaMetadata(meta).setSubtitleConfigurations(subs)
            .apply { if (s.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        val video: MediaSource = factory.createMediaSource(videoItem)
        return if (s.audioUrl != null) MergingMediaSource(video, factory.createMediaSource(MediaItem.fromUri(s.audioUrl))) else video
    }

    /** Load a stream, keeping [startMs] (used when switching quality). */
    fun load(context: Context, url: String, s: Stream, startMs: Long = 0L, play: Boolean = true) {
        val p = get(context)
        val source = source(url, s)
        p.setMediaSource(source, startMs)
        p.prepare()
        p.playWhenReady = play
        current.value = s
        currentUrl.value = url
        // Re-apply the caption choice to the new tracks once they're known.
        caption.value?.let { c -> s.captions.firstOrNull { it.lang == c.lang && it.auto == c.auto }?.let { pending = it } }
        applyAudioOnly(context, audioOnly.value)
        context.startService(Intent(context, PlaybackService::class.java))
    }

    private var pending: Caption? = null

    /** Called by the screen when the player reports new tracks. */
    fun onTracksChanged(context: Context) {
        val c = pending ?: return
        if (selectCaption(context, c)) pending = null
    }

    /** Show [c], or hide captions when null. Returns false when the track isn't loaded yet. */
    fun selectCaption(context: Context, c: Caption?): Boolean {
        val p = get(context)
        caption.value = c
        val b = p.trackSelectionParameters.buildUpon()
        if (c == null) {
            p.trackSelectionParameters = b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            return true
        }
        b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).clearOverridesOfType(C.TRACK_TYPE_TEXT)
        val group = p.currentTracks.groups.firstOrNull { g ->
            g.type == C.TRACK_TYPE_TEXT && (0 until g.length).any { g.getTrackFormat(it).id?.endsWith(c.id) == true }
        }
        if (group != null) {
            val idx = (0 until group.length).first { group.getTrackFormat(it).id?.endsWith(c.id) == true }
            b.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, idx))
        } else {
            b.setPreferredTextLanguage(c.lang)
        }
        p.trackSelectionParameters = b.build()
        return group != null
    }

    /** Audio only: stop fetching video to save data (great on mobile data or with the screen off). */
    fun applyAudioOnly(context: Context, on: Boolean) {
        audioOnly.value = on
        val p = get(context)
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, on)
            .build()
    }

    fun stop() {
        exo?.stop()
        current.value = null
        currentUrl.value = null
    }
}
