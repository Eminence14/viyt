package com.viyt.app.ui

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.viyt.app.core.Engine
import com.viyt.app.core.Stream
import com.viyt.app.download.DownloadService
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var url = ""
    private val title = mutableStateOf<String?>(null)
    private val channel = mutableStateOf<String?>(null)
    private val error = mutableStateOf<String?>(null)
    private val loading = mutableStateOf(true)
    private val inPip = mutableStateOf(false)
    private val landscape = mutableStateOf(false)
    private val showSheet = mutableStateOf(false)
    private val quality = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        landscape.value = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val p = ExoPlayer.Builder(this).build().apply {
            setWakeMode(C.WAKE_MODE_NETWORK)
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                    error.value = "Playback failed: ${e.errorCodeName}. Try a lower quality in Settings."
                }
            })
        }
        player = p
        load(intent)

        setContent {
            ViytTheme {
                val pip by inPip
                val land by landscape
                if (pip || land) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) { PlayerSurface(p, controls = !pip) }
                } else {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                            contentAlignment = Alignment.Center) {
                            PlayerSurface(p, controls = true)
                            if (loading.value) CircularProgressIndicator()
                        }
                        Column(Modifier.padding(16.dp)) {
                            Text(title.value ?: "Loading", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            channel.value?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                            quality.value?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                            }
                            error.value?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(onClick = { showSheet.value = true }) {
                                    Icon(Icons.Outlined.Download, null)
                                    Text("  Download")
                                }
                                OutlinedButton(onClick = { enterPip() }) {
                                    Icon(Icons.Outlined.PictureInPictureAlt, null)
                                    Text("  Mini player")
                                }
                            }
                        }
                    }
                }
                if (showSheet.value) {
                    DownloadSheet(title.value ?: url, url, onDismiss = { showSheet.value = false })
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun PlayerSurface(p: ExoPlayer, controls: Boolean) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = p
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            update = { it.useController = controls },
            modifier = Modifier.fillMaxSize(),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        load(intent)
    }

    private fun load(intent: Intent) {
        url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        title.value = intent.getStringExtra(EXTRA_TITLE)
        channel.value = null
        error.value = null
        loading.value = true
        lifecycleScope.launch {
            try {
                val s = Engine.resolve(url)
                title.value = s.title
                channel.value = s.channel
                quality.value = s.height?.let { "${it}p" }
                play(s)
            } catch (t: Throwable) {
                error.value = Engine.friendly(t)
            } finally {
                loading.value = false
            }
        }
    }

    private fun play(s: Stream) {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(s.headers)
        s.headers["User-Agent"]?.let { http.setUserAgent(it) }
        val factory = ProgressiveMediaSource.Factory(http)
        val video: MediaSource = factory.createMediaSource(MediaItem.fromUri(s.videoUrl))
        val source: MediaSource = if (s.audioUrl != null) {
            MergingMediaSource(video, factory.createMediaSource(MediaItem.fromUri(s.audioUrl)))
        } else video
        player?.apply {
            setMediaSource(source)
            prepare()
            play()
        }
    }

    private fun enterPip() {
        runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }.onFailure { Toast.makeText(this, "Mini player isn't available here", Toast.LENGTH_SHORT).show() }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        landscape.value = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, url: String, title: String?) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_TITLE, title)
            )
        }
    }
}
