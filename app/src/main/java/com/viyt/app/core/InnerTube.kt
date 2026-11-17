package com.viyt.app.core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * The fast path: YouTube's own JSON API (the same one youtube.com and the YouTube apps use),
 * over one reused HTTP/2 connection. Each screen costs one request of 0.2 to 0.9 s.
 */
object InnerTube {

    class SessionExpired : IOException("Your YouTube session expired. Sign in again from You > Settings.")

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

    /** Region/language for feeds. */
    var gl: String = "NG"
    var hl: String = "en"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // ------------------------------------------------------------------ visitor id

    /**
     * YouTube's anonymous visitor ID. The real apps send one with every request; without it YouTube
     * soon answers "Sign in to confirm you're not a bot".
     */
    @Volatile var visitorData: String? = null

    private fun visitor(refresh: Boolean = false): String? {
        if (!refresh) visitorData?.let { return it }
        val body = JSONObject().put("context", webContext(withVisitor = false))
        val req = Request.Builder()
            .url("$ORIGIN/youtubei/v1/visitor_id?prettyPrint=false")
            .post(body.toString().toRequestBody(JSON))
            .header("User-Agent", WEB_UA).header("Origin", ORIGIN)
            .build()
        val vd = runCatching { execute(req).optJSONObject("responseContext")?.optString("visitorData")?.ifEmpty { null } }.getOrNull()
        if (vd != null) visitorData = vd
        return visitorData
    }

    /** Open the connection and get a visitor ID early, so the first real request is fast. */
    fun warm() {
        runCatching { visitor() }
    }

    // ------------------------------------------------------------------ requests

    private fun webContext(withVisitor: Boolean = true): JSONObject {
        val client = JSONObject()
            .put("clientName", "WEB").put("clientVersion", WEB_VERSION)
            .put("hl", hl).put("gl", gl).put("userAgent", WEB_UA)
        if (withVisitor) visitor()?.let { client.put("visitorData", it) }
        return JSONObject().put("client", client)
    }

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
        visitorData?.let { b.header("X-Goog-Visitor-Id", it) }
        val sapisid = cookies?.get("SAPISID") ?: cookies?.get("__Secure-3PAPISID")
        if (cookies != null && sapisid != null) {
            val ts = System.currentTimeMillis() / 1000
            b.header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            b.header("Authorization", "SAPISIDHASH ${ts}_${sha1("$ts $sapisid $ORIGIN")}")
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

    /** First page of a feed, with its filter chips (Home has them). */
    fun browsePage(browseId: String, params: String?, limit: Int, cookies: Map<String, String>?): Feed {
        val body = JSONObject().put("browseId", browseId)
        if (params != null) body.put("params", params)
        val first = postWeb("browse", body, cookies)
        return Feed(pagedFrom(first, "browse", limit, cookies), chips(first))
    }

    fun browse(browseId: String, params: String?, limit: Int, cookies: Map<String, String>?): List<Item> =
        browsePage(browseId, params, limit, cookies).items

    /** Items behind a chip or any continuation token. */
    fun continueWith(token: String, limit: Int, cookies: Map<String, String>?): List<Item> =
        pagedFrom(postWeb("browse", JSONObject().put("continuation", token), cookies), "browse", limit, cookies)

    fun search(query: String, limit: Int, cookies: Map<String, String>?): List<Item> =
        pagedFrom(postWeb("search", JSONObject().put("query", query), cookies), "search", limit, cookies)

    private fun pagedFrom(first: JSONObject, endpoint: String, limit: Int, cookies: Map<String, String>?): List<Item> {
        val out = LinkedHashMap<String, Item>()
        collect(first, out)
        var token = continuation(first)
        var pages = 1
        while (out.size < limit && token != null && pages < 4) {
            val json = postWeb(endpoint, JSONObject().put("continuation", token), cookies)
            val before = out.size
            collect(json, out)
            if (out.size == before) break
            token = continuation(json)
            pages++
        }
        return out.values.take(limit)
    }

    private fun chips(json: JSONObject): List<Chip> {
        val out = ArrayList<Chip>()
        walkKey(json, "chipCloudChipRenderer") { c ->
            val label = text(c.opt("text")) ?: return@walkKey
            val token = firstString(c.optJSONObject("navigationEndpoint"), "token")
            if (out.none { it.label == label }) out.add(Chip(label, if (c.optBoolean("isSelected")) null else token))
        }
        return out
    }

    // Keys that never hold feed cards; skipping them keeps parsing fast and avoids menu junk.
    private val SKIP = setOf(
        "topbar", "frameworkUpdates", "onResponseReceivedActions", "responseContext", "header",
        "menu", "thumbnailOverlays", "trackingParams", "overlay", "sheetViewModel", "loggingDirectives",
    )
    private val VIDEO_KEYS = setOf(
        "videoRenderer", "compactVideoRenderer", "gridVideoRenderer", "playlistVideoRenderer",
        "videoWithContextRenderer", "playlistPanelVideoRenderer",
    )
    private val SHORT_KEYS = setOf("reelItemRenderer", "shortsLockupViewModel")
    private val CHANNEL_KEYS = setOf("channelRenderer", "gridChannelRenderer", "compactChannelRenderer")
    private val PLAYLIST_KEYS = setOf("playlistRenderer", "gridPlaylistRenderer", "compactPlaylistRenderer", "radioRenderer")

    private fun collect(node: Any?, out: MutableMap<String, Item>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k in SKIP) continue
                    val v = node.opt(k)
                    if (v is JSONObject) {
                        val item = when {
                            k in VIDEO_KEYS -> runCatching { video(v) }.getOrNull()
                            k in SHORT_KEYS -> runCatching { short(v) }.getOrNull()
                            k in CHANNEL_KEYS -> runCatching { channel(v) }.getOrNull()
                            k in PLAYLIST_KEYS -> runCatching { playlist(v) }.getOrNull()
                            k == "lockupViewModel" -> runCatching { lockup(v) }.getOrNull()
                            else -> { collect(v, out); null }
                        }
                        if (item != null) out.putIfAbsent(item.kind.name + item.id, item)
                    } else if (v is JSONArray) collect(v, out)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out)
        }
    }

    private fun watchUrl(id: String) = "$ORIGIN/watch?v=$id"

    private fun video(v: JSONObject): Item? {
        val id = v.optString("videoId").ifEmpty { return null }
        val title = text(v.opt("title")) ?: text(v.opt("headline")) ?: return null
        val ownerRuns = v.optJSONObject("ownerText") ?: v.optJSONObject("shortBylineText") ?: v.optJSONObject("longBylineText")
        val channel = text(ownerRuns)
        val channelId = firstString(ownerRuns, "browseId")
        val views = text(v.opt("shortViewCountText")) ?: text(v.opt("viewCountText"))
        val published = text(v.opt("publishedTimeText"))
        val live = views?.contains("watching") == true ||
            v.optJSONArray("badges")?.toString()?.contains("LIVE") == true
        val duration = v.optString("lengthSeconds").toLongOrNull() ?: clock(text(v.opt("lengthText")))
        val isShort = v.optJSONObject("navigationEndpoint")?.has("reelWatchEndpoint") == true
        val avatar = thumb(v.optJSONObject("channelThumbnailSupportedRenderers") ?: v.opt("channelThumbnail") ?: v.opt("avatar"))
        return Item(
            id, title, if (isShort) "$ORIGIN/shorts/$id" else watchUrl(id), channel, duration,
            "https://i.ytimg.com/vi/$id/hqdefault.jpg", Item.Kind.VIDEO,
            meta = listOfNotNull(views, published).joinToString(" · ").ifEmpty { null },
            avatar = avatar, channelUrl = channelId?.let { "$ORIGIN/channel/$it" }, short = isShort, live = live,
        )
    }

    private fun short(v: JSONObject): Item? {
        val id = firstString(v.optJSONObject("onTap") ?: v.optJSONObject("navigationEndpoint") ?: v, "videoId")
            ?: v.optString("videoId").ifEmpty { null } ?: return null
        val overlay = v.optJSONObject("overlayMetadata")
        val title = overlay?.optJSONObject("primaryText")?.optString("content")?.ifEmpty { null }
            ?: text(v.opt("headline")) ?: "Short"
        val views = overlay?.optJSONObject("secondaryText")?.optString("content")?.ifEmpty { null }
            ?: text(v.opt("viewCountText"))
        val t = thumb(v.opt("thumbnail")) ?: "https://i.ytimg.com/vi/$id/oar2.jpg"
        return Item(id, title, "$ORIGIN/shorts/$id", null, null, t, Item.Kind.VIDEO, meta = views, short = true)
    }

    private fun channel(v: JSONObject): Item? {
        val id = v.optString("channelId").ifEmpty { return null }
        val title = text(v.opt("title")) ?: return null
        val subs = text(v.opt("videoCountText"))?.takeIf { it.contains("subscriber") } ?: text(v.opt("subscriberCountText"))
        val t = thumb(v.opt("thumbnail"))
        return Item(id, title, "$ORIGIN/channel/$id", null, null, t, Item.Kind.CHANNEL, meta = subs, avatar = t)
    }

    private fun playlist(v: JSONObject): Item? {
        val id = v.optString("playlistId").ifEmpty { return null }
        val title = text(v.opt("title")) ?: return null
        val owner = text(v.opt("shortBylineText")) ?: text(v.opt("longBylineText"))
        val count = text(v.opt("videoCountText")) ?: v.optString("videoCount").ifEmpty { null }?.let { "$it videos" }
        val t = thumb(v.opt("thumbnail")) ?: thumb(v.opt("thumbnails"))
        return Item(id, title, "$ORIGIN/playlist?list=$id", owner, null, t, Item.Kind.PLAYLIST, meta = count)
    }

    /** The newer card format (Home, Up next, many search results). */
    private fun lockup(v: JSONObject): Item? {
        val id = v.optString("contentId").ifEmpty { return null }
        val type = v.optString("contentType")
        val meta = v.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = meta.optJSONObject("title")?.optString("content")?.ifEmpty { null } ?: return null
        val rows = meta.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
        val lines = ArrayList<String>()
        if (rows != null) {
            for (r in 0 until rows.length()) {
                val parts = rows.optJSONObject(r)?.optJSONArray("metadataParts") ?: continue
                val texts = ArrayList<String>()
                for (p in 0 until parts.length()) {
                    val part = parts.optJSONObject(p) ?: continue
                    var t = part.optJSONObject("text")?.optString("content").orEmpty()
                    if (t.isEmpty()) continue
                    // "204K" whose label says "views" -> "204K views"
                    if (part.optString("accessibilityLabel").endsWith("views") && !t.contains("view")) t = "$t views"
                    texts.add(t)
                }
                if (texts.isNotEmpty()) lines.add(texts.joinToString(" · "))
            }
        }
        val channel = lines.getOrNull(0)
        val metaLine = lines.drop(1).joinToString(" · ").ifEmpty { null }
        val avatarNode = meta.optJSONObject("image")
        val avatar = thumb(avatarNode)
        val channelId = firstString(avatarNode, "browseId")
        val image = v.optJSONObject("contentImage")
        val live = image?.toString()?.contains("\"LIVE\"") == true
        return when {
            type.contains("VIDEO") -> Item(
                id, title, watchUrl(id), channel, findClock(image), "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                Item.Kind.VIDEO, meta = metaLine, avatar = avatar, channelUrl = channelId?.let { "$ORIGIN/channel/$it" }, live = live,
            )
            type.contains("PLAYLIST") || type.contains("ALBUM") || type.contains("PODCAST") -> Item(
                id, title, "$ORIGIN/playlist?list=$id", channel, null, thumb(image), Item.Kind.PLAYLIST, meta = metaLine,
            )
            type.contains("CHANNEL") -> Item(id, title, "$ORIGIN/channel/$id", null, null, thumb(image), Item.Kind.CHANNEL, meta = metaLine)
            else -> null
        }
    }

    // ------------------------------------------------------------------ watch page

    /** Title, stats, channel, likes, comments preview and related videos, all from one request. */
    fun watch(videoId: String, cookies: Map<String, String>?): WatchInfo {
        val json = postWeb("next", JSONObject().put("videoId", videoId), cookies)
        val two = json.optJSONObject("contents")?.optJSONObject("twoColumnWatchNextResults")
        val results = two?.optJSONObject("results")?.optJSONObject("results")
        val primary = findObj(results, "videoPrimaryInfoRenderer")
        val secondary = findObj(results, "videoSecondaryInfoRenderer")
        val owner = findObj(secondary, "videoOwnerRenderer")
        val subBtn = findObj(secondary, "subscribeButtonRenderer")
        val likeVm = findObj(primary, "likeButtonViewModel")
        val viewCount = findObj(primary, "videoViewCountRenderer")

        var commentsToken: String? = null
        var commentCount: String? = null
        var teaser: Comment? = null
        val contents = results?.optJSONArray("contents")
        if (contents != null) {
            for (i in 0 until contents.length()) {
                val sec = contents.optJSONObject(i)?.optJSONObject("itemSectionRenderer") ?: continue
                if (sec.optString("sectionIdentifier") == "comment-item-section") {
                    commentsToken = firstString(sec, "token")
                }
                findObj(sec, "commentsEntryPointHeaderRenderer")?.let { h ->
                    commentCount = text(h.opt("commentCount"))
                    findObj(h, "commentsEntryPointTeaserRenderer")?.let { t ->
                        teaser = Comment("", thumb(t.opt("teaserAvatar")), text(t.opt("teaserContent")) ?: "", null, null, null)
                    }
                }
            }
        }

        val related = LinkedHashMap<String, Item>()
        collect(two?.optJSONObject("secondaryResults"), related)

        return WatchInfo(
            videoId = videoId,
            title = text(primary?.opt("title")) ?: "",
            views = text(viewCount?.opt("shortViewCount")) ?: text(viewCount?.opt("viewCount")),
            date = text(primary?.opt("relativeDateText")) ?: text(primary?.opt("dateText")),
            description = secondary?.optJSONObject("attributedDescription")?.optString("content")?.ifEmpty { null },
            channel = text(owner?.opt("title")),
            channelId = subBtn?.optString("channelId")?.ifEmpty { null } ?: firstString(owner, "browseId"),
            avatar = thumb(owner?.opt("thumbnail")),
            subscribers = text(owner?.opt("subscriberCountText")),
            subscribed = subBtn?.optBoolean("subscribed", false) ?: false,
            likes = firstString(likeVm, "title"),
            liked = firstString(likeVm, "likeStatus") == "LIKE",
            commentCount = commentCount,
            commentTeaser = teaser,
            commentsToken = commentsToken,
            related = related.values.filter { it.id != videoId }.take(40),
        )
    }

    /** One page of comments; returns them plus the token for the next page. */
    fun comments(token: String, cookies: Map<String, String>?): Pair<List<Comment>, String?> {
        val json = postWeb("next", JSONObject().put("continuation", token), cookies)
        val out = ArrayList<Comment>()
        val muts = json.optJSONObject("frameworkUpdates")?.optJSONObject("entityBatchUpdate")?.optJSONArray("mutations")
        if (muts != null) {
            for (i in 0 until muts.length()) {
                val c = muts.optJSONObject(i)?.optJSONObject("payload")?.optJSONObject("commentEntityPayload") ?: continue
                val props = c.optJSONObject("properties")
                val author = c.optJSONObject("author")
                val toolbar = c.optJSONObject("toolbar")
                if (props?.optInt("replyLevel", 0) != 0) continue
                out.add(
                    Comment(
                        author = author?.optString("displayName").orEmpty(),
                        avatar = author?.optString("avatarThumbnailUrl")?.ifEmpty { null },
                        text = props?.optJSONObject("content")?.optString("content").orEmpty(),
                        time = props?.optString("publishedTime")?.ifEmpty { null },
                        likes = toolbar?.optString("likeCountNotliked")?.trim()?.ifEmpty { null },
                        replies = toolbar?.optString("replyCount")?.trim()?.ifEmpty { null },
                    )
                )
            }
        }
        // The "load more" token sits in the last continuation item of the response.
        var next: String? = null
        walkKey(json.optJSONArray("onResponseReceivedEndpoints"), "continuationItemRenderer") { r ->
            if (r.optJSONObject("button") == null) next = firstString(r, "token")
        }
        return out to next
    }

    // ------------------------------------------------------------------ search suggestions

    fun suggest(query: String): List<String> {
        val q = URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=$hl&gl=$gl&q=$q")
            .header("User-Agent", WEB_UA).build()
        return http.newCall(req).execute().use { r ->
            val arr = JSONArray(r.body?.string().orEmpty())
            val list = arr.optJSONArray(1) ?: return@use emptyList()
            (0 until list.length()).map { list.optString(it) }.filter { it.isNotEmpty() }.take(12)
        }
    }

    // ------------------------------------------------------------------ account actions

    fun like(videoId: String, on: Boolean, cookies: Map<String, String>) {
        postWeb(if (on) "like/like" else "like/removelike", JSONObject().put("target", JSONObject().put("videoId", videoId)), cookies)
    }

    fun subscribe(channelId: String, on: Boolean, cookies: Map<String, String>) {
        postWeb(if (on) "subscription/subscribe" else "subscription/unsubscribe",
            JSONObject().put("channelIds", JSONArray().put(channelId)), cookies)
    }

    /** Turn a youtube.com/@handle (or any channel URL) into a browse id. */
    fun resolveBrowseId(url: String, cookies: Map<String, String>?): String? {
        val json = postWeb("navigation/resolve_url", JSONObject().put("url", url), cookies)
        return firstString(json.optJSONObject("endpoint"), "browseId")
    }

    // ------------------------------------------------------------------ JSON helpers

    private fun text(o: Any?): String? = when (o) {
        is String -> o.ifEmpty { null }
        is JSONObject -> o.optString("simpleText").ifEmpty { null }
            ?: o.optJSONArray("runs")?.let { runs ->
                buildString { for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty()) }.ifEmpty { null }
            }
            ?: o.optString("content").ifEmpty { null }
        else -> null
    }

    private val CLOCK = Regex("^\\d{1,2}(:\\d{2}){1,2}$")

    private fun clock(s: String?): Long? {
        if (s == null || !CLOCK.matches(s.trim())) return null
        return s.trim().split(":").fold(0L) { acc, p -> acc * 60 + p.toLong() }
    }

    private fun findClock(node: Any?): Long? {
        when (node) {
            is String -> return clock(node)
            is JSONObject -> { val ks = node.keys(); while (ks.hasNext()) findClock(node.opt(ks.next()))?.let { return it } }
            is JSONArray -> for (i in 0 until node.length()) findClock(node.opt(i))?.let { return it }
        }
        return null
    }

    private fun firstString(node: Any?, key: String): String? {
        when (node) {
            is JSONObject -> {
                node.opt(key)?.let { if (it is String && it.isNotEmpty()) return it }
                val ks = node.keys()
                while (ks.hasNext()) firstString(node.opt(ks.next()), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) firstString(node.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun findObj(node: Any?, key: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let { return it }
                val ks = node.keys()
                while (ks.hasNext()) findObj(node.opt(ks.next()), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findObj(node.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun walkKey(node: Any?, key: String, f: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                val ks = node.keys()
                while (ks.hasNext()) {
                    val k = ks.next()
                    val v = node.opt(k)
                    if (k == key && v is JSONObject) f(v) else walkKey(v, key, f)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) walkKey(node.opt(i), key, f)
        }
    }

    /** Best image URL under [node] (largest of the first thumbnail list found). */
    private fun thumb(node: Any?): String? {
        val list = findArray(node, "thumbnails") ?: findArray(node, "sources")
        var best: String? = null
        var bestW = -1
        if (list != null) {
            for (i in 0 until list.length()) {
                val t = list.optJSONObject(i) ?: continue
                val u = t.optString("url")
                if (u.isEmpty()) continue
                val w = t.optInt("width", 0)
                if (w >= bestW) { bestW = w; best = u }
            }
        }
        val u = best ?: firstString(node, "url") ?: return null
        return if (u.startsWith("//")) "https:$u" else u
    }

    private fun findArray(node: Any?, key: String): JSONArray? {
        when (node) {
            is JSONObject -> {
                node.optJSONArray(key)?.let { return it }
                val ks = node.keys()
                while (ks.hasNext()) findArray(node.opt(ks.next()), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findArray(node.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun continuation(json: JSONObject): String? {
        var found: String? = null
        walkKey(json, "continuationItemRenderer") { r -> firstString(r, "token")?.let { found = it } }
        return found
    }

    // ------------------------------------------------------------------ player

    private val VIDEO_ID = Regex("(?:[?&]v=|youtu\\.be/|/shorts/|/live/|/embed/)([A-Za-z0-9_-]{11})")

    fun videoId(url: String): String? = VIDEO_ID.find(url)?.groupValues?.get(1)

    /** Direct stream links for one video, in a single request. */
    fun player(videoId: String, maxHeight: Int): Stream {
        var j = playerRequest(videoId, visitor())
        var status = j.optJSONObject("playabilityStatus")
        if (status?.optString("status") == "LOGIN_REQUIRED") {
            j = playerRequest(videoId, visitor(refresh = true))
            status = j.optJSONObject("playabilityStatus")
        }
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
        // For vertical videos (Shorts) compare the short side, so "720p" means the same thing.
        fun side(f: JSONObject) = minOf(f.optInt("height"), f.optInt("width").takeIf { it > 0 } ?: Int.MAX_VALUE)
        if (adaptive != null) {
            for (i in 0 until adaptive.length()) {
                val f = adaptive.optJSONObject(i) ?: continue
                if (f.optString("url").isEmpty()) continue
                val mime = f.optString("mimeType")
                if (mime.startsWith("video/mp4") && mime.contains("avc1")) {
                    val h = side(f)
                    if (h > 0) heights.add(h)
                    if (h in 1..maxHeight) {
                        val v = video
                        if (v == null || h > side(v) || (h == side(v) && f.optInt("bitrate") > v.optInt("bitrate"))) video = f
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
        if (video == null && adaptive != null && heights.isNotEmpty()) {
            val low = heights.last()
            for (i in 0 until adaptive.length()) {
                val f = adaptive.optJSONObject(i) ?: continue
                if (f.optString("mimeType").let { it.startsWith("video/mp4") && it.contains("avc1") } &&
                    side(f) == low && f.optString("url").isNotEmpty()) { video = f; break }
            }
        }
        val hList = heights.toList()
        val v = video
        val a = audio
        if (v != null && a != null) {
            return Stream(title, author, v.getString("url"), a.getString("url"), headers, side(v).takeIf { it > 0 },
                fast = true, captions = captions, heights = hList, videoId = videoId)
        }
        val hls = sd.optString("hlsManifestUrl")
        if (hls.isNotEmpty()) return Stream(title, author, hls, null, headers, null, hls = true, fast = true,
            captions = captions, heights = hList, videoId = videoId)
        throw IOException("No directly playable stream")
    }

    private fun playerRequest(videoId: String, vd: String?): JSONObject {
        val client = JSONObject()
            .put("clientName", PLAYER_NAME).put("clientVersion", PLAYER_VERSION)
            .put("deviceMake", "Apple").put("deviceModel", "RealityDevice17,1")
            .put("osName", "visionOS").put("osVersion", "26.5.23O471")
            .put("userAgent", PLAYER_UA).put("hl", hl).put("gl", gl)
        if (vd != null) client.put("visitorData", vd)
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
            .apply { if (vd != null) header("X-Goog-Visitor-Id", vd) }
            .build()
        return execute(req)
    }

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
}
