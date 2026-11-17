package com.viyt.app.ui

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.viyt.app.core.Engine
import com.viyt.app.core.InnerTube
import com.viyt.app.core.Item
import com.viyt.app.core.LibraryFeed
import com.viyt.app.player.PlayerHolder

enum class Tab(val label: String, val on: ImageVector, val off: ImageVector) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    SHORTS("Shorts", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    SUBS("Subscriptions", Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions),
    YOU("You", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
}

sealed interface Route {
    data class Search(val query: String = "") : Route
    data class Page(val title: String, val url: String) : Route
    data class Library(val feed: LibraryFeed) : Route
    data object Downloads : Route
    data object Settings : Route
}

class MainActivity : ComponentActivity() {

    private val inPip = mutableStateOf(false)
    private val pendingUrl = mutableStateOf<String?>(null)

    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() { Watch.firstFrame = true }
        override fun onTracksChanged(tracks: Tracks) = PlayerHolder.onTracksChanged(this@MainActivity)
        override fun onPlayerError(error: PlaybackException) { Watch.error = "Playback stopped (${error.errorCodeName})" }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED || !Engine.prefs.autoplay) return
            val p = PlayerHolder.get(this@MainActivity)
            if (p.repeatMode != Player.REPEAT_MODE_OFF || Watch.item == null) return
            val next = Watch.info?.related?.firstOrNull { it.kind == Item.Kind.VIDEO && !it.short } ?: return
            Watch.open(this@MainActivity, lifecycleScope, next)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PlayerHolder.get(this).addListener(listener)
        handleIntent(intent)
        if (savedInstanceState == null && Engine.prefs.startTab == "MUSIC") MusicUi.on = true
        setContent {
            ViytTheme {
                Surface(color = YtBg, contentColor = YtText) { App() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.getStringExtra(EXTRA_TAB) == "downloads") pendingUrl.value = "viyt:downloads"
        if (intent.getBooleanExtra(EXTRA_EXPAND, false) && Watch.item != null) Watch.expanded = true
        if (intent.action == Intent.ACTION_VIEW) intent.data?.toString()?.let { pendingUrl.value = it }
        if (intent.action == Intent.ACTION_SEND) {
            Regex("https?://\\S+").find(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())?.value?.let { pendingUrl.value = it }
        }
    }

    // ---------------------------------------------------------------- full screen, PiP

    fun setFullscreen(on: Boolean) {
        Watch.fullscreen = on
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        immersive(on)
    }

    private fun immersive(on: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    fun enterPip() {
        runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }.onFailure { Toast.makeText(this, "Picture-in-picture isn't available", Toast.LENGTH_SHORT).show() }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val p = PlayerHolder.get(this)
        if (Watch.item != null && p.isPlaying && !PlayerHolder.audioOnly.value) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        // Leaving the floating window without coming back to the app means it was dismissed (closed): stop the video.
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            Watch.close()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (inPip.value) return
        val land = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (Watch.item != null && Watch.expanded) {
            Watch.fullscreen = land
            immersive(land)
        }
    }

    override fun onDestroy() {
        PlayerHolder.get(this).removeListener(listener)
        if (isFinishing && (!Engine.prefs.backgroundPlay || !PlayerHolder.get(this).isPlaying)) Watch.close()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- UI

    @Composable
    private fun App() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val player = remember { PlayerHolder.get(context) }
        var tab by remember { mutableStateOf(runCatching { Tab.valueOf(Engine.prefs.startTab) }.getOrDefault(Tab.HOME)) }
        val hideShorts by Engine.prefs.hideShortsState
        LaunchedEffect(hideShorts) { if (hideShorts && tab == Tab.SHORTS) tab = Tab.HOME }
        val stack = remember { mutableStateListOf<Route>() }
        var shortsQueue by remember { mutableStateOf<List<Item>?>(null) }
        var shortsStart by remember { mutableIntStateOf(0) }
        var more by remember { mutableStateOf<Item?>(null) }
        val signIn = rememberSignIn()

        val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }

        val a = remember {
            Actions(
                open = { i ->
                    when {
                        i.kind == Item.Kind.VIDEO && i.short -> {
                            shortsQueue = listOf(i); shortsStart = 0; tab = Tab.SHORTS; stack.clear(); Watch.expanded = false
                        }
                        i.kind == Item.Kind.VIDEO -> Watch.open(context, scope, i)
                        else -> { stack.add(Route.Page(i.title, i.url)); Watch.expanded = false }
                    }
                },
                more = { more = it },
            )
        }
        val openShorts: (List<Item>, Int) -> Unit = { list, i -> shortsQueue = list; shortsStart = i; tab = Tab.SHORTS; stack.clear() }

        // Links shared into the app, or opened from a notification.
        val pending by pendingUrl
        LaunchedEffect(pending) {
            val u = pending ?: return@LaunchedEffect
            pendingUrl.value = null
            if (u == "viyt:downloads") { stack.add(Route.Downloads); return@LaunchedEffect }
            val id = InnerTube.videoId(u)
            if (id != null) a.open(Item(id, "", "https://www.youtube.com/watch?v=$id", null, null, "https://i.ytimg.com/vi/$id/hqdefault.jpg", Item.Kind.VIDEO))
            else stack.add(Route.Search(u))
        }

        val watching = Watch.item != null
        val music = MusicUi.on
        BackHandler(enabled = watching && Watch.fullscreen) { setFullscreen(false) }
        BackHandler(enabled = !music && watching && Watch.expanded && !Watch.fullscreen) { Watch.expanded = false }
        BackHandler(enabled = !music && !(watching && Watch.expanded) && stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
        val startTab = runCatching { Tab.valueOf(Engine.prefs.startTab) }.getOrDefault(Tab.HOME)
        BackHandler(enabled = !music && !(watching && Watch.expanded) && stack.isEmpty() && tab != startTab) { tab = startTab }

        // One video surface, moved between the watch page, mini player and PiP without restarting.
        val surface = remember { movableContentOf { VideoSurface(player) } }
        val nextUp = Watch.info?.related?.firstOrNull { it.kind == Item.Kind.VIDEO && !it.short }

        val musicQueue by com.viyt.app.player.MusicQueue.tracks.collectAsState()
        val playerMode by PlayerHolder.mode.collectAsState()
        val musicPlaying = playerMode == PlayerHolder.Mode.MUSIC && musicQueue.isNotEmpty()
        when {
            inPip.value -> Box(Modifier.fillMaxSize().background(Color.Black)) { if (watching) surface() }
            music -> MusicApp(onSwitch = { MusicUi.on = false }, onSignIn = signIn)
            watching && Watch.fullscreen -> PlayerBox(
                player, surface, Modifier.fillMaxSize(),
                onCollapse = { setFullscreen(false) }, onFullscreen = { setFullscreen(it) },
                onNext = nextUp?.let { n -> { Watch.open(context, scope, n) } },
            )
            else -> Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    val shortsFull = tab == Tab.SHORTS && stack.isEmpty()
                    Box(Modifier.weight(1f).fillMaxWidth().then(if (shortsFull) Modifier else Modifier.statusBarsPadding())) {
                        Screen(stack.lastOrNull(), tab, stack, a, openShorts, shortsQueue, shortsStart, signIn)
                    }
                    if (musicPlaying && !watching && !shortsFull) MusicMini { MusicUi.on = true; MusicUi.nowPlaying = true }
                    if (watching && !Watch.expanded && !shortsFull) {
                        MiniPlayer(player, surface, onExpand = { Watch.expanded = true }, onClose = { Watch.close() })
                    }
                    BottomBar(tab) { t ->
                        if (t == Tab.SHORTS && tab != Tab.SHORTS) shortsQueue = null
                        tab = t
                        stack.clear()
                    }
                }
                if (watching && Watch.expanded) {
                    Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
                        WatchPage(player, surface, a, onCollapse = { Watch.expanded = false },
                            onFullscreen = { setFullscreen(it) }, onPip = { enterPip() })
                    }
                }
            }
        }
        more?.let { MoreSheet(it) { more = null } }
    }

    @Composable
    private fun Screen(
        top: Route?, tab: Tab, stack: MutableList<Route>, a: Actions,
        openShorts: (List<Item>, Int) -> Unit, shortsQueue: List<Item>?, shortsStart: Int, signIn: () -> Unit,
    ) {
        val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
        val toSearch: () -> Unit = { stack.add(Route.Search()) }
        val toDownloads: () -> Unit = { stack.add(Route.Downloads) }
        when (top) {
            is Route.Search -> SearchScreen(top.query, a, back)
            is Route.Page -> {
                val h = rememberFeed("page:${top.url}", "url:${top.url}") { fresh -> Engine.list(top.url, fresh) }
                ListPage(top.title, h, a, back, big = top.url.contains("/channel/") || top.url.contains("/@"))
            }
            is Route.Library -> {
                val h = rememberFeed("lib:${top.feed}", "lib:${top.feed.name}") { fresh -> Engine.library(top.feed, fresh) }
                ListPage(top.feed.label, h, a, back)
            }
            Route.Downloads -> DownloadsScreen(back)
            Route.Settings -> Column { BackTopBar("Settings", back); SettingsScreen() }
            null -> when (tab) {
                Tab.HOME -> HomeScreen(a, openShorts, toSearch, toDownloads)
                Tab.SHORTS -> ShortsScreen(shortsQueue, shortsStart, a)
                Tab.SUBS -> SubscriptionsScreen(a, toSearch, toDownloads, signIn)
                Tab.YOU -> YouScreen(a, toSearch, { f -> stack.add(Route.Library(f)) }, toDownloads, { stack.add(Route.Settings) }, signIn)
            }
        }
    }

    @Composable
    private fun BottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
        val dark = tab == Tab.SHORTS
        Column(Modifier.fillMaxWidth().background(if (dark) Color.Black else YtBg).navigationBarsPadding()) {
            HorizontalDivider(color = if (dark) Color(0xFF222222) else YtDivider, thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth().height(50.dp)) {
                val hideShorts by Engine.prefs.hideShortsState
                Tab.entries.filter { !(hideShorts && it == Tab.SHORTS) }.forEach { t ->
                    val selected = t == tab
                    Column(
                        Modifier.weight(1f).height(50.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(t) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(if (selected) t.on else t.off, t.label, tint = YtText, modifier = Modifier.size(24.dp))
                        Text(t.label, fontSize = 10.sp, color = YtText, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_EXPAND = "expand"
    }
}
