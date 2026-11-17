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

/** What the player needs: one or two direct stream links plus the headers YouTube expects. */
data class Stream(
    val title: String,
    val channel: String?,
    val videoUrl: String,
    val audioUrl: String?,
    val headers: Map<String, String>,
    val height: Int?,
)

/** The YouTube feeds viyt can read with your signed-in session. */
enum class Feed(val label: String, val url: String, val needsLogin: Boolean) {
    HOME("Home", ":ytrec", true),
    SUBSCRIPTIONS("Subscriptions", "https://www.youtube.com/feed/subscriptions", true),
    LIKED("Liked videos", "https://www.youtube.com/playlist?list=LL", true),
    WATCH_LATER("Watch later", "https://www.youtube.com/playlist?list=WL", true),
    HISTORY("History", "https://www.youtube.com/feed/history", true),
    PLAYLISTS("Your playlists", "https://www.youtube.com/feed/playlists", true),
    CHANNELS("Subscribed channels", "https://www.youtube.com/feed/channels", true),
}

enum class DownloadFormat(val label: String) {
    BEST("Video, best up to 1080p"),
    P720("Video, 720p"),
    P480("Video, 480p"),
    P360("Video, 360p (data saver)"),
    M4A("Audio, M4A"),
    MP3("Audio, MP3"),
}

fun Long.asDuration(): String {
    val h = this / 3600
    val m = (this % 3600) / 60
    val s = this % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
