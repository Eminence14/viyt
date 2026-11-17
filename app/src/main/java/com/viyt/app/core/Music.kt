package com.viyt.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** A card or row in Music: a song, music video, album, playlist or artist. */
data class MusicItem(
    val kind: Kind,
    /** videoId for songs/videos, browseId for albums/playlists/artists. */
    val id: String,
    val title: String,
    val subtitle: String?,
    val thumb: String?,
    val duration: String? = null,
    val artistId: String? = null,
) {
    enum class Kind { SONG, VIDEO, ALBUM, PLAYLIST, ARTIST }
    val playable get() = kind == Kind.SONG || kind == Kind.VIDEO
    /** "Song • Burna Boy • 3M plays" -> "Burna Boy" */
    val artist: String?
        get() = subtitle?.split(" • ")?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() && it !in setOf("Song", "Video", "Single", "EP", "Album", "Playlist") }

    fun toTrack() = Track(id, title, artist, thumb, duration)
}

/** A row of items with a title ("Quick picks", "Top songs", ...). [list] = shown as song rows. */
data class Shelf(val title: String, val items: List<MusicItem>, val list: Boolean)

/** Album, playlist or artist page. */
data class MusicPage(val title: String, val subtitle: String?, val thumb: String?, val kind: MusicItem.Kind, val shelves: List<Shelf>) {
    val tracks get() = shelves.firstOrNull { s -> s.list && s.items.any { it.playable } }?.items?.filter { it.playable }.orEmpty()
}

/** One song in the play queue. */
data class Track(val videoId: String, val title: String, val artist: String?, val thumb: String?, val duration: String?)

data class RadioPage(val tracks: List<Track>, val next: String?)

/**
 * YouTube Music's own API (music.youtube.com, client WEB_REMIX). Same sign-in as YouTube.
 */
object Music {
    private const val ORIGIN = "https://music.youtube.com"
    private const val VERSION = "1.20260707.12.00"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    @Volatile private var visitor: String? = null

    enum class Filter(val label: String, val params: String) {
        SONGS("Songs", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"),
        VIDEOS("Videos", "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"),
        ALBUMS("Albums", "EgWKAQIYAWoKEAkQAxAEEAoQBQ%3D%3D"),
        ARTISTS("Artists", "EgWKAQIgAWoKEAkQAxAEEAoQBQ%3D%3D"),
        PLAYLISTS("Playlists", "EgeKAQQoAEABagoQAxAEEAoQCRAF"),
    }

    private fun post(endpoint: String, body: JSONObject, query: String = ""): JSONObject {
        val client = JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", VERSION)
            .put("hl", InnerTube.hl).put("gl", InnerTube.gl)
        visitor?.let { client.put("visitorData", it) }
        body.put("context", JSONObject().put("client", client))
        val b = Request.Builder()
            .url("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false$query")
            .post(body.toString().toRequestBody(JSON))
            .header("User-Agent", UA).header("Origin", ORIGIN).header("X-Origin", ORIGIN)
            .header("X-Youtube-Client-Name", "67").header("X-Youtube-Client-Version", VERSION)
        visitor?.let { b.header("X-Goog-Visitor-Id", it) }
        val cookies = if (Engine.signedIn) Engine.cookies.map() else null
        val sapisid = cookies?.get("SAPISID") ?: cookies?.get("__Secure-3PAPISID")
        if (cookies != null && sapisid != null) {
            val ts = System.currentTimeMillis() / 1000
            b.header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            b.header("Authorization", "SAPISIDHASH ${ts}_${sha1("$ts $sapisid $ORIGIN")}")
            b.header("X-Goog-AuthUser", "0")
        }
        val json = InnerTube.http.newCall(b.build()).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("YouTube Music answered HTTP ${r.code}")
            JSONObject(t)
        }
        json.optJSONObject("responseContext")?.optString("visitorData")?.ifEmpty { null }?.let { if (visitor == null) visitor = it }
        return json
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------------ cache

    private class Cached<T>(val v: T, val at: Long)
    private val cache = ConcurrentHashMap<String, Cached<Any>>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, fresh: Boolean, ttl: Long = 15 * 60_000L, load: () -> T): T {
        if (!fresh) cache[key]?.let { if (System.currentTimeMillis() - it.at < ttl) return it.v as T }
        val v = withContext(Dispatchers.IO) { load() }
        cache[key] = Cached(v, System.currentTimeMillis())
        return v
    }

    fun clear() = cache.clear()

    // ------------------------------------------------------------------ screens

    /** Home: first page plus two "load more" pages of shelves. */
    suspend fun home(fresh: Boolean = false): List<Shelf> = cached("home", fresh) {
        val first = post("browse", JSONObject().put("browseId", "FEmusic_home"))
        val out = ArrayList(shelves(first))
        var token = sectionToken(first)
        var n = 0
        while (token != null && n < 2) {
            val more = post("browse", JSONObject(), "&ctoken=$token&continuation=$token&type=next")
            out.addAll(shelves(more))
            token = sectionToken(more)
            n++
        }
        out.filter { it.items.isNotEmpty() }
    }

    suspend fun explore(fresh: Boolean = false): List<Shelf> = cached("explore", fresh) {
        shelves(post("browse", JSONObject().put("browseId", "FEmusic_explore"))).filter { it.items.isNotEmpty() }
    }

    suspend fun search(query: String, filter: Filter): List<MusicItem> = cached("search:${filter.name}:$query", false) {
        shelves(post("search", JSONObject().put("query", query).put("params", filter.params))).flatMap { it.items }
    }

    /** Album, playlist or artist page (also Liked songs "VLLM", Library "FEmusic_liked_playlists", "FEmusic_history"). */
    suspend fun page(browseId: String, fresh: Boolean = false): MusicPage = cached("page:$browseId", fresh) {
        val json = post("browse", JSONObject().put("browseId", browseId))
        val header = findObj(json, "musicResponsiveHeaderRenderer") ?: findObj(json, "musicImmersiveHeaderRenderer")
            ?: findObj(json, "musicVisualHeaderRenderer") ?: findObj(json, "musicDetailHeaderRenderer") ?: findObj(json, "musicHeaderRenderer")
        val title = text(header?.opt("title")) ?: ""
        val strap = text(header?.opt("straplineTextOne"))
        val sub = listOfNotNull(text(header?.opt("subtitle")), strap, text(header?.opt("secondSubtitle"))).joinToString(" · ").ifEmpty { null }
        val art = big(thumb(header?.opt("thumbnail") ?: header?.opt("foregroundThumbnail")))
        val kind = when {
            browseId.startsWith("MPRE") -> MusicItem.Kind.ALBUM
            browseId.startsWith("UC") -> MusicItem.Kind.ARTIST
            else -> MusicItem.Kind.PLAYLIST
        }
        // Album tracks carry no artwork of their own; use the album's.
        val sh = shelves(json).map { s ->
            if (kind == MusicItem.Kind.ALBUM) s.copy(items = s.items.map { if (it.thumb == null) it.copy(thumb = art, subtitle = it.subtitle ?: strap) else it }) else s
        }
        MusicPage(title, sub, art, kind, sh.filter { it.items.isNotEmpty() })
    }

    /** A radio (endless mix) seeded from a song, or the tracks of a playlist. */
    fun radio(videoId: String, playlistId: String? = null): RadioPage {
        val json = post("next", JSONObject().put("videoId", videoId)
            .put("playlistId", playlistId ?: "RDAMVM$videoId")
            .put("isAudioOnly", true).put("enablePersistentPlaylistPanel", true).put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL"))
        return RadioPage(panel(json), radioToken(json))
    }

    fun radioMore(token: String): RadioPage {
        val json = post("next", JSONObject().put("enablePersistentPlaylistPanel", true).put("isAudioOnly", true),
            "&ctoken=$token&continuation=$token&type=next")
        return RadioPage(panel(json), radioToken(json))
    }

    // ------------------------------------------------------------------ parsing

    private fun sectionToken(json: JSONObject): String? {
        val src = json.optJSONObject("continuationContents") ?: json.optJSONObject("contents") ?: return null
        val sl = findObj(src, "sectionListRenderer") ?: findObj(src, "sectionListContinuation") ?: src
        return findObj(sl.optJSONArray("continuations"), "nextContinuationData")?.optString("continuation")?.ifEmpty { null }
    }

    private fun radioToken(json: JSONObject): String? =
        findObj(json, "nextRadioContinuationData")?.optString("continuation")?.ifEmpty { null }
            ?: findObj(json.optJSONObject("continuationContents"), "nextContinuationData")?.optString("continuation")?.ifEmpty { null }

    private fun panel(json: JSONObject): List<Track> {
        val out = ArrayList<Track>()
        walk(json, "playlistPanelVideoRenderer") { r ->
            val id = r.optString("videoId").ifEmpty { return@walk }
            val by = r.optJSONObject("longBylineText")?.optJSONArray("runs")
            val artist = if (by != null) {
                val parts = ArrayList<String>()
                for (i in 0 until by.length()) {
                    val t = by.optJSONObject(i)?.optString("text").orEmpty()
                    if (t == " • ") break
                    parts.add(t)
                }
                parts.joinToString("").ifEmpty { null }
            } else text(r.opt("shortBylineText"))
            if (out.none { it.videoId == id }) out.add(Track(id, text(r.opt("title")) ?: "", artist, big(thumb(r.opt("thumbnail"))), text(r.opt("lengthText"))))
        }
        return out
    }

    private fun shelves(json: JSONObject): List<Shelf> {
        val out = ArrayList<Shelf>()
        fun go(n: Any?) {
            when (n) {
                is JSONObject -> {
                    val ks = n.keys()
                    while (ks.hasNext()) {
                        val k = ks.next()
                        val v = n.opt(k)
                        if (v !is JSONObject) { go(v); continue }
                        when (k) {
                            "musicCarouselShelfRenderer" -> {
                                val title = text(findObj(v.optJSONObject("header"), "title")) ?: ""
                                val items = items(v.optJSONArray("contents"))
                                out.add(Shelf(title, items, list = v.optJSONArray("contents")?.optJSONObject(0)?.has("musicResponsiveListItemRenderer") == true))
                            }
                            "musicShelfRenderer", "musicPlaylistShelfRenderer" ->
                                out.add(Shelf(text(v.opt("title")) ?: "", items(v.optJSONArray("contents")), list = true))
                            "gridRenderer" -> out.add(Shelf(text(findObj(v.optJSONObject("header"), "title")) ?: "", items(v.optJSONArray("items")), list = false))
                            "menu", "header", "responseContext", "frameworkUpdates" -> {}
                            else -> go(v)
                        }
                    }
                }
                is JSONArray -> for (i in 0 until n.length()) go(n.opt(i))
            }
        }
        go(json.optJSONObject("contents") ?: json.optJSONObject("continuationContents") ?: json)
        return out
    }

    private fun items(arr: JSONArray?): List<MusicItem> {
        if (arr == null) return emptyList()
        val out = ArrayList<MusicItem>()
        for (i in 0 until arr.length()) {
            val w = arr.optJSONObject(i) ?: continue
            val it = runCatching {
                w.optJSONObject("musicTwoRowItemRenderer")?.let { two(it) }
                    ?: w.optJSONObject("musicResponsiveListItemRenderer")?.let { row(it) }
            }.getOrNull()
            if (it != null) out.add(it)
        }
        return out
    }

    private fun kindOf(nav: JSONObject?): Pair<MusicItem.Kind, String>? {
        nav ?: return null
        nav.optJSONObject("watchEndpoint")?.let { w ->
            val id = w.optString("videoId").ifEmpty { return null }
            val type = findString(w, "musicVideoType")
            return (if (type == "MUSIC_VIDEO_TYPE_ATV" || type == null) MusicItem.Kind.SONG else MusicItem.Kind.VIDEO) to id
        }
        nav.optJSONObject("browseEndpoint")?.let { b ->
            val id = b.optString("browseId").ifEmpty { return null }
            val page = findString(b, "pageType").orEmpty()
            val k = when {
                page.contains("ARTIST") || page.contains("USER_CHANNEL") -> MusicItem.Kind.ARTIST
                page.contains("ALBUM") || page.contains("AUDIOBOOK") -> MusicItem.Kind.ALBUM
                else -> MusicItem.Kind.PLAYLIST
            }
            return k to id
        }
        return null
    }

    private fun two(r: JSONObject): MusicItem? {
        val (kind, id) = kindOf(r.optJSONObject("navigationEndpoint")) ?: return null
        return MusicItem(kind, id, text(r.opt("title")) ?: return null, text(r.opt("subtitle")), big(thumb(r.opt("thumbnailRenderer"))))
    }

    private fun row(r: JSONObject): MusicItem? {
        val cols = r.optJSONArray("flexColumns") ?: return null
        fun col(i: Int): JSONArray? = cols.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs")
        val title = col(0)?.let { runsText(it) } ?: return null
        val runs1 = col(1)
        var subtitle = runs1?.let { runsText(it) }
        val fixed = r.optJSONArray("fixedColumns")?.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
        var duration = text(fixed?.opt("text"))
        // Search rows end with " • 3:52"; pull it out as the duration.
        subtitle?.let { s ->
            val last = s.substringAfterLast(" • ")
            if (duration == null && last.matches(Regex("\\d{1,2}(:\\d{2}){1,2}"))) { duration = last; subtitle = s.substringBeforeLast(" • ") }
        }
        var artistId: String? = null
        if (runs1 != null) for (i in 0 until runs1.length()) {
            val nav = runs1.optJSONObject(i)?.optJSONObject("navigationEndpoint")
            if (findString(nav, "pageType")?.contains("ARTIST") == true) { artistId = nav?.optJSONObject("browseEndpoint")?.optString("browseId"); break }
        }
        val art = big(thumb(r.opt("thumbnail")))
        val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")?.ifEmpty { null }
            ?: findObj(r.optJSONObject("overlay"), "watchEndpoint")?.optString("videoId")?.ifEmpty { null }
        if (videoId != null) {
            val type = findString(r.optJSONObject("overlay"), "musicVideoType")
            val kind = if (type == null || type == "MUSIC_VIDEO_TYPE_ATV") MusicItem.Kind.SONG else MusicItem.Kind.VIDEO
            return MusicItem(kind, videoId, title, subtitle, art, duration, artistId)
        }
        val (kind, id) = kindOf(r.optJSONObject("navigationEndpoint")) ?: return null
        return MusicItem(kind, id, title, subtitle, art)
    }

    // ------------------------------------------------------------------ helpers

    /** Ask Google's image server for a bigger version of a thumbnail. */
    fun big(url: String?, size: Int = 544): String? {
        url ?: return null
        return when {
            url.contains("googleusercontent.com") || url.contains("ggpht.com") ->
                url.replace(Regex("=w\\d+-h\\d+.*$"), "=w$size-h$size-l90-rj").replace(Regex("=s\\d+.*$"), "=s$size")
            else -> url
        }
    }

    private fun runsText(runs: JSONArray) = buildString { for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty()) }.ifEmpty { null }

    private fun text(o: Any?): String? = when (o) {
        is String -> o.ifEmpty { null }
        is JSONObject -> o.optString("simpleText").ifEmpty { null } ?: o.optJSONArray("runs")?.let { runsText(it) }
        else -> null
    }

    private fun thumb(node: Any?): String? {
        val arr = findArr(node, "thumbnails") ?: return null
        var best: String? = null
        var w = -1
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            val u = t.optString("url")
            if (u.isNotEmpty() && t.optInt("width", 0) >= w) { w = t.optInt("width", 0); best = u }
        }
        return best?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun findArr(n: Any?, key: String): JSONArray? {
        when (n) {
            is JSONObject -> { n.optJSONArray(key)?.let { return it }; val ks = n.keys(); while (ks.hasNext()) findArr(n.opt(ks.next()), key)?.let { return it } }
            is JSONArray -> for (i in 0 until n.length()) findArr(n.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun findObj(n: Any?, key: String): JSONObject? {
        when (n) {
            is JSONObject -> { n.optJSONObject(key)?.let { return it }; val ks = n.keys(); while (ks.hasNext()) findObj(n.opt(ks.next()), key)?.let { return it } }
            is JSONArray -> for (i in 0 until n.length()) findObj(n.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun findString(n: Any?, key: String): String? {
        when (n) {
            is JSONObject -> {
                n.opt(key)?.let { if (it is String && it.isNotEmpty()) return it }
                val ks = n.keys(); while (ks.hasNext()) findString(n.opt(ks.next()), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until n.length()) findString(n.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun walk(n: Any?, key: String, f: (JSONObject) -> Unit) {
        when (n) {
            is JSONObject -> { val ks = n.keys(); while (ks.hasNext()) { val k = ks.next(); val v = n.opt(k); if (k == key && v is JSONObject) f(v) else walk(v, key, f) } }
            is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i), key, f)
        }
    }
}
