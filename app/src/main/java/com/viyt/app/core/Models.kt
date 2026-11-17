package com.viyt.app.core

/** One row in any list: a video, a playlist or a channel. */
data class Item(
    val id: String,
    val title: String,
    val url: String,
    val channel: String?,
    val durationSec: Long?,
    val thumbnail: String?,
    val kind: Kind,
) {
    enum class Kind { VIDEO, PLAYLIST, CHANNEL }
}

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
    /** True when it came from the direct API (fast path), false when yt-dlp had to resolve it. */
    val fast: Boolean = false,
    val captions: List<Caption> = emptyList(),
    /** Video heights available for this video, highest first. */
    val heights: List<Int> = emptyList(),
    val videoId: String? = null,
)

/** The YouTube feeds viyt can read with your signed-in session. */
enum class Feed(val label: String, val browseId: String, val ytdlpUrl: String) {
    HOME("Home", "FEwhat_to_watch", ":ytrec"),
    SUBSCRIPTIONS("Subscriptions", "FEsubscriptions", "https://www.youtube.com/feed/subscriptions"),
    LIKED("Liked videos", "VLLL", "https://www.youtube.com/playlist?list=LL"),
    WATCH_LATER("Watch later", "VLWL", "https://www.youtube.com/playlist?list=WL"),
    HISTORY("History", "FEhistory", "https://www.youtube.com/feed/history"),
    PLAYLISTS("Your playlists", "FEplaylist_aggregation", "https://www.youtube.com/feed/playlists"),
    CHANNELS("Subscribed channels", "FEchannels", "https://www.youtube.com/feed/channels"),
}

enum class DownloadFormat(val label: String, val height: Int?) {
    BEST("Video, best up to 1080p", 1080),
    P720("Video, 720p", 720),
    P480("Video, 480p", 480),
    P360("Video, 360p (data saver)", 360),
    M4A("Audio, M4A", null),
    MP3("Audio, MP3", null),
}

fun Long.asDuration(): String {
    val h = this / 3600
    val m = (this % 3600) / 60
    val s = this % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
