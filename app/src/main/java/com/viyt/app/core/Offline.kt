package com.viyt.app.core

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A song saved for offline listening. */
data class OfflineTrack(
    val videoId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val thumb: String?,
    val duration: String?,
    val uri: String,
    val savedAt: Long,
) {
    fun toTrack() = Track(videoId, title, artist, thumb, duration)
}

/**
 * The list of downloaded songs. The player looks here first, so a downloaded song always plays
 * from the phone, even inside a radio or someone else's playlist.
 */
object Offline {
    private lateinit var file: File
    private lateinit var app: Context
    val tracks = MutableStateFlow<List<OfflineTrack>>(emptyList())

    fun init(context: Context) {
        app = context.applicationContext
        file = File(app.filesDir, "offline.json")
        tracks.value = runCatching {
            val a = JSONArray(file.readText())
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                OfflineTrack(o.getString("id"), o.getString("title"), o.optStringOrNull("artist"), o.optStringOrNull("album"),
                    o.optStringOrNull("thumb"), o.optStringOrNull("dur"), o.getString("uri"), o.optLong("at"))
            }
        }.getOrDefault(emptyList())
    }

    /** Local file for a song, if downloaded and still on the phone. */
    fun uriFor(videoId: String): String? {
        val t = tracks.value.firstOrNull { it.videoId == videoId } ?: return null
        val ok = runCatching { app.contentResolver.openFileDescriptor(Uri.parse(t.uri), "r")?.use { true } ?: false }.getOrDefault(false)
        if (!ok) { remove(videoId); return null }
        return t.uri
    }

    fun has(videoId: String) = tracks.value.any { it.videoId == videoId }

    fun add(t: OfflineTrack) {
        tracks.value = listOf(t) + tracks.value.filter { it.videoId != t.videoId }
        save()
    }

    fun remove(videoId: String) {
        val t = tracks.value.firstOrNull { it.videoId == videoId }
        if (t != null) runCatching { app.contentResolver.delete(Uri.parse(t.uri), null, null) }
        tracks.value = tracks.value.filter { it.videoId != videoId }
        save()
    }

    private fun save() {
        val a = JSONArray()
        tracks.value.forEach {
            a.put(JSONObject().put("id", it.videoId).put("title", it.title).put("artist", it.artist).put("album", it.album)
                .put("thumb", it.thumb).put("dur", it.duration).put("uri", it.uri).put("at", it.savedAt))
        }
        runCatching { file.writeText(a.toString()) }
    }
}
