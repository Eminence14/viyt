package com.viyt.app.core

import org.json.JSONArray
import org.json.JSONObject

/** One card in any list: a video, short, playlist or channel. */
data class Item(
    val id: String,
    val title: String,
    val url: String,
    val channel: String?,
    val durationSec: Long?,
    val thumbnail: String?,
    val kind: Kind,
    /** "1.2M views · 3 years ago" */
    val meta: String? = null,
    val avatar: String? = null,
    val channelUrl: String? = null,
    val short: Boolean = false,
    val live: Boolean = false,
) {
    enum class Kind { VIDEO, PLAYLIST, CHANNEL }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("url", url).put("channel", channel)
        .put("dur", durationSec ?: -1).put("thumb", thumbnail).put("kind", kind.name)
        .put("meta", meta).put("avatar", avatar).put("curl", channelUrl).put("short", short).put("live", live)

    companion object {
        fun fromJson(o: JSONObject) = Item(
            o.getString("id"), o.getString("title"), o.getString("url"), o.optStringOrNull("channel"),
            o.optLong("dur", -1).takeIf { it >= 0 }, o.optStringOrNull("thumb"), Kind.valueOf(o.getString("kind")),
            o.optStringOrNull("meta"), o.optStringOrNull("avatar"), o.optStringOrNull("curl"),
            o.optBoolean("short"), o.optBoolean("live"),
        )

        fun listToJson(items: List<Item>) = JSONArray().apply { items.forEach { put(it.toJson()) } }
        fun listFromJson(a: JSONArray) = (0 until a.length()).mapNotNull { runCatching { fromJson(a.getJSONObject(it)) }.getOrNull() }
    }
}

fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k)) null else optString(k).ifEmpty { null }

/** A home-feed filter chip ("All", "Music", "Gaming", ...). [token] null means the unfiltered feed. */
data class Chip(val label: String, val token: String?, val query: String? = null)

/** Where the next page of a feed comes from (YouTube's continuation token and the endpoint it belongs to). */
data class Cont(val endpoint: String, val token: String)

data class Feed(val items: List<Item>, val chips: List<Chip> = emptyList(), val next: Cont? = null)

data class Comment(
    val author: String,
    val avatar: String?,
    val text: String,
    val time: String?,
    val likes: String?,
    val replies: String?,
    val id: String = "",
    /** Token that loads this comment's replies; null when it has none. */
    val repliesToken: String? = null,
    /** Posted by the video's channel. */
    val byCreator: Boolean = false,
)

/** Everything on the watch page under the player, from one request. */
data class WatchInfo(
    val videoId: String,
    val title: String,
    val views: String?,
    val date: String?,
    val description: String?,
    val channel: String?,
    val channelId: String?,
    val avatar: String?,
    val subscribers: String?,
    val subscribed: Boolean,
    val likes: String?,
    val liked: Boolean,
    val commentCount: String?,
    val commentTeaser: Comment?,
    val commentsToken: String?,
    val related: List<Item>,
)

/** One subtitle track the player can show. */
data class Caption(val id: String, val label: String, val lang: String, val url: String, val auto: Boolean)

/** What the player needs: one or two direct stream links plus the headers YouTube expects. */
data class Stream(
    val title: String,
    val channel: String?,
    val videoUrl: String,
    val audioUrl: String?,
    val headers: Map<String, String>,
    val height: Int?,
    val hls: Boolean = false,
    val fast: Boolean = false,
    val captions: List<Caption> = emptyList(),
    val heights: List<Int> = emptyList(),
    val videoId: String? = null,
)

/** The signed-in library feeds. */
enum class LibraryFeed(val label: String, val browseId: String, val ytdlpUrl: String) {
    HOME("Home", "FEwhat_to_watch", ":ytrec"),
    SUBSCRIPTIONS("Subscriptions", "FEsubscriptions", "https://www.youtube.com/feed/subscriptions"),
    LIKED("Liked videos", "VLLL", "https://www.youtube.com/playlist?list=LL"),
    WATCH_LATER("Watch later", "VLWL", "https://www.youtube.com/playlist?list=WL"),
    HISTORY("History", "FEhistory", "https://www.youtube.com/feed/history"),
    PLAYLISTS("Playlists", "FEplaylist_aggregation", "https://www.youtube.com/feed/playlists"),
    CHANNELS("All subscriptions", "FEchannels", "https://www.youtube.com/feed/channels"),
}

enum class DownloadFormat(val label: String, val height: Int?) {
    BEST("Full HD (1080p)", 1080),
    P720("High (720p)", 720),
    P480("Medium (480p)", 480),
    P360("Low (360p)", 360),
    M4A("Audio only (M4A)", null),
    MP3("Audio only (MP3)", null),
}

fun Long.asDuration(): String {
    val h = this / 3600
    val m = (this % 3600) / 60
    val s = this % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Videos not shown last time come first, so pulling to refresh always brings something new to the top. */
fun newFirst(items: List<Item>, seen: Set<String>): List<Item> {
    if (seen.isEmpty()) return items
    val (old, new) = items.partition { it.id in seen }
    return new + old
}
