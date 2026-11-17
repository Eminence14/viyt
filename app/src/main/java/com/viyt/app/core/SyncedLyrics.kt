package com.viyt.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One timed lyric line. */
data class LyricLine(val ms: Long, val text: String)

/**
 * Time-synced lyrics from LRCLIB (lrclib.net), a free, open lyrics database.
 * Used on top of YouTube Music's own (unsynced) lyrics.
 */
object SyncedLyrics {
    private val cache = ConcurrentHashMap<String, List<LyricLine>>()
    private val LINE = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]\\s?(.*)")

    suspend fun find(title: String, artist: String?, durationSec: Long?): List<LyricLine>? = withContext(Dispatchers.IO) {
        val key = "$artist|$title"
        cache[key]?.let { return@withContext it.ifEmpty { null } }
        val clean = title.replace(Regex("\\s*[(\\[](feat|ft|with|official|lyric|audio|video)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "").trim()
        val result = runCatching {
            // 1) Exact match by artist, title and duration.
            val get = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", clean)
                .apply { artist?.let { addQueryParameter("artist_name", it) } }
                .apply { durationSec?.takeIf { it > 0 }?.let { addQueryParameter("duration", it.toString()) } }
                .build()
            fetch(get.toString())?.let { o -> parse(o.optString("syncedLyrics")) }
                ?: run {
                    // 2) Search, prefer the closest duration.
                    val q = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                        .addQueryParameter("q", listOfNotNull(artist, clean).joinToString(" ")).build()
                    val arr = fetchArray(q.toString()) ?: return@run null
                    var best: JSONObject? = null
                    var bestDiff = Long.MAX_VALUE
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.optString("syncedLyrics").isEmpty()) continue
                        val diff = if (durationSec != null) kotlin.math.abs(o.optLong("duration") - durationSec) else 0L
                        if (diff < bestDiff) { bestDiff = diff; best = o }
                    }
                    if (best != null && bestDiff <= 8) parse(best.optString("syncedLyrics")) else null
                }
        }.getOrNull()
        cache[key] = result.orEmpty()
        result
    }

    private fun fetch(url: String): JSONObject? {
        val req = Request.Builder().url(url).header("User-Agent", "viyt (https://github.com/Eminence14/viyt)").build()
        return InnerTube.http.newCall(req).execute().use { r -> if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()) else null }
    }

    private fun fetchArray(url: String): JSONArray? {
        val req = Request.Builder().url(url).header("User-Agent", "viyt (https://github.com/Eminence14/viyt)").build()
        return InnerTube.http.newCall(req).execute().use { r -> if (r.isSuccessful) JSONArray(r.body?.string().orEmpty()) else null }
    }

    fun parse(lrc: String?): List<LyricLine>? {
        if (lrc.isNullOrBlank()) return null
        val out = ArrayList<LyricLine>()
        lrc.lineSequence().forEach { line ->
            val m = LINE.matchEntire(line.trim()) ?: return@forEach
            val (min, sec, frac, text) = m.destructured
            val ms = min.toLong() * 60_000 + sec.toLong() * 1000 + when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
            out.add(LyricLine(ms, text))
        }
        return out.sortedBy { it.ms }.ifEmpty { null }
    }
}
