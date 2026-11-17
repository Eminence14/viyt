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
 * viyt's engine, in two layers:
 *  1. Fast path: InnerTube, YouTube's own JSON API over one reused HTTP/2 connection (~0.2 to 0.6 s per call).
 *  2. Fallback: yt-dlp (Python), only when the fast path can't answer, and for MP3 downloads.
 * Results are cached in memory so going back and forth is instant.
 */
object Engine {
    lateinit var prefs: Prefs
        private set
    lateinit var cookies: Cookies
        private set
    private lateinit var app: Context

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** yt-dlp status. The fast path doesn't wait for it. */
    val status = MutableStateFlow("Ready")
    val ytdlpReady = MutableStateFlow(false)
    val version = MutableStateFlow<String?>(null)
    val failed = MutableStateFlow<String?>(null)

    private class Cached<T>(val value: T, val at: Long)
    private val lists = ConcurrentHashMap<String, Cached<List<Item>>>()
    private val streams = ConcurrentHashMap<String, Cached<Stream>>()
    private val pending = ConcurrentHashMap<String, Deferred<Stream>>()

    private const val LIST_TTL = 10 * 60_000L
    private const val STREAM_TTL = 4 * 3600_000L // YouTube stream links last about 6 hours

    fun start(context: Context) {
        app = context.applicationContext
        prefs = Prefs(app)
        cookies = Cookies(app)
        scope.launch { InnerTube.warm() }
        // yt-dlp unpacks Python on first run; do it in the background, never in the way of the UI.
        scope.launch {
            try {
                YoutubeDL.init(app)
                FFmpeg.getInstance().init(app)
                version.value = YoutubeDL.version(app)
                ytdlpReady.value = true
                if (System.currentTimeMillis() - prefs.lastUpdateCheck > 12 * 3600_000L) {
                    delay(30_000) // let the app settle first
                    runCatching { update() }
                }
            } catch (t: Throwable) {
                failed.value = t.message ?: t.toString()
            }
        }
    }

    private suspend fun awaitYtdlp() {
        ytdlpReady.first { it }
    }

    val jsRuntime: File? get() = File(app.applicationInfo.nativeLibraryDir, "libqjs.so").takeIf { it.exists() }

    private fun cookieMap(): Map<String, String>? = if (cookies.isSignedIn) cookies.map() else null

    // ---------------------------------------------------------------- lists

    /** Cached copy if we have one (used to paint screens instantly). */
    fun cached(key: String): List<Item>? = lists[key]?.value

    private suspend fun cachedList(key: String, fresh: Boolean, load: suspend () -> List<Item>): List<Item> {
        if (!fresh) lists[key]?.let { if (System.currentTimeMillis() - it.at < LIST_TTL) return it.value }
        val items = load()
        lists[key] = Cached(items, System.currentTimeMillis())
        return items
    }

    suspend fun feed(feed: Feed, fresh: Boolean = false): List<Item> =
        cachedList("feed:${feed.name}", fresh) {
            withContext(Dispatchers.IO) {
                fastOr({ InnerTube.browse(feed.browseId, null, 100, cookieMap()) }) { ytdlpList(feed.ytdlpUrl, 100) }
            }
        }

    suspend fun search(query: String): List<Item> =
        cachedList("search:$query", false) {
            withContext(Dispatchers.IO) {
                fastOr({ InnerTube.search(query, 40, cookieMap()) }) { ytdlpList("ytsearch40:$query", 40) }
            }
        }

    /** A playlist or channel URL. */
    suspend fun list(url: String, fresh: Boolean = false): List<Item> =
        cachedList("url:$url", fresh) {
            withContext(Dispatchers.IO) {
                fastOr({
                    val c = cookieMap()
                    val listId = Regex("[?&]list=([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)
                    val channelId = Regex("/channel/(UC[A-Za-z0-9_-]{22})").find(url)?.groupValues?.get(1)
                    when {
                        listId != null -> InnerTube.browse("VL$listId", null, 200, c)
                        channelId != null -> InnerTube.browse(channelId, CHANNEL_VIDEOS, 120, c)
                        url.contains("/@") || url.contains("/c/") || url.contains("/user/") -> {
                            val id = InnerTube.resolveBrowseId(url.substringBefore("/videos"), c)
                                ?: throw IllegalStateException("Channel not found")
                            InnerTube.browse(id, CHANNEL_VIDEOS, 120, c)
                        }
                        else -> throw IllegalStateException("Not a list URL")
                    }
                }) { ytdlpList(url, 200) }
            }
        }

    suspend fun upNext(videoId: String): List<Item> =
        cachedList("next:$videoId", false) {
            withContext(Dispatchers.IO) { runCatching { InnerTube.next(videoId, cookieMap()) }.getOrDefault(emptyList()) }
        }

    /** Channel "Videos" tab parameter. */
    private const val CHANNEL_VIDEOS = "EgZ2aWRlb3PyBgQKAjoA"

    /** Try the fast path; on anything but an expired session, fall back to yt-dlp. */
    private suspend fun <T> fastOr(fast: () -> T, slow: suspend () -> T): T =
        try {
            fast()
        } catch (e: InnerTube.SessionExpired) {
            throw e
        } catch (t: Throwable) {
            slow()
        }

    // ---------------------------------------------------------------- streaming

    /**
     * Stream links for the player. Cached for hours, and de-duplicated, so calling [prefetch]
     * when a row is pressed makes the player open with the link already in hand.
     */
    suspend fun resolve(url: String, height: Int = prefs.streamHeight): Stream {
        val key = "$height|$url"
        streams[key]?.let { if (System.currentTimeMillis() - it.at < STREAM_TTL) return it.value }
        val job = pending.getOrPut(key) {
            scope.async {
                try {
                    val id = InnerTube.videoId(url)
                    val s = if (id != null) {
                        try {
                            InnerTube.player(id, height)
                        } catch (t: Throwable) {
                            ytdlpResolve(url, height)
                        }
                    } else ytdlpResolve(url, height)
                    streams[key] = Cached(s, System.currentTimeMillis())
                    s
                } finally {
                    pending.remove(key)
                }
            }
        }
        return job.await()
    }

    fun prefetch(url: String) {
        if (InnerTube.videoId(url) == null) return
        scope.launch { runCatching { resolve(url) } }
    }

    // ---------------------------------------------------------------- yt-dlp fallback

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
        val req = base(url).addOption("--flat-playlist").addOption("-J").addOption("--playlist-end", limit)
        return parseEntries(JSONObject(run(req)))
    }

    private suspend fun ytdlpResolve(url: String, h: Int): Stream {
        awaitYtdlp()
        val fmt = "bv*[height<=$h][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]/b"
        val j = JSONObject(run(base(url).addOption("-J").addOption("--no-playlist").addOption("-f", fmt)))
        val title = j.optString("title")
        val channel = j.optString("channel").ifEmpty { j.optString("uploader") }.ifEmpty { null }
        val caps = ytdlpCaptions(j)
        val heights = listOf(1080, 720, 480, 360)
        val rf = j.optJSONArray("requested_formats")
        return if (rf != null && rf.length() >= 2) {
            var v: JSONObject? = null
            var a: JSONObject? = null
            for (i in 0 until rf.length()) {
                val f = rf.getJSONObject(i)
                if (f.optString("vcodec") != "none" && v == null) v = f else a = f
            }
            val video = v ?: rf.getJSONObject(0)
            Stream(title, channel, video.getString("url"), a?.optString("url"), headers(video.optJSONObject("http_headers")),
                video.optInt("height").takeIf { it > 0 }, captions = caps, heights = heights, videoId = j.optString("id"))
        } else {
            Stream(title, channel, j.getString("url"), null, headers(j.optJSONObject("http_headers")),
                j.optInt("height").takeIf { it > 0 }, captions = caps, heights = heights, videoId = j.optString("id"))
        }
    }

    private fun ytdlpCaptions(j: JSONObject): List<Caption> {
        val out = ArrayList<Caption>()
        fun add(o: JSONObject?, auto: Boolean, only: String?) {
            o ?: return
            o.keys().forEach { lang ->
                if (only != null && lang != only && lang != "$only-orig") return@forEach
                val arr = o.optJSONArray(lang) ?: return@forEach
                for (i in 0 until arr.length()) {
                    val f = arr.optJSONObject(i) ?: continue
                    if (f.optString("ext") == "vtt") {
                        val name = f.optString("name").ifEmpty { lang }
                        out.add(Caption("cc_${out.size}_$lang", if (auto) "$name (auto-generated)" else name, lang, f.optString("url"), auto))
                        break
                    }
                }
            }
        }
        add(j.optJSONObject("subtitles"), false, null)
        add(j.optJSONObject("automatic_captions"), true, j.optString("language").ifEmpty { "en" })
        return out
    }

    private fun headers(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val m = HashMap<String, String>()
        o.keys().forEach { k -> m[k] = o.optString(k) }
        return m
    }

    private fun parseEntries(root: JSONObject): List<Item> {
        val arr = root.optJSONArray("entries") ?: return emptyList()
        val out = ArrayList<Item>(arr.length())
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val nested = e.optJSONArray("entries")
            if (nested != null) {
                for (k in 0 until nested.length()) nested.optJSONObject(k)?.let { toItem(it) }?.let(out::add)
            } else toItem(e)?.let(out::add)
        }
        return out
    }

    private fun toItem(e: JSONObject): Item? {
        val id = e.optString("id")
        if (id.isEmpty()) return null
        var url = e.optString("url").ifEmpty { e.optString("webpage_url") }
        val kind = when {
            url.contains("list=") || url.contains("/playlist") -> Item.Kind.PLAYLIST
            e.optString("ie_key") == "YoutubeTab" || url.contains("/channel/") || url.contains("/@") -> Item.Kind.CHANNEL
            else -> Item.Kind.VIDEO
        }
        if (kind == Item.Kind.VIDEO && !url.startsWith("http")) url = "https://www.youtube.com/watch?v=$id"
        if (url.isEmpty()) return null
        val duration = if (e.has("duration") && !e.isNull("duration")) e.optDouble("duration").toLong() else null
        val thumb = pickThumb(e.optJSONArray("thumbnails"))
            ?: if (kind == Item.Kind.VIDEO) "https://i.ytimg.com/vi/$id/mqdefault.jpg" else null
        return Item(id, e.optString("title").ifEmpty { id }, url,
            e.optString("channel").ifEmpty { e.optString("uploader") }.ifEmpty { null }, duration, thumb, kind)
    }

    private fun pickThumb(arr: JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            var u = t.optString("url")
            if (u.isEmpty()) continue
            if (u.startsWith("//")) u = "https:$u"
            val w = t.optInt("width", 0)
            val score = if (w == 0) 10_000 else kotlin.math.abs(w - 480)
            if (score <= bestScore) { bestScore = score; best = u }
        }
        return best
    }

    // ---------------------------------------------------------------- downloads (yt-dlp path)

    suspend fun ytdlpDownloadRequest(url: String, format: DownloadFormat, outDir: File): YoutubeDLRequest {
        awaitYtdlp()
        val r = base(url)
            .addOption("--no-playlist")
            .addOption("--no-mtime")
            .addOption("--embed-metadata")
            .addOption("-N", 4) // fetch fragments in parallel
            .addOption("-o", File(outDir, "%(title).120B [%(id)s].%(ext)s").absolutePath)
        when (format) {
            DownloadFormat.M4A -> {
                r.addOption("-f", "ba[ext=m4a]/ba"); r.addOption("-x"); r.addOption("--audio-format", "m4a")
            }
            DownloadFormat.MP3 -> {
                r.addOption("-f", "ba"); r.addOption("-x"); r.addOption("--audio-format", "mp3"); r.addOption("--audio-quality", "0")
            }
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
        } finally {
            status.value = "Ready"
        }
    }

    fun clearCache() {
        lists.clear()
        streams.clear()
    }

    fun friendly(t: Throwable): String {
        if (t is InnerTube.SessionExpired) return t.message!!
        val msg = t.message ?: return t.toString()
        val err = msg.lineSequence().lastOrNull { it.contains("ERROR:") } ?: msg.lineSequence().firstOrNull() ?: msg
        val clean = err.substringAfter("ERROR:").trim()
        return when {
            clean.contains("Sign in to confirm", true) ->
                "YouTube wants you signed in for this. Sign in from Settings, then try again."
            clean.contains("Unable to resolve host", true) || clean.contains("Failed to resolve", true) ||
                clean.contains("timeout", true) ->
                "Network problem. Check your connection and try again."
            else -> clean.take(300)
        }
    }
}
