package com.viyt.app.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import com.viyt.app.core.DownloadFormat
import com.viyt.app.core.Engine
import com.viyt.app.core.InnerTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Direct downloads without yt-dlp: take the stream links from the fast API, pull each file in
 * 2 MB pieces over 6 parallel connections, then merge video + audio with Android's MediaMuxer.
 * Throws on anything unusual so the caller can fall back to yt-dlp.
 */
object FastDownload {

    private const val CHUNK = 2L * 1024 * 1024
    private const val PARALLEL = 6

    class Canceled : IOException("Canceled")

    suspend fun run(
        url: String,
        format: DownloadFormat,
        dir: File,
        canceled: () -> Boolean,
        progress: (Float, String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        require(format != DownloadFormat.MP3) { "MP3 needs conversion" }
        val id = InnerTube.videoId(url) ?: throw IOException("Not a single video")
        progress(0f, "Preparing")
        val s = Engine.resolve(url, format.height ?: 360)
        if (!s.fast || s.hls) throw IOException("No direct streams")
        val audioUrl = s.audioUrl ?: throw IOException("No separate audio stream")
        val base = safeName(s.title).ifEmpty { id } + " [$id]"

        val audioOnly = format == DownloadFormat.M4A
        val parts = if (audioOnly) listOf(audioUrl) else listOf(s.videoUrl, audioUrl)
        val sizes = parts.map { contentLength(it, s.headers) }
        val total = sizes.sum().toFloat()
        val done = AtomicLong(0)
        val tick: (Long) -> Unit = { n ->
            val d = done.addAndGet(n)
            progress((d / total).coerceIn(0f, 0.98f), "Downloading")
        }
        val files = parts.mapIndexed { i, _ -> File(dir, "part$i") }
        coroutineScope {
            parts.indices.map { i ->
                async { fetch(parts[i], sizes[i], files[i], s.headers, canceled, tick) }
            }.awaitAll()
        }
        if (canceled()) throw Canceled()
        progress(0.99f, "Merging")
        val out = File(dir, if (audioOnly) "$base.m4a" else "$base.mp4")
        mux(files, out)
        files.forEach { it.delete() }
        out
    }

    private fun safeName(t: String) = t.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").trim().take(120)

    private fun contentLength(url: String, headers: Map<String, String>): Long {
        Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
        val req = Request.Builder().url(url).head().apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        InnerTube.http.newCall(req).execute().use { r ->
            val len = r.header("Content-Length")?.toLongOrNull()
            if (!r.isSuccessful || len == null || len <= 0) throw IOException("Unknown file size")
            return len
        }
    }

    private suspend fun fetch(
        url: String, size: Long, file: File, headers: Map<String, String>,
        canceled: () -> Boolean, tick: (Long) -> Unit,
    ) = coroutineScope {
        RandomAccessFile(file, "rw").use { it.setLength(size) }
        val gate = Semaphore(PARALLEL / 2 + 1)
        val ranges = (0 until size step CHUNK).map { it to minOf(it + CHUNK, size) - 1 }
        ranges.map { (from, to) ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    var attempt = 0
                    while (true) {
                        if (canceled()) throw Canceled()
                        try {
                            piece(url, from, to, file, headers, canceled, tick)
                            break
                        } catch (c: Canceled) {
                            throw c
                        } catch (e: IOException) {
                            if (++attempt >= 4) throw e
                        }
                    }
                }
            }
        }.awaitAll()
    }

    private fun piece(
        url: String, from: Long, to: Long, file: File, headers: Map<String, String>,
        canceled: () -> Boolean, tick: (Long) -> Unit,
    ) {
        val req = Request.Builder().url(url).header("Range", "bytes=$from-$to")
            .apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        InnerTube.http.newCall(req).execute().use { r ->
            if (r.code != 206 && r.code != 200) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("Empty body")
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(from)
                val buf = ByteArray(64 * 1024)
                val input = body.byteStream()
                var written = 0L
                val expected = to - from + 1
                while (written < expected) {
                    if (canceled()) throw Canceled()
                    val n = input.read(buf)
                    if (n < 0) break
                    raf.write(buf, 0, n)
                    written += n
                    tick(n.toLong())
                }
                if (written < expected) {
                    tick(-written)
                    throw IOException("Connection dropped")
                }
            }
        }
    }

    /** Copy every track of [inputs] into one MP4 container (no re-encoding, so it takes a second or two). */
    private fun mux(inputs: List<File>, out: File) {
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val extractors = inputs.map { f -> MediaExtractor().apply { setDataSource(f.absolutePath) } }
        try {
            val mapping = ArrayList<Pair<MediaExtractor, Int>>()
            for (ex in extractors) {
                for (t in 0 until ex.trackCount) {
                    val fmt = ex.getTrackFormat(t)
                    val mime = fmt.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                        ex.selectTrack(t)
                        mapping.add(ex to muxer.addTrack(fmt))
                        break
                    }
                }
            }
            if (mapping.isEmpty()) throw IOException("Nothing to merge")
            muxer.start()
            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            for ((ex, track) in mapping) {
                while (true) {
                    val n = ex.readSampleData(buffer, 0)
                    if (n < 0) break
                    info.set(0, n, ex.sampleTime, ex.sampleFlags)
                    muxer.writeSampleData(track, buffer, info)
                    ex.advance()
                }
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            extractors.forEach { runCatching { it.release() } }
        }
    }
}
