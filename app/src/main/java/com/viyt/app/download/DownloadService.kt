package com.viyt.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.viyt.app.core.DownloadFormat
import com.viyt.app.core.Engine
import com.viyt.app.ui.MainActivity
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Runs downloads one at a time in the foreground so Android doesn't kill them. */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private var runningId: String? = null
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground(notification("Preparing download", null, 0f, true))
        when (intent?.action) {
            ACTION_ENQUEUE -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: url
                val fmt = runCatching { DownloadFormat.valueOf(intent.getStringExtra(EXTRA_FORMAT)!!) }
                    .getOrDefault(DownloadFormat.BEST)
                val song = intent.getStringExtra(EXTRA_SONG)?.let { j ->
                    runCatching {
                        val o = org.json.JSONObject(j)
                        SongMeta(o.getString("id"), o.getString("title"), o.optString("artist").ifEmpty { null },
                            o.optString("album").ifEmpty { null }, o.optString("thumb").ifEmpty { null },
                            o.optString("dur").ifEmpty { null }, o.optBoolean("mp3"))
                    }.getOrNull()
                }
                if (song != null && Downloads.tasks.value.any { it.song?.videoId == song.videoId && it.active }) return START_NOT_STICKY
                Downloads.add(DownloadTask(UUID.randomUUID().toString(), url, title, fmt, song = song))
            }
            ACTION_CANCEL -> {
                val id = intent.getStringExtra(EXTRA_ID)
                if (id != null) {
                    if (id == runningId) YoutubeDL.destroyProcessById(id)
                    Downloads.edit(id) { it.copy(status = DownloadTask.Status.CANCELED, message = "Canceled") }
                }
            }
        }
        if (worker?.isActive != true) worker = scope.launch { drain() }
        return START_NOT_STICKY
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private suspend fun drain() {
        while (true) {
            val task = Downloads.nextQueued() ?: break
            runOne(task)
        }
        runningId = null
        stopForeground(STOP_FOREGROUND_DETACH)
        val done = Downloads.tasks.value.count { it.status == DownloadTask.Status.DONE }
        if (done > 0) nm.notify(NOTIF_DONE, notification("Downloads finished", "$done saved to Download/viyt", 1f, false))
        stopSelf()
    }

    private suspend fun runOne(task: DownloadTask) {
        runningId = task.id
        Downloads.edit(task.id) { it.copy(status = DownloadTask.Status.RUNNING, message = "Starting") }
        val tmp = File(getExternalFilesDir(null) ?: filesDir, "tmp/${task.id}").apply { mkdirs() }
        val canceled = { Downloads.get(task.id)?.status == DownloadTask.Status.CANCELED }
        var lastNotify = 0L
        val report: (Float, String, Long) -> Unit = { p, msg, eta ->
            Downloads.edit(task.id) { it.copy(progress = p, etaSec = eta, message = msg) }
            val now = System.currentTimeMillis()
            if (now - lastNotify > 700) {
                lastNotify = now
                nm.notify(NOTIF_ID, notification(task.title, msg, p, false))
            }
        }
        try {
            if (task.song != null) { runSong(task, tmp, canceled, report); return }
            // 1) Fast path: direct parallel download + native merge.
            val file = try {
                if (task.format == DownloadFormat.MP3) throw IllegalStateException("MP3 goes through yt-dlp")
                FastDownload.run(task.url, task.format, tmp, canceled) { p, msg -> report(p, msg, -1) }
            } catch (c: FastDownload.Canceled) {
                return
            } catch (t: Throwable) {
                if (canceled()) return
                // 2) Fallback: yt-dlp.
                tmp.listFiles()?.forEach { it.delete() }
                report(0f, "Preparing", -1)
                val req = Engine.ytdlpDownloadRequest(task.url, task.format, tmp)
                val onProgress: (Float, Long, String) -> Unit = { progress, eta, line ->
                    val msg = when {
                        line.contains("[Merger]") || line.contains("[ExtractAudio]") -> "Converting"
                        progress > 0 -> "Downloading"
                        else -> "Preparing"
                    }
                    report((progress / 100f).coerceIn(0f, 1f), msg, eta)
                }
                YoutubeDL.execute(req, task.id, onProgress)
                if (canceled()) return
                tmp.listFiles()
                    ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.startsWith("part") }
                    ?.maxByOrNull { it.length() }
                    ?: throw IllegalStateException("yt-dlp finished but no file was produced")
            }
            val mime = mimeOf(file)
            val uri = publish(file, mime)
            Downloads.edit(task.id) {
                it.copy(status = DownloadTask.Status.DONE, progress = 1f, message = "Saved to Download/viyt",
                    resultUri = uri.toString(), mime = mime)
            }
        } catch (t: Throwable) {
            if (!canceled()) {
                Downloads.edit(task.id) { it.copy(status = DownloadTask.Status.FAILED, message = Engine.friendly(t)) }
            }
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** Music: tagged audio with cover art, saved to Music/viyt/<album>, remembered for offline play. */
    private suspend fun runSong(task: DownloadTask, tmp: File, canceled: () -> Boolean, report: (Float, String, Long) -> Unit) {
        val song = task.song!!
        val onProgress: (Float, Long, String) -> Unit = { progress, eta, line ->
            val msg = when {
                line.contains("[ExtractAudio]") || line.contains("[EmbedThumbnail]") || line.contains("[Metadata]") -> "Tagging"
                progress > 0 -> "Downloading"
                else -> "Preparing"
            }
            report((progress / 100f).coerceIn(0f, 1f), msg, eta)
        }
        try {
            YoutubeDL.execute(Engine.ytdlpSongRequest(task.url, song.mp3, tmp, withArt = true), task.id, onProgress)
        } catch (t: Throwable) {
            if (canceled()) return
            // Cover art is a nice-to-have; retry without it rather than fail the song.
            tmp.listFiles()?.forEach { it.delete() }
            YoutubeDL.execute(Engine.ytdlpSongRequest(task.url, song.mp3, tmp, withArt = false), task.id, onProgress)
        }
        if (canceled()) return
        val file = tmp.listFiles()?.filter { it.isFile && (it.extension == "m4a" || it.extension == "mp3") }?.maxByOrNull { it.length() }
            ?: throw IllegalStateException("No audio file was produced")
        val name = listOfNotNull(song.artist, song.title).joinToString(" - ").replace(Regex("[\\\\/:*?\"<>|]"), " ").take(150) + "." + file.extension
        val mime = if (file.extension == "mp3") "audio/mpeg" else "audio/mp4"
        val folder = "Music/viyt" + (song.album?.let { "/" + it.replace(Regex("[\\\\/:*?\"<>|]"), " ").take(80) } ?: "")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("Couldn't create the song file")
        contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
        com.viyt.app.core.Offline.add(com.viyt.app.core.OfflineTrack(song.videoId, song.title, song.artist, song.album, song.thumb,
            song.duration, uri.toString(), System.currentTimeMillis()))
        Downloads.edit(task.id) { it.copy(status = DownloadTask.Status.DONE, progress = 1f, message = "Saved to $folder", resultUri = uri.toString(), mime = mime) }
    }

    /** Move the finished file into the public Download/viyt folder. */
    private fun publish(file: File, mime: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/viyt")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Couldn't create the file in Downloads")
        contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
        return uri
    }

    private fun mimeOf(f: File) = when (f.extension.lowercase()) {
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "opus", "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }

    private fun notification(title: String, text: String?, progress: Float, indeterminate: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "downloads"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val finished = progress >= 1f && !indeterminate
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(if (finished) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(!finished)
            .apply { if (!finished) setProgress(100, (progress * 100).toInt(), indeterminate) }
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 41
        private const val NOTIF_DONE = 42
        const val ACTION_ENQUEUE = "viyt.ENQUEUE"
        const val ACTION_CANCEL = "viyt.CANCEL"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_FORMAT = "format"
        const val EXTRA_ID = "id"
        const val EXTRA_SONG = "song"

        /** Download a song (tagged, with cover art) for offline listening. */
        fun enqueueSong(context: Context, t: com.viyt.app.core.Track, album: String?, mp3: Boolean = false) {
            val meta = org.json.JSONObject().put("id", t.videoId).put("title", t.title).put("artist", t.artist ?: "")
                .put("album", album ?: "").put("thumb", t.thumb ?: "").put("dur", t.duration ?: "").put("mp3", mp3)
            val i = Intent(context, DownloadService::class.java)
                .setAction(ACTION_ENQUEUE)
                .putExtra(EXTRA_URL, "https://www.youtube.com/watch?v=${t.videoId}")
                .putExtra(EXTRA_TITLE, listOfNotNull(t.artist, t.title).joinToString(" - "))
                .putExtra(EXTRA_FORMAT, if (mp3) DownloadFormat.MP3.name else DownloadFormat.M4A.name)
                .putExtra(EXTRA_SONG, meta.toString())
            ContextCompat.startForegroundService(context, i)
        }

        fun enqueue(context: Context, url: String, title: String, format: DownloadFormat) {
            val i = Intent(context, DownloadService::class.java)
                .setAction(ACTION_ENQUEUE)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_FORMAT, format.name)
            ContextCompat.startForegroundService(context, i)
        }

        fun cancel(context: Context, id: String) {
            val i = Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, id)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
