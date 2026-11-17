package com.viyt.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.viyt.app.core.Engine
import com.viyt.app.core.Feed
import com.viyt.app.core.Item
import com.viyt.app.download.DownloadService
import com.viyt.app.download.DownloadTask
import com.viyt.app.download.Downloads

class MainActivity : ComponentActivity() {

    /** A link shared into viyt from another app (e.g. YouTube's Share button). */
    private val sharedUrl = mutableStateOf<String?>(null)
    private val startTab = mutableStateOf(Tab.HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { ViytTheme { App(sharedUrl, startTab) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getStringExtra(EXTRA_TAB) == "downloads") startTab.value = Tab.DOWNLOADS
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            Regex("https?://\\S+").find(text)?.value?.let { sharedUrl.value = it }
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    SEARCH("Search", Icons.Outlined.Search),
    LIBRARY("Library", Icons.Outlined.VideoLibrary),
    DOWNLOADS("Downloads", Icons.Outlined.Download),
}

/** A pushed page on top of the tabs: a playlist/channel list or Settings. */
sealed class Page {
    data class ListPage(val title: String, val url: String) : Page()
    data class FeedPage(val feed: Feed) : Page()
    data object Settings : Page()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(sharedUrl: androidx.compose.runtime.MutableState<String?>, startTab: androidx.compose.runtime.MutableState<Tab>) {
    val context = LocalContext.current
    var tab by startTab
    val stack = remember { mutableStateListOf<Page>() }
    var downloadTarget by remember { mutableStateOf<Item?>(null) }

    // Ask for notification permission once, so download progress can show.
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val open: (Item) -> Unit = { item ->
        when (item.kind) {
            Item.Kind.VIDEO -> PlayerActivity.start(context, item.url, item.title)
            Item.Kind.PLAYLIST -> stack.add(Page.ListPage(item.title, item.url))
            Item.Kind.CHANNEL -> stack.add(Page.ListPage(item.title, item.url.trimEnd('/') + "/videos"))
        }
    }
    val download: (Item) -> Unit = { downloadTarget = it }

    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    val top = stack.lastOrNull()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (top != null) IconButton(onClick = { stack.removeAt(stack.lastIndex) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                title = {
                    when (top) {
                        is Page.ListPage -> Text(top.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        is Page.FeedPage -> Text(top.feed.label)
                        Page.Settings -> Text("Settings")
                        null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("vi", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Text("yt", fontWeight = FontWeight.Black)
                        }
                    }
                },
                actions = {
                    if (top == null) IconButton(onClick = { stack.add(Page.Settings) }) {
                        Icon(Icons.Outlined.Settings, "Settings")
                    }
                },
            )
        },
        bottomBar = {
            if (top == null) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                val active = Downloads.tasks.collectAsState().value.count { it.active }
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, null) },
                        label = { Text(if (t == Tab.DOWNLOADS && active > 0) "${t.label} ($active)" else t.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            EngineBanner()
            when (top) {
                is Page.ListPage -> ItemList(top.url, { fresh -> Engine.list(top.url, fresh) }, open, download)
                is Page.FeedPage -> ItemList("feed:${top.feed.name}", { fresh -> Engine.feed(top.feed, fresh) }, open, download)
                Page.Settings -> SettingsScreen()
                null -> when (tab) {
                    Tab.HOME -> HomeScreen(open, download)
                    Tab.SEARCH -> SearchScreen(open, download)
                    Tab.LIBRARY -> LibraryScreen { f -> stack.add(Page.FeedPage(f)) }
                    Tab.DOWNLOADS -> DownloadsScreen()
                }
            }
        }
    }

    downloadTarget?.let { item ->
        DownloadSheet(item.title, item.url, onDismiss = { downloadTarget = null })
    }
    sharedUrl.value?.let { url ->
        DownloadSheet(
            "Shared link", url, showPlay = true,
            onPlay = { PlayerActivity.start(context, url, null) },
            onDismiss = { sharedUrl.value = null },
        )
    }
}

@Composable
private fun EngineBanner() {
    // The fast path never waits on yt-dlp, so only show something when yt-dlp is busy or broken.
    val status by Engine.status.collectAsState()
    val failed by Engine.failed.collectAsState()
    if (failed != null) {
        Text("Download engine error: $failed", color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    } else if (status != "Ready") {
        Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
}

// ------------------------------------------------------------------ Home

@Composable
private fun HomeScreen(open: (Item) -> Unit, download: (Item) -> Unit) {
    var signedIn by remember { mutableStateOf(Engine.cookies.isSignedIn) }
    if (!signedIn) {
        SignInCard(onSignedIn = { signedIn = true })
    } else {
        ItemList("home", { fresh -> Engine.feed(Feed.HOME, fresh) }, open, download)
    }
}

@Composable
fun SignInCard(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Engine.cookies.isSignedIn) { Engine.clearCache(); onSignedIn() }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            if (Engine.cookies.importFrom(uri)) {
                Engine.clearCache(); onSignedIn()
                Toast.makeText(context, "Signed in from cookies.txt", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "That file doesn't contain YouTube cookies", Toast.LENGTH_LONG).show()
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp)) {
                Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
                Text("Sign in to YouTube", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Brings in your home feed, liked videos, playlists, Watch later, history and subscriptions. " +
                        "Your session stays on this phone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = { login.launch(Intent(context, LoginActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()) { Text("Sign in with Google") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { importer.launch(arrayOf("text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth()) { Text("Import cookies.txt instead") }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("You can still search, stream and download without signing in.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

// ------------------------------------------------------------------ Search

@Composable
private fun SearchScreen(open: (Item) -> Unit, download: (Item) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text("Search YouTube or paste a link") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(Icons.Outlined.Close, "Clear") } },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { query = text.trim() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        )
        val q = query
        when {
            q.isEmpty() -> Message("Type something and press search.\nPasting a YouTube link works too.")
            q.startsWith("http") -> ItemList("link:$q", { fresh ->
                // A pasted playlist/channel opens as a list; a single video shows as one row.
                if (q.contains("list=") || q.contains("/@") || q.contains("/channel/")) Engine.list(q, fresh)
                else listOf(Item(q, q, q, null, null, null, Item.Kind.VIDEO))
            }, open, download)
            else -> ItemList("search:$q", { _ -> Engine.search(q) }, open, download)
        }
    }
}

// ------------------------------------------------------------------ Library

@Composable
private fun LibraryScreen(openFeed: (Feed) -> Unit) {
    var signedIn by remember { mutableStateOf(Engine.cookies.isSignedIn) }
    if (!signedIn) {
        SignInCard(onSignedIn = { signedIn = true })
        return
    }
    val icons = mapOf(
        Feed.SUBSCRIPTIONS to Icons.Outlined.Subscriptions,
        Feed.LIKED to Icons.Outlined.ThumbUp,
        Feed.WATCH_LATER to Icons.Outlined.Schedule,
        Feed.HISTORY to Icons.Outlined.History,
        Feed.PLAYLISTS to Icons.AutoMirrored.Outlined.PlaylistPlay,
        Feed.CHANNELS to Icons.Outlined.Person,
    )
    LazyColumn(Modifier.fillMaxSize()) {
        items(Feed.entries.filter { it != Feed.HOME }) { f ->
            ListItem(
                headlineContent = { Text(f.label) },
                leadingContent = { Icon(icons[f] ?: Icons.Outlined.VideoLibrary, null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { openFeed(f) },
            )
        }
    }
}

// ------------------------------------------------------------------ Downloads

@Composable
private fun DownloadsScreen() {
    val context = LocalContext.current
    val tasks by Downloads.tasks.collectAsState()
    if (tasks.isEmpty()) {
        Message("No downloads yet.\nTap the download icon on any video.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        if (tasks.any { !it.active }) {
            TextButton(onClick = { Downloads.clearFinished() }, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
                Text("Clear finished")
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(tasks, key = { it.id }) { t -> DownloadRow(t, context) }
        }
    }
}

@Composable
private fun DownloadRow(t: DownloadTask, context: android.content.Context) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = t.status == DownloadTask.Status.DONE && t.resultUri != null) {
                val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(t.resultUri), t.mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(i) }
                    .onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                val state = when (t.status) {
                    DownloadTask.Status.QUEUED -> "Waiting"
                    DownloadTask.Status.RUNNING -> buildString {
                        append(t.message ?: "Downloading")
                        if (t.progress > 0f) append(" · ${(t.progress * 100).toInt()}%")
                        if (t.etaSec > 0) append(" · ${t.etaSec}s left")
                    }
                    DownloadTask.Status.DONE -> "Done · tap to open"
                    DownloadTask.Status.FAILED -> "Failed: ${t.message}"
                    DownloadTask.Status.CANCELED -> "Canceled"
                }
                Text(
                    "${t.format.label} · $state",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (t.status == DownloadTask.Status.FAILED) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (t.active) IconButton(onClick = { DownloadService.cancel(context, t.id) }) {
                Icon(Icons.Outlined.Close, "Cancel")
            }
        }
        if (t.status == DownloadTask.Status.RUNNING) {
            Spacer(Modifier.height(6.dp))
            if (t.progress > 0f) LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
