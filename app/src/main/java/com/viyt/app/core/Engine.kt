package com.viyt.app.core

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * viyt's engine: a thin layer over yt-dlp.
 * Everything YouTube-related (search, your library, stream links, downloads) goes through here.
 */
object Engine {
    lateinit var prefs: Prefs
        private set
    lateinit var cookies: Cookies
        private set
    private lateinit var app: Context

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Human-readable engine status for the UI. */
    val status = MutableStateFlow("Starting engine")
    val ready = MutableStateFlow(false)
    val version = MutableStateFlow<String?>(null)
    val failed = MutableStateFlow<String?>(null)

    private val listCache = HashMap<String, List<Item>>()

    fun start(context: Context) {
        app = context.applicationContext
        prefs = Prefs(app)
        cookies = Cookies(app)
        scope.launch {
            try {
                YoutubeDL.init(app)
                FFmpeg.getInstance().init(app)
                version.value = YoutubeDL.version(app)
                ready.value = true
                status.value = "Ready"
                // Keep yt-dlp fresh: YouTube changes often, and an old yt-dlp is the #1 cause of failures.
                if (System.currentTimeMillis() - prefs.lastUpdateCheck > 6 * 3600_000L) {
                    status.value = "Updating yt-dlp"
                    runCatching { update() }
                    status.value = "Ready"
                }
            } catch (t: Throwable) {
                failed.value = t.message ?: t.toString()
                status.value = "Engine failed to start"
            }
        }
    }

    private suspend fun awaitReady() {
        ready.first { it }
    }

    /** True when the bundled QuickJS engine is present (needed for full YouTube support). */
    val jsRuntime: File? get() = File(app.applicationInfo.nativeLibraryDir, "libqjs.so").takeIf { it.exists() }

    /** Options every yt-dlp call gets. */
    private fun base(url: String, useCookies: Boolean = true): YoutubeDLRequest {
        val r = YoutubeDLRequest(url)
        r.addOption("--cache-dir", File(app.filesDir, "ytdlp-cache").absolutePath)
        jsRuntime?.let {
            r.addOption("--js-runtimes", "quickjs:${it.absolutePath}")
            r.addOption("--remote-components", "ejs:github")
        }
        if (useCookies && cookies.isSignedIn) r.addOption("--cookies", cookies.file.absolutePath)
        return r
    }

    private fun run(req: YoutubeDLRequest): String =
        YoutubeDL.execute(req, null, null).out

    // ---------------------------------------------------------------- lists

    /** Load any YouTube list (feed, playlist, channel, search) without resolving each video. */
    suspend fun list(url: String, limit: Int = 60, fresh: Boolean = false): List<Item> =
        withContext(Dispatchers.IO) {
            if (!fresh) listCache[url]?.let { return@withContext it }
            awaitReady()
            val req = base(url)
                .addOption("--flat-playlist")
                .addOption("-J")
                .addOption("--playlist-end", limit)
            val items = parseEntries(JSONObject(run(req)))
            listCache[url] = items
            items
        }

    suspend fun search(query: String): List<Item> = list("ytsearch40:$query", 40)

    suspend fun feed(feed: Feed, fresh: Boolean = false) = list(feed.url, 80, fresh)

    private fun parseEntries(root: JSONObject): List<Item> {
        val arr = root.optJSONArray("entries") ?: return emptyList()
        val out = ArrayList<Item>(arr.length())
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            // Some feeds group videos into shelves; flatten them.
            val nested = e.optJSONArray("entries")
            if (nested != null) {
                for (j in 0 until nested.length()) nested.optJSONObject(j)?.let { toItem(it) }?.let(out::add)
            } else {
                toItem(e)?.let(out::add)
            }
        }
        return out
    }

    private fun toItem(e: JSONObject): Item? {
        val id = e.optString("id")
        if (id.isEmpty()) return null
        var url = e.optString("url").ifEmpty { e.optString("webpage_url") }
        val ie = e.optString("ie_key")
        val kind = when {
            url.contains("list=") || url.contains("/playlist") -> Item.Kind.PLAYLIST
            ie == "YoutubeTab" || url.contains("/channel/") || url.contains("/@") -> Item.Kind.CHANNEL
            else -> Item.Kind.VIDEO
        }
        if (kind == Item.Kind.VIDEO && !url.startsWith("http")) url = "https://www.youtube.com/watch?v=$id"
        if (url.isEmpty()) return null
        val title = e.optString("title").ifEmpty { id }
        val channel = e.optString("channel").ifEmpty { e.optString("uploader") }.ifEmpty { null }
        val duration = if (e.has("duration") && !e.isNull("duration")) e.optDouble("duration").toLong() else null
        val thumb = pickThumb(e.optJSONArray("thumbnails"))
            ?: if (kind == Item.Kind.VIDEO) "https://i.ytimg.com/vi/$id/mqdefault.jpg" else null
        return Item(id, title, url, channel, duration, thumb, kind)
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

    // ---------------------------------------------------------------- streaming

    /** Get direct stream links for the player. Video and audio come separately above 360p. */
    suspend fun resolve(url: String): Stream = withContext(Dispatchers.IO) {
        awaitReady()
        val h = prefs.streamHeight
        val fmt = "bv*[height<=$h][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]/b"
        val req = base(url).addOption("-J").addOption("--no-playlist").addOption("-f", fmt)
        val j = JSONObject(run(req))
        val title = j.optString("title")
        val channel = j.optString("channel").ifEmpty { j.optString("uploader") }.ifEmpty { null }
        val rf = j.optJSONArray("requested_formats")
        if (rf != null && rf.length() >= 2) {
            var v: JSONObject? = null
            var a: JSONObject? = null
            for (i in 0 until rf.length()) {
                val f = rf.getJSONObject(i)
                if (f.optString("vcodec") != "none" && v == null) v = f else a = f
            }
            val video = v ?: rf.getJSONObject(0)
            Stream(title, channel, video.getString("url"), a?.optString("url"),
                headers(video.optJSONObject("http_headers")), video.optInt("height").takeIf { it > 0 })
        } else {
            Stream(title, channel, j.getString("url"), null,
                headers(j.optJSONObject("http_headers")), j.optInt("height").takeIf { it > 0 })
        }
    }

    private fun headers(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val m = HashMap<String, String>()
        o.keys().forEach { k -> m[k] = o.optString(k) }
        return m
    }

    // ---------------------------------------------------------------- downloads

    fun downloadRequest(url: String, format: DownloadFormat, outDir: File): YoutubeDLRequest {
        val r = base(url)
            .addOption("--no-playlist")
            .addOption("--no-mtime")
            .addOption("--embed-metadata")
            .addOption("-o", File(outDir, "%(title).120B [%(id)s].%(ext)s").absolutePath)
        fun video(h: Int) {
            r.addOption("-f", "bv*[height<=$h][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]/b")
            r.addOption("--merge-output-format", "mp4")
        }
        when (format) {
            DownloadFormat.BEST -> video(1080)
            DownloadFormat.P720 -> video(720)
            DownloadFormat.P480 -> video(480)
            DownloadFormat.P360 -> video(360)
            DownloadFormat.M4A -> {
                r.addOption("-f", "ba[ext=m4a]/ba")
                r.addOption("-x")
                r.addOption("--audio-format", "m4a")
            }
            DownloadFormat.MP3 -> {
                r.addOption("-f", "ba")
                r.addOption("-x")
                r.addOption("--audio-format", "mp3")
                r.addOption("--audio-quality", "0")
            }
        }
        return r
    }

    suspend fun awaitEngine() = awaitReady()

    // ---------------------------------------------------------------- maintenance

    suspend fun update(): String = withContext(Dispatchers.IO) {
        awaitReady()
        val channel = if (prefs.nightly) YoutubeDL.UpdateChannel.NIGHTLY else YoutubeDL.UpdateChannel.STABLE
        val result = YoutubeDL.updateYoutubeDL(app, channel)
        prefs.lastUpdateCheck = System.currentTimeMillis()
        version.value = YoutubeDL.version(app)
        listCache.clear()
        when (result) {
            YoutubeDL.UpdateStatus.DONE -> "Updated yt-dlp to ${version.value}"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp is already up to date (${version.value})"
            else -> "Couldn't check for updates"
        }
    }

    fun clearCache() = listCache.clear()

    /** Turn a yt-dlp exception into one readable line. */
    fun friendly(t: Throwable): String {
        val msg = t.message ?: return t.toString()
        val err = msg.lineSequence().lastOrNull { it.contains("ERROR:") } ?: msg.lineSequence().firstOrNull() ?: msg
        val clean = err.substringAfter("ERROR:").trim()
        return when {
            clean.contains("Sign in to confirm", true) ->
                "YouTube wants you signed in for this. Sign in from Settings, then try again."
            clean.contains("cookies", true) && clean.contains("no longer valid", true) ->
                "Your YouTube session expired. Sign in again from Settings."
            clean.contains("Unable to download API page", true) || clean.contains("Failed to resolve", true) ->
                "Network problem. Check your connection and try again."
            else -> clean.take(300)
        }
    }
}
