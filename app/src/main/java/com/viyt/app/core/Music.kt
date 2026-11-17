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

/** A Home mood chip ("Energize", "Relax", ...). */
data class MusicChip(val label: String, val params: String)

data class MusicHome(val chips: List<MusicChip>, val shelves: List<Shelf>)

/** A "Moods & genres" button. */
data class Mood(val label: String, val color: Long, val browseId: String, val params: String?)
data class MoodGroup(val title: String, val moods: List<Mood>)

/** Per-song extras from the watch panel: lyrics/related pages and your like. */
data class TrackMeta(val lyricsId: String?, val relatedId: String?, val liked: Boolean)

data class Lyrics(val text: String, val source: String?)

/** Album, playlist or artist page. */
data class MusicPage(
    val title: String,
    val subtitle: String?,
    val thumb: String?,
    val kind: MusicItem.Kind,
    val shelves: List<Shelf>,
    val channelId: String? = null,
    val subscribed: Boolean = false,
    /** Playlist id to save/like this album or playlist. */
    val playlistId: String? = null,
    val description: String? = null,
) {
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

    /** Home: first page plus two "load more" pages of shelves, and the mood chips. [params] = a chip. */
    suspend fun home(fresh: Boolean = false, params: String? = null): MusicHome = cached("home:$params", fresh) {
        val body = JSONObject().put("browseId", "FEmusic_home")
        if (params != null) body.put("params", params)
        val first = post("browse", body)
        val chips = ArrayList<MusicChip>()
        walk(first, "chipCloudChipRenderer") { c ->
            val label = text(c.opt("text")) ?: return@walk
            val p = findObj(c.optJSONObject("navigationEndpoint"), "browseEndpoint")?.optString("params")?.ifEmpty { null } ?: return@walk
            chips.add(MusicChip(label, p))
        }
        val out = ArrayList(shelves(first))
        var token = sectionToken(first)
        var n = 0
        while (token != null && n < 3) {
            val more = post("browse", JSONObject().put("continuation", token))
            out.addAll(shelves(more))
            token = sectionToken(more)
            n++
        }
        MusicHome(chips, out.filter { it.items.isNotEmpty() })
    }

    /** Explore's "Moods & genres" buttons. */
    suspend fun moods(fresh: Boolean = false): List<MoodGroup> = cached("moods", fresh) {
        val json = post("browse", JSONObject().put("browseId", "FEmusic_moods_and_genres"))
        val out = ArrayList<MoodGroup>()
        walk(json, "gridRenderer") { g ->
            val title = text(findObj(g.optJSONObject("header"), "title")) ?: ""
            val moods = ArrayList<Mood>()
            val arr = g.optJSONArray("items")
            if (arr != null) for (i in 0 until arr.length()) {
                val b2 = arr.optJSONObject(i)?.optJSONObject("musicNavigationButtonRenderer") ?: continue
                val be = findObj(b2.optJSONObject("clickCommand"), "browseEndpoint") ?: continue
                moods.add(Mood(text(b2.opt("buttonText")) ?: continue, b2.optJSONObject("solid")?.optLong("leftStripeColor") ?: 0xFF888888,
                    be.optString("browseId"), be.optString("params").ifEmpty { null }))
            }
            if (moods.isNotEmpty()) out.add(MoodGroup(title, moods))
        }
        out
    }

    suspend fun trackMeta(videoId: String): TrackMeta = cached("meta:$videoId", false, ttl = 60 * 60_000L) {
        val json = post("next", JSONObject().put("videoId", videoId).put("isAudioOnly", true).put("enablePersistentPlaylistPanel", true))
        var lyrics: String? = null
        var related: String? = null
        walk(findObj(json, "watchNextTabbedResultsRenderer"), "tabRenderer") { t ->
            val be = findObj(t.optJSONObject("endpoint"), "browseEndpoint") ?: return@walk
            val type = findString(be, "pageType").orEmpty()
            if (type.contains("LYRICS")) lyrics = be.optString("browseId")
            if (type.contains("RELATED")) related = be.optString("browseId")
        }
        val liked = findObj(json, "likeButtonRenderer")?.optString("likeStatus") == "LIKE"
        TrackMeta(lyrics, related, liked)
    }

    suspend fun lyrics(browseId: String): Lyrics? = cached("lyrics:$browseId", false, ttl = 24 * 3600_000L) {
        val d = findObj(post("browse", JSONObject().put("browseId", browseId)), "musicDescriptionShelfRenderer")
        val text = text(d?.opt("description"))
        if (text == null) Lyrics("", null) else Lyrics(text.replace("\r", ""), text(d?.opt("footer")))
    }.takeIf { it.text.isNotEmpty() }

    suspend fun related(browseId: String): List<Shelf> = cached("related:$browseId", false) {
        shelves(post("browse", JSONObject().put("browseId", browseId))).filter { it.items.isNotEmpty() }
    }

    // ------------------------------------------------------------------ your account (signed in)

    private fun requireSignIn() { if (!Engine.signedIn) throw IllegalStateException("Sign in from Library to do this") }

    suspend fun likeSong(videoId: String, on: Boolean) = withContext(Dispatchers.IO) {
        requireSignIn()
        post(if (on) "like/like" else "like/removelike", JSONObject().put("target", JSONObject().put("videoId", videoId)))
        cache.remove("meta:$videoId"); cache.keys.filter { it.startsWith("page:VLLM") || it.startsWith("page:FEmusic_liked_videos") }.forEach { cache.remove(it) }
        Unit
    }

    /** Save (like) an album or playlist to your library. */
    suspend fun savePlaylist(playlistId: String, on: Boolean) = withContext(Dispatchers.IO) {
        requireSignIn()
        post(if (on) "like/like" else "like/removelike", JSONObject().put("target", JSONObject().put("playlistId", playlistId)))
        cache.keys.filter { it.startsWith("page:FEmusic_liked") }.forEach { cache.remove(it) }
        Unit
    }

    /** Your own playlists, for "Add to playlist". */
    suspend fun myPlaylists(): List<MusicItem> = page("FEmusic_liked_playlists", fresh = true).shelves.flatMap { it.items }
        .filter { it.kind == MusicItem.Kind.PLAYLIST && it.id.startsWith("VL") && !it.id.startsWith("VLLM") && !it.id.startsWith("VLSE") }

    suspend fun addToPlaylist(playlistBrowseId: String, videoId: String) = withContext(Dispatchers.IO) {
        requireSignIn()
        val pid = playlistBrowseId.removePrefix("VL")
        post("browse/edit_playlist", JSONObject().put("playlistId", pid)
            .put("actions", JSONArray().put(JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId).put("dedupeOption", "DEDUPE_OPTION_SKIP"))))
        cache.remove("page:$playlistBrowseId:null")
        Unit
    }

    suspend fun createPlaylist(title: String, videoId: String?) = withContext(Dispatchers.IO) {
        requireSignIn()
        val body = JSONObject().put("title", title).put("privacyStatus", "PRIVATE")
        if (videoId != null) body.put("videoIds", JSONArray().put(videoId))
        post("playlist/create", body)
        cache.keys.filter { it.startsWith("page:FEmusic_liked_playlists") }.forEach { cache.remove(it) }
        Unit
    }

    suspend fun explore(fresh: Boolean = false): List<Shelf> = cached("explore", fresh) {
        shelves(post("browse", JSONObject().put("browseId", "FEmusic_explore"))).filter { it.items.isNotEmpty() }
    }

    suspend fun search(query: String, filter: Filter): List<MusicItem> = cached("search:${filter.name}:$query", false) {
        shelves(post("search", JSONObject().put("query", query).put("params", filter.params))).flatMap { it.items }
    }

    /** Album, playlist, artist, mood page (also Liked music "VLLM", library pages "FEmusic_*"). */
    suspend fun page(browseId: String, params: String? = null, fresh: Boolean = false): MusicPage = cached("page:$browseId:$params", fresh) {
        val body = JSONObject().put("browseId", browseId)
        if (params != null) body.put("params", params)
        val json = post("browse", body)
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
        val sb = findObj(header, "subscribeButtonRenderer")
        val playlistId = when {
            browseId.startsWith("VL") -> browseId.removePrefix("VL")
            else -> findString(header?.optJSONObject("buttons") ?: header?.optJSONArray("buttons"), "playlistId")
        }
        val desc = text(findObj(header, "musicDescriptionShelfRenderer")?.opt("description")) ?: text(header?.opt("description"))
        val sh = shelves(json).map { s ->
            if (kind == MusicItem.Kind.ALBUM) s.copy(items = s.items.map { if (it.thumb == null) it.copy(thumb = art, subtitle = it.subtitle ?: strap) else it }) else s
        }
        MusicPage(title, sub, art, kind, sh.filter { it.items.isNotEmpty() },
            channelId = sb?.optString("channelId")?.ifEmpty { null } ?: if (kind == MusicItem.Kind.ARTIST) browseId else null,
            subscribed = sb?.optBoolean("subscribed") ?: false,
            playlistId = playlistId, description = desc)
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

    internal fun radioToken(json: JSONObject): String? =
        findObj(json, "nextRadioContinuationData")?.optString("continuation")?.ifEmpty { null }
            ?: findObj(json.optJSONObject("continuationContents"), "nextContinuationData")?.optString("continuation")?.ifEmpty { null }

    internal fun panel(json: JSONObject): List<Track> {
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

    internal fun shelves(json: JSONObject): List<Shelf> {
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
