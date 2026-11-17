package com.viyt.app.core

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * viyt's engine.
 *  - Fast path: InnerTube (YouTube's own API) for every screen and every stream link.
 *  - Cache: lists live in memory and on disk, so screens paint instantly on launch and refresh
 *    quietly in the background, the way YouTube's app does.
 *  - Fallback: yt-dlp, only when the fast path can't answer, and for MP3.
 */
object Engine {
    lateinit var prefs: Prefs
        private set
    lateinit var cookies: Cookies
        private set
    private lateinit var app: Context

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val status = MutableStateFlow("Ready")
    val ytdlpReady = MutableStateFlow(false)
    val version = MutableStateFlow<String?>(null)
    val failed = MutableStateFlow<String?>(null)
    /** Bumped on sign-in/out so screens reload. */
    val accountVersion = MutableStateFlow(0)

    private class Cached<T>(val value: T, val at: Long)
    private val feeds = ConcurrentHashMap<String, Cached<Feed>>()
    private val streams = ConcurrentHashMap<String, Cached<Stream>>()
    private val watches = ConcurrentHashMap<String, Cached<WatchInfo>>()
    private val pendingStreams = ConcurrentHashMap<String, Deferred<Stream>>()
    private val pendingWatch = ConcurrentHashMap<String, Deferred<WatchInfo>>()

    private const val FEED_TTL = 15 * 60_000L
    private const val STREAM_TTL = 4 * 3600_000L

    fun start(context: Context) {
        app = context.applicationContext
        prefs = Prefs(app)
        cookies = Cookies(app)
        scope.launch { InnerTube.warm() }
        scope.launch {
            try {
                YoutubeDL.init(app)
                FFmpeg.getInstance().init(app)
                version.value = YoutubeDL.version(app)
                ytdlpReady.value = true
                if (System.currentTimeMillis() - prefs.lastUpdateCheck > 12 * 3600_000L) {
                    delay(30_000)
                    runCatching { update() }
                }
            } catch (t: Throwable) {
                failed.value = t.message ?: t.toString()
            }
        }
    }

    val signedIn: Boolean get() = cookies.isSignedIn
    private fun cookieMap(): Map<String, String>? = if (cookies.isSignedIn) cookies.map() else null
    val jsRuntime: File? get() = File(app.applicationInfo.nativeLibraryDir, "libqjs.so").takeIf { it.exists() }

    fun onAccountChanged() {
        clearCache()
        File(app.cacheDir, "feeds2").deleteRecursively()
        accountVersion.value++
    }

    // ---------------------------------------------------------------- feed cache (memory + disk)

    private fun diskFile(key: String) = File(File(app.cacheDir, "feeds2").apply { mkdirs() }, key.hashCode().toUInt().toString() + ".json")

    /** Whatever we have for [key] right now, from memory or disk, however old. Instant. */
    fun peek(key: String): Feed? {
        feeds[key]?.let { return it.value }
        val f = diskFile(key)
        if (!f.exists()) return null
        return runCatching {
            val o = JSONObject(f.readText())
            val chips = o.optJSONArray("chips")?.let { a ->
                (0 until a.length()).map { i ->
                    val c = a.getJSONObject(i)
                    Chip(c.getString("label"), c.optStringOrNull("token"), c.optStringOrNull("query"))
                }
            }.orEmpty()
            Feed(Item.listFromJson(o.getJSONArray("items")), chips)
        }.getOrNull()?.also { feeds[key] = Cached(it, 0L) }
    }

    private fun store(key: String, feed: Feed) {
        feeds[key] = Cached(feed, System.currentTimeMillis())
        scope.launch {
            runCatching {
                val chips = JSONArray().apply {
                    feed.chips.forEach { put(JSONObject().put("label", it.label).put("token", it.token).put("query", it.query)) }
                }
                diskFile(key).writeText(JSONObject().put("items", Item.listToJson(feed.items)).put("chips", chips).toString())
            }
        }
    }

    private suspend fun cachedFeed(key: String, fresh: Boolean, load: suspend () -> Feed): Feed {
        if (!fresh) feeds[key]?.let { if (System.currentTimeMillis() - it.at < FEED_TTL) return it.value }
        val f = withContext(Dispatchers.IO) { load() }
        store(key, f)
        return f
    }

    /** Topics for the Home chips when YouTube has no personal feed (signed out). */
    private val TOPICS = listOf("Music", "Afrobeats", "News", "Football", "Gaming", "Comedy", "Podcasts", "Tech", "Nollywood", "Live")

    /**
     * Home is YouTube's own recommended feed for your account, the same one youtube.com shows,
     * including channels you don't follow. It is never rebuilt from searches while you're signed in:
     * if YouTube's feed can't be loaded, Home says so instead of showing a stand-in.
     */
    suspend fun home(fresh: Boolean = false): Feed = cachedFeed("home", fresh) {
        val c = cookieMap()
        if (c != null) {
            val page = try {
                InnerTube.browsePage("FEwhat_to_watch", null, 80, c)
            } catch (e: InnerTube.SessionExpired) {
                throw e
            } catch (e: Exception) {
                // One retry with a fresh visitor session before giving up.
                InnerTube.renewVisitor()
                InnerTube.browsePage("FEwhat_to_watch", null, 80, c)
            }
            if (page.items.isEmpty()) throw IllegalStateException(
                "YouTube sent an empty Home for your account. This happens when watch history is turned off " +
                    "(turn it on at myactivity.google.com > YouTube History). Pull down to try again."
            )
            page
        } else {
            // Signed out, YouTube's home is empty; build one from what's popular right now.
            val items = coroutineScope {
                listOf("trending today", "new music this week", "news today").map { q ->
                    async { runCatching { InnerTube.search(q, 20, null) }.getOrDefault(emptyList()) }
                }.awaitAll()
            }.let { lists -> interleave(lists) }
            Feed(items, listOf(Chip("All", null)) + TOPICS.map { Chip(it, null, it) })
        }
    }

    suspend fun chip(chip: Chip, fresh: Boolean = false): Feed = cachedFeed("chip:${chip.label}:${chip.token ?: chip.query}", fresh) {
        val items = when {
            chip.token != null -> InnerTube.continueWith(chip.token, 60, cookieMap())
            chip.query != null -> InnerTube.search(chip.query, 40, cookieMap())
            else -> emptyList()
        }
        Feed(items)
    }

    suspend fun library(feed: LibraryFeed, fresh: Boolean = false): Feed = cachedFeed("lib:${feed.name}", fresh) {
        Feed(fastOr({ InnerTube.browse(feed.browseId, null, 100, cookieMap()) }) { ytdlpList(feed.ytdlpUrl, 100) })
    }

    suspend fun search(query: String): Feed = cachedFeed("search:$query", false) {
        Feed(fastOr({ InnerTube.search(query, 40, cookieMap()) }) { ytdlpList("ytsearch40:$query", 40) })
    }

    suspend fun suggest(query: String): List<String> =
        withContext(Dispatchers.IO) { runCatching { InnerTube.suggest(query) }.getOrDefault(emptyList()) }

    /** Shorts for the Shorts tab, gathered from Home, Subscriptions and a Shorts search. */
    suspend fun shorts(fresh: Boolean = false): Feed = cachedFeed("shorts", fresh) {
        val c = cookieMap()
        val lists = coroutineScope {
            listOf(
                async { runCatching { home().items }.getOrDefault(emptyList()) },
                async { if (c != null) runCatching { InnerTube.browse("FEsubscriptions", null, 60, c) }.getOrDefault(emptyList()) else emptyList() },
                async { runCatching { InnerTube.search("#shorts", 60, c) }.getOrDefault(emptyList()) },
                async { runCatching { InnerTube.search("funny shorts", 60, c) }.getOrDefault(emptyList()) },
            ).awaitAll()
        }
        Feed(interleave(lists.map { l -> l.filter { it.short } }).distinctBy { it.id })
    }

    /** A playlist or channel URL. */
    suspend fun list(url: String, fresh: Boolean = false): Feed = cachedFeed("url:$url", fresh) {
        Feed(fastOr({
            val c = cookieMap()
            val listId = Regex("[?&]list=([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)
            val channelId = Regex("/channel/(UC[A-Za-z0-9_-]{22})").find(url)?.groupValues?.get(1)
            when {
                listId != null -> InnerTube.browse("VL$listId", null, 200, c)
                channelId != null -> InnerTube.browse(channelId, CHANNEL_VIDEOS, 120, c)
                url.contains("/@") || url.contains("/c/") || url.contains("/user/") -> {
                    val id = InnerTube.resolveBrowseId(url.substringBefore("/videos"), c) ?: throw IllegalStateException("Channel not found")
                    InnerTube.browse(id, CHANNEL_VIDEOS, 120, c)
                }
                else -> throw IllegalStateException("Not a list URL")
            }
        }) { ytdlpList(url, 200) })
    }

    private const val CHANNEL_VIDEOS = "EgZ2aWRlb3PyBgQKAjoA"

    private fun interleave(lists: List<List<Item>>): List<Item> {
        val out = LinkedHashMap<String, Item>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) for (l in lists) l.getOrNull(i)?.let { out.putIfAbsent(it.id, it) }
        return out.values.toList()
    }

    private suspend fun <T> fastOr(fast: () -> T, slow: suspend () -> T): T =
        try { fast() } catch (e: InnerTube.SessionExpired) { throw e } catch (t: Throwable) { slow() }

    // ---------------------------------------------------------------- watch page

    suspend fun watch(videoId: String): WatchInfo {
        watches[videoId]?.let { if (System.currentTimeMillis() - it.at < FEED_TTL) return it.value }
        val job = pendingWatch.getOrPut(videoId) {
            scope.async {
                try {
                    InnerTube.watch(videoId, cookieMap()).also { watches[videoId] = Cached(it, System.currentTimeMillis()) }
                } finally { pendingWatch.remove(videoId) }
            }
        }
        return job.await()
    }

    suspend fun comments(token: String): Pair<List<Comment>, String?> =
        withContext(Dispatchers.IO) { InnerTube.comments(token, cookieMap()) }

    suspend fun like(videoId: String, on: Boolean): Unit = withContext(Dispatchers.IO) {
        val c = cookieMap() ?: throw IllegalStateException("Sign in to like videos")
        InnerTube.like(videoId, on, c)
        watches.remove(videoId)
        Unit
    }

    suspend fun subscribe(channelId: String, on: Boolean): Unit = withContext(Dispatchers.IO) {
        val c = cookieMap() ?: throw IllegalStateException("Sign in to subscribe")
        InnerTube.subscribe(channelId, on, c)
        watches.clear()
    }

    // ---------------------------------------------------------------- streams

    suspend fun resolve(url: String, height: Int = prefs.streamHeight): Stream {
        val key = "$height|$url"
        streams[key]?.let { if (System.currentTimeMillis() - it.at < STREAM_TTL) return it.value }
        val job = pendingStreams.getOrPut(key) {
            scope.async {
                try {
                    val id = InnerTube.videoId(url)
                    val s = if (id != null) {
                        try { InnerTube.player(id, height) } catch (t: Throwable) { ytdlpResolve(url, height) }
                    } else ytdlpResolve(url, height)
                    streams[key] = Cached(s, System.currentTimeMillis())
                    s
                } finally { pendingStreams.remove(key) }
            }
        }
        return job.await()
    }

    /** Start fetching the stream and the watch page the moment a video is tapped. */
    fun prefetch(url: String, height: Int = prefs.streamHeight) {
        val id = InnerTube.videoId(url) ?: return
        scope.launch { runCatching { resolve(url, height) } }
        scope.launch { runCatching { watch(id) } }
    }

    fun forgetStream(url: String) {
        streams.keys.filter { it.endsWith("|$url") }.forEach { streams.remove(it) }
    }

    // ---------------------------------------------------------------- yt-dlp fallback

    private suspend fun awaitYtdlp() { ytdlpReady.first { it } }

    private fun base(url: String): YoutubeDLRequest {
        val r = YoutubeDLRequest(url)
        r.addOption("--cache-dir", File(app.filesDir, "ytdlp-cache").absolutePath)
        r.addOption("--no-warnings")
        jsRuntime?.let {
            r.addOption("--js-runtimes", "quickjs:${it.absolutePath}")
            r.addOption("--remote-components", "ejs:github")
        }
        if (cookies.isSignedIn) r.addOption("--cookies", cookies.file.absolutePath)
        return r
    }

    private fun run(req: YoutubeDLRequest): String = YoutubeDL.execute(req, null, null).out

    private suspend fun ytdlpList(url: String, limit: Int): List<Item> {
        awaitYtdlp()
        val root = JSONObject(run(base(url).addOption("--flat-playlist").addOption("-J").addOption("--playlist-end", limit)))
        val arr = root.optJSONArray("entries") ?: return emptyList()
        val out = ArrayList<Item>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val id = e.optString("id")
            if (id.isEmpty()) continue
            val u = e.optString("url")
            val kind = when {
                u.contains("list=") -> Item.Kind.PLAYLIST
                u.contains("/channel/") || u.contains("/@") -> Item.Kind.CHANNEL
                else -> Item.Kind.VIDEO
            }
            val dur = if (e.has("duration") && !e.isNull("duration")) e.optDouble("duration").toLong() else null
            out.add(Item(id, e.optString("title").ifEmpty { id },
                if (kind == Item.Kind.VIDEO) "https://www.youtube.com/watch?v=$id" else u,
                e.optString("channel").ifEmpty { e.optString("uploader") }.ifEmpty { null }, dur,
                if (kind == Item.Kind.VIDEO) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else null, kind,
                meta = e.optLong("view_count", -1).takeIf { it >= 0 }?.let { "${compact(it)} views" }))
        }
        return out
    }

    private fun compact(n: Long): String = when {
        n >= 1_000_000_000 -> "%.1fB".format(n / 1e9)
        n >= 1_000_000 -> "%.1fM".format(n / 1e6)
        n >= 1_000 -> "%.0fK".format(n / 1e3)
        else -> n.toString()
    }.replace(".0", "")

    private suspend fun ytdlpResolve(url: String, h: Int): Stream {
        awaitYtdlp()
        val fmt = "bv*[height<=$h][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]/b"
        val j = JSONObject(run(base(url).addOption("-J").addOption("--no-playlist").addOption("-f", fmt)))
        val title = j.optString("title")
        val channel = j.optString("channel").ifEmpty { j.optString("uploader") }.ifEmpty { null }
        val heights = listOf(1080, 720, 480, 360)
        val caps = ArrayList<Caption>()
        j.optJSONObject("subtitles")?.let { o ->
            o.keys().forEach { lang ->
                val arr = o.optJSONArray(lang) ?: return@forEach
                for (i in 0 until arr.length()) {
                    val f = arr.optJSONObject(i) ?: continue
                    if (f.optString("ext") == "vtt") { caps.add(Caption("cc_${caps.size}_$lang", f.optString("name").ifEmpty { lang }, lang, f.optString("url"), false)); break }
                }
            }
        }
        val hdr = HashMap<String, String>()
        val rf = j.optJSONArray("requested_formats")
        return if (rf != null && rf.length() >= 2) {
            var v: JSONObject? = null
            var a: JSONObject? = null
            for (i in 0 until rf.length()) {
                val f = rf.getJSONObject(i)
                if (f.optString("vcodec") != "none" && v == null) v = f else a = f
            }
            val video = v ?: rf.getJSONObject(0)
            video.optJSONObject("http_headers")?.let { o -> o.keys().forEach { hdr[it] = o.optString(it) } }
            Stream(title, channel, video.getString("url"), a?.optString("url"), hdr, video.optInt("height").takeIf { it > 0 },
                captions = caps, heights = heights, videoId = j.optString("id"))
        } else {
            j.optJSONObject("http_headers")?.let { o -> o.keys().forEach { hdr[it] = o.optString(it) } }
            Stream(title, channel, j.getString("url"), null, hdr, j.optInt("height").takeIf { it > 0 },
                captions = caps, heights = heights, videoId = j.optString("id"))
        }
    }

    /** Song download: best audio as M4A/MP3 with title, artist, album and cover art written into the file. */
    suspend fun ytdlpSongRequest(url: String, mp3: Boolean, outDir: File, withArt: Boolean): YoutubeDLRequest {
        awaitYtdlp()
        val r = base(url)
            .addOption("--no-playlist").addOption("--no-mtime")
            .addOption("-f", if (mp3) "ba" else "ba[ext=m4a]/ba")
            .addOption("-x").addOption("--audio-format", if (mp3) "mp3" else "m4a")
            .addOption("--embed-metadata")
            .addOption("-o", File(outDir, "song.%(ext)s").absolutePath)
        if (mp3) r.addOption("--audio-quality", "0")
        if (withArt) {
            r.addOption("--embed-thumbnail")
            r.addOption("--convert-thumbnails", "jpg")
            // Square cover art, like a real album sleeve.
            r.addOption("--ppa", "ThumbnailsConvertor:-vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"")
        }
        return r
    }

    suspend fun ytdlpDownloadRequest(url: String, format: DownloadFormat, outDir: File): YoutubeDLRequest {
        awaitYtdlp()
        val r = base(url)
            .addOption("--no-playlist").addOption("--no-mtime").addOption("--embed-metadata")
            .addOption("-N", 4)
            .addOption("-o", File(outDir, "%(title).120B [%(id)s].%(ext)s").absolutePath)
        when (format) {
            DownloadFormat.M4A -> { r.addOption("-f", "ba[ext=m4a]/ba"); r.addOption("-x"); r.addOption("--audio-format", "m4a") }
            DownloadFormat.MP3 -> { r.addOption("-f", "ba"); r.addOption("-x"); r.addOption("--audio-format", "mp3"); r.addOption("--audio-quality", "0") }
            else -> {
                val h = format.height ?: 1080
                r.addOption("-f", "bv*[height<=$h][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]/b")
                r.addOption("--merge-output-format", "mp4")
            }
        }
        return r
    }

    // ---------------------------------------------------------------- maintenance

    suspend fun update(): String = withContext(Dispatchers.IO) {
        awaitYtdlp()
        status.value = "Updating yt-dlp"
        try {
            val channel = if (prefs.nightly) YoutubeDL.UpdateChannel.NIGHTLY else YoutubeDL.UpdateChannel.STABLE
            val result = YoutubeDL.updateYoutubeDL(app, channel)
            prefs.lastUpdateCheck = System.currentTimeMillis()
            version.value = YoutubeDL.version(app)
            when (result) {
                YoutubeDL.UpdateStatus.DONE -> "Updated yt-dlp to ${version.value}"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp is already up to date (${version.value})"
                else -> "Couldn't check for updates"
            }
        } finally { status.value = "Ready" }
    }

    fun clearCache() {
        feeds.clear(); streams.clear(); watches.clear()
    }

    fun friendly(t: Throwable): String {
        if (t is InnerTube.SessionExpired) return t.message!!
        val msg = t.message ?: return t.toString()
        val err = msg.lineSequence().lastOrNull { it.contains("ERROR:") } ?: msg.lineSequence().firstOrNull() ?: msg
        val clean = err.substringAfter("ERROR:").trim()
        return when {
            clean.contains("Sign in to confirm", true) -> "YouTube wants you signed in for this. Sign in from the You tab."
            clean.contains("Unable to resolve host", true) || clean.contains("Failed to resolve", true) ||
                clean.contains("timeout", true) || clean.contains("failed to connect", true) ->
                "You're offline or the connection is weak. Check your connection and try again."
            else -> clean.take(300)
        }
    }
}
