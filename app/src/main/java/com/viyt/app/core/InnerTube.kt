package com.viyt.app.core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * The fast path. Talks to YouTube's own JSON API directly over a pooled HTTP/2 connection,
 * so a list or a stream link costs one small request instead of starting yt-dlp (Python).
 * Anything this can't handle falls back to yt-dlp in Engine.
 */
object InnerTube {

    class SessionExpired : IOException("Your YouTube session expired. Sign in again from Settings.")

    private const val WEB_VERSION = "2.20260708.00.00"
    private const val WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    // A client that returns ready-to-play links: no JavaScript challenge, no extra tokens.
    private const val PLAYER_NAME = "VISIONOS"
    private const val PLAYER_ID = "101"
    private const val PLAYER_VERSION = "1.02"
    const val PLAYER_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"

    private const val ORIGIN = "https://www.youtube.com"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Open the connection to YouTube early so the first real request skips the TLS handshake. */
    fun warm() {
        runCatching {
            http.newCall(Request.Builder().url("$ORIGIN/generate_204").header("User-Agent", WEB_UA).build())
                .execute().close()
        }
    }

    // ------------------------------------------------------------------ requests

    private fun webContext() = JSONObject().put(
        "client", JSONObject()
            .put("clientName", "WEB").put("clientVersion", WEB_VERSION)
            .put("hl", "en").put("userAgent", WEB_UA)
    )

    private fun postWeb(endpoint: String, body: JSONObject, cookies: Map<String, String>?): JSONObject {
        body.put("context", webContext())
        val b = Request.Builder()
            .url("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody(JSON))
            .header("User-Agent", WEB_UA)
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", WEB_VERSION)
        val sapisid = cookies?.get("SAPISID") ?: cookies?.get("__Secure-3PAPISID")
        if (cookies != null && sapisid != null) {
            val ts = System.currentTimeMillis() / 1000
            val hash = sha1("$ts $sapisid $ORIGIN")
            b.header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            b.header("Authorization", "SAPISIDHASH ${ts}_$hash")
            b.header("X-Goog-AuthUser", "0")
        }
        val json = execute(b.build())
        if (sapisid != null) {
            val loggedOut = json.optJSONObject("responseContext")
                ?.optJSONObject("mainAppWebResponseContext")?.optBoolean("loggedOut", false) ?: false
            if (loggedOut) throw SessionExpired()
        }
        return json
    }

    private fun execute(req: Request): JSONObject =
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("YouTube answered HTTP ${r.code}")
            JSONObject(text)
        }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------------ lists

    /** A feed, playlist or channel page. Follows "load more" tokens until [limit] items or 4 pages. */
    fun browse(browseId: String, params: String?, limit: Int, cookies: Map<String, String>?): List<Item> {
        val body = JSONObject().put("browseId", browseId)
        if (params != null) body.put("params", params)
        return paged("browse", body, limit, cookies)
    }

    fun search(query: String, limit: Int, cookies: Map<String, String>?): List<Item> =
        paged("search", JSONObject().put("query", query), limit, cookies)

    private fun paged(endpoint: String, first: JSONObject, limit: Int, cookies: Map<String, String>?): List<Item> {
        val out = LinkedHashMap<String, Item>()
        var json = postWeb(endpoint, first, cookies)
        collect(json, out)
        var token = continuation(json)
        var pages = 1
        while (out.size < limit && token != null && pages < 4) {
            json = postWeb(endpoint, JSONObject().put("continuation", token), cookies)
            val before = out.size
            collect(json, out)
            if (out.size == before) break
            token = continuation(json)
            pages++
        }
        return out.values.take(limit)
    }

    private val VIDEO_KEYS = setOf(
        "videoRenderer", "compactVideoRenderer", "gridVideoRenderer", "playlistVideoRenderer",
        "videoWithContextRenderer", "playlistPanelVideoRenderer", "reelItemRenderer",
    )
    private val CHANNEL_KEYS = setOf("channelRenderer", "gridChannelRenderer", "compactChannelRenderer")
    private val PLAYLIST_KEYS = setOf(
        "playlistRenderer", "gridPlaylistRenderer", "compactPlaylistRenderer", "radioRenderer",
    )

    /** Walk the whole response and pick out anything that is a video, playlist or channel. */
    private fun collect(node: Any?, out: MutableMap<String, Item>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = node.opt(k)
                    if (v is JSONObject) {
                        val known = k in VIDEO_KEYS || k in CHANNEL_KEYS || k in PLAYLIST_KEYS ||
                            k == "lockupViewModel" || k == "shortsLockupViewModel"
                        if (known) {
                            val item = runCatching {
                                when (k) {
                                    in VIDEO_KEYS -> video(v)
                                    in CHANNEL_KEYS -> channel(v)
                                    in PLAYLIST_KEYS -> playlist(v)
                                    "lockupViewModel" -> lockup(v)
                                    else -> short(v)
                                }
                            }.getOrNull()
                            if (item != null) out.putIfAbsent(item.kind.name + item.id, item)
                        } else {
                            collect(v, out)
                        }
                    } else if (v is JSONArray) {
                        collect(v, out)
                    }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out)
        }
    }

    private fun videoItem(id: String, title: String, channel: String?, duration: Long?) = Item(
        id, title, "$ORIGIN/watch?v=$id", channel, duration,
        "https://i.ytimg.com/vi/$id/mqdefault.jpg", Item.Kind.VIDEO,
    )

    private fun video(v: JSONObject): Item? {
        val id = v.optString("videoId").ifEmpty { return null }
        val title = text(v.opt("title")) ?: text(v.opt("headline")) ?: return null
        val channel = text(v.opt("ownerText")) ?: text(v.opt("shortBylineText")) ?: text(v.opt("longBylineText"))
        val duration = v.optString("lengthSeconds").toLongOrNull()
            ?: clock(text(v.opt("lengthText")))
            ?: findClock(v.opt("thumbnailOverlays"))
        return videoItem(id, title, channel, duration)
    }

    private fun short(v: JSONObject): Item? {
        val id = firstString(v, "videoId") ?: return null
        val title = v.optJSONObject("overlayMetadata")?.optJSONObject("primaryText")?.optString("content")
            ?.ifEmpty { null } ?: "Short"
        return videoItem(id, title, "Shorts", null)
    }

    private fun channel(v: JSONObject): Item? {
        val id = v.optString("channelId").ifEmpty { return null }
        val title = text(v.opt("title")) ?: return null
        return Item(id, title, "$ORIGIN/channel/$id", null, null, thumb(v.opt("thumbnail")), Item.Kind.CHANNEL)
    }

    private fun playlist(v: JSONObject): Item? {
        val id = v.optString("playlistId").ifEmpty { return null }
        val title = text(v.opt("title")) ?: return null
        val owner = text(v.opt("shortBylineText")) ?: text(v.opt("longBylineText"))
        val t = thumb(v.opt("thumbnail")) ?: thumb(v.opt("thumbnails"))
        return Item(id, title, "$ORIGIN/playlist?list=$id", owner, null, t, Item.Kind.PLAYLIST)
    }

    /** The newer card format YouTube uses for videos and playlists. */
    private fun lockup(v: JSONObject): Item? {
        val id = v.optString("contentId").ifEmpty { return null }
        val type = v.optString("contentType")
        val meta = v.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val title = meta?.optJSONObject("title")?.optString("content")?.ifEmpty { null } ?: return null
        val by = firstString(meta.opt("metadata"), "content")
        return when {
            type.contains("VIDEO") -> videoItem(id, title, by, findClock(v.opt("contentImage")))
            type.contains("PLAYLIST") || type.contains("ALBUM") || type.contains("PODCAST") ->
                Item(id, title, "$ORIGIN/playlist?list=$id", by, null, thumb(v.opt("contentImage")), Item.Kind.PLAYLIST)
            type.contains("CHANNEL") ->
                Item(id, title, "$ORIGIN/channel/$id", null, null, thumb(v.opt("contentImage")), Item.Kind.CHANNEL)
            else -> null
        }
    }

    // ------------------------------------------------------------------ small JSON helpers

    private fun text(o: Any?): String? = when (o) {
        is String -> o.ifEmpty { null }
        is JSONObject -> o.optString("simpleText").ifEmpty { null }
            ?: o.optJSONArray("runs")?.let { runs ->
                buildString { for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty()) }
                    .ifEmpty { null }
            }
            ?: o.optString("content").ifEmpty { null }
        else -> null
    }

    private val CLOCK = Regex("^\\d{1,2}(:\\d{2}){1,2}$")

    private fun clock(s: String?): Long? {
        if (s == null || !CLOCK.matches(s.trim())) return null
        return s.trim().split(":").fold(0L) { acc, p -> acc * 60 + p.toLong() }
    }

    /** First "12:34"-style string anywhere under [node]. */
    private fun findClock(node: Any?): Long? {
        when (node) {
            is String -> return clock(node)
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) findClock(node.opt(keys.next()))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findClock(node.opt(i))?.let { return it }
        }
        return null
    }

    /** First non-empty string stored under [key] anywhere under [node]. */
    private fun firstString(node: Any?, key: String): String? {
        when (node) {
            is JSONObject -> {
                node.opt(key)?.let { if (it is String && it.isNotEmpty()) return it }
                val keys = node.keys()
                while (keys.hasNext()) firstString(node.opt(keys.next()), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) firstString(node.opt(i), key)?.let { return it }
        }
        return null
    }

    /** First image URL under [node] (works for both the old and new thumbnail shapes). */
    private fun thumb(node: Any?): String? {
        val u = firstString(node, "url") ?: return null
        return if (u.startsWith("//")) "https:$u" else u
    }

    private fun continuation(json: JSONObject): String? {
        val found = ArrayList<String>()
        fun walk(n: Any?) {
            when (n) {
                is JSONObject -> {
                    val keys = n.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = n.opt(k)
                        if (k == "continuationItemRenderer") firstString(v, "token")?.let(found::add)
                        else walk(v)
                    }
                }
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
            }
        }
        walk(json)
        return found.lastOrNull()
    }

    // ------------------------------------------------------------------ player

    private val VIDEO_ID = Regex("(?:[?&]v=|youtu\\.be/|/shorts/|/live/|/embed/)([A-Za-z0-9_-]{11})")

    fun videoId(url: String): String? = VIDEO_ID.find(url)?.groupValues?.get(1)

    /** Direct stream links for one video, in a single request. Throws if YouTube won't hand them out. */
    fun player(videoId: String, maxHeight: Int): Stream {
        val client = JSONObject()
            .put("clientName", PLAYER_NAME).put("clientVersion", PLAYER_VERSION)
            .put("deviceMake", "Apple").put("deviceModel", "RealityDevice17,1")
            .put("osName", "visionOS").put("osVersion", "26.5.23O471")
            .put("userAgent", PLAYER_UA).put("hl", "en")
        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", videoId)
            .put("contentCheckOk", true).put("racyCheckOk", true)
        val req = Request.Builder()
            .url("$ORIGIN/youtubei/v1/player?prettyPrint=false")
            .post(body.toString().toRequestBody(JSON))
            .header("User-Agent", PLAYER_UA)
            .header("Origin", ORIGIN)
            .header("X-Youtube-Client-Name", PLAYER_ID)
            .header("X-Youtube-Client-Version", PLAYER_VERSION)
            .build()
        val j = execute(req)
        val status = j.optJSONObject("playabilityStatus")
        if (status?.optString("status") != "OK") {
            throw IOException(status?.optString("reason")?.ifEmpty { null } ?: "Video unavailable on the fast path")
        }
        val details = j.optJSONObject("videoDetails")
        val title = details?.optString("title").orEmpty()
        val author = details?.optString("author")?.ifEmpty { null }
        val headers = mapOf("User-Agent" to PLAYER_UA)
        val sd = j.optJSONObject("streamingData") ?: throw IOException("No streams returned")

        val captions = captions(j)
        val heights = sortedSetOf<Int>(compareByDescending { it })
        var video: JSONObject? = null
        var audio: JSONObject? = null
        val adaptive = sd.optJSONArray("adaptiveFormats")
        if (adaptive != null) {
            for (i in 0 until adaptive.length()) {
                val f = adaptive.optJSONObject(i) ?: continue
                if (f.optString("url").isEmpty()) continue
                val mime = f.optString("mimeType")
                if (mime.startsWith("video/mp4") && mime.contains("avc1")) {
                    val h = f.optInt("height")
                    if (h > 0) heights.add(h)
                    if (h in 1..maxHeight) {
                        val v = video
                        if (v == null || h > v.optInt("height") ||
                            (h == v.optInt("height") && f.optInt("bitrate") > v.optInt("bitrate"))) video = f
                    }
                } else if (mime.startsWith("audio/mp4")) {
                    if (f.optBoolean("isDrc", false)) continue
                    val track = f.optJSONObject("audioTrack")
                    if (track != null && !track.optBoolean("audioIsDefault", false)) continue
                    val a = audio
                    if (a == null || f.optInt("bitrate") > a.optInt("bitrate")) audio = f
                }
            }
        }
        // Lowest available quality still plays when nothing fits under the limit.
        if (video == null && adaptive != null && heights.isNotEmpty()) {
            val low = heights.last()
            for (i in 0 until adaptive.length()) {
                val f = adaptive.optJSONObject(i) ?: continue
                if (f.optString("mimeType").let { it.startsWith("video/mp4") && it.contains("avc1") } &&
                    f.optInt("height") == low && f.optString("url").isNotEmpty()) { video = f; break }
            }
        }
        val hList = heights.toList()
        val v = video
        val a = audio
        if (v != null && a != null) {
            return Stream(title, author, v.getString("url"), a.getString("url"), headers,
                v.optInt("height").takeIf { it > 0 }, hls = false, fast = true,
                captions = captions, heights = hList, videoId = videoId)
        }
        val hls = sd.optString("hlsManifestUrl")
        if (hls.isNotEmpty()) return Stream(title, author, hls, null, headers, null, hls = true, fast = true,
            captions = captions, heights = hList, videoId = videoId)
        val muxed = sd.optJSONArray("formats")
        if (muxed != null) {
            for (i in muxed.length() - 1 downTo 0) {
                val f = muxed.optJSONObject(i) ?: continue
                if (f.optString("url").isNotEmpty() && f.optInt("height") <= maxHeight) {
                    return Stream(title, author, f.getString("url"), null, headers,
                        f.optInt("height").takeIf { it > 0 }, hls = false, fast = true,
                        captions = captions, heights = hList, videoId = videoId)
                }
            }
        }
        throw IOException("No directly playable stream")
    }

    /** Subtitle tracks from a player response, as WebVTT links. Auto-generated tracks are listed last. */
    private fun captions(j: JSONObject): List<Caption> {
        val tracks = j.optJSONObject("captions")?.optJSONObject("playerCaptionsTracklistRenderer")
            ?.optJSONArray("captionTracks") ?: return emptyList()
        val out = ArrayList<Caption>()
        for (i in 0 until tracks.length()) {
            val t = tracks.optJSONObject(i) ?: continue
            val base = t.optString("baseUrl")
            if (base.isEmpty()) continue
            val lang = t.optString("languageCode").ifEmpty { "und" }
            val auto = t.optString("kind") == "asr"
            val name = text(t.opt("name")) ?: lang
            val url = (if (base.startsWith("/")) ORIGIN + base else base).replace(Regex("&fmt=[^&]*"), "") + "&fmt=vtt"
            out.add(Caption("cc_${i}_$lang", if (auto && !name.contains("auto", true)) "$name (auto-generated)" else name, lang, url, auto))
        }
        return out.sortedBy { it.auto }
    }

    /** "Up next" for a video: one request to the same endpoint YouTube's watch page uses. */
    fun next(videoId: String, cookies: Map<String, String>?): List<Item> {
        val json = postWeb("next", JSONObject().put("videoId", videoId), cookies)
        val out = LinkedHashMap<String, Item>()
        collect(json.optJSONObject("contents")?.optJSONObject("twoColumnWatchNextResults")
            ?.optJSONObject("secondaryResults") ?: json, out)
        return out.values.filter { it.id != videoId }.take(30)
    }

    /** Turn a youtube.com/@handle (or any channel URL) into a browse id. */
    fun resolveBrowseId(url: String, cookies: Map<String, String>?): String? {
        val json = postWeb("navigation/resolve_url", JSONObject().put("url", url), cookies)
        return firstString(json.optJSONObject("endpoint"), "browseId")
    }
}
