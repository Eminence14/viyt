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
                Downloads.add(DownloadTask(UUID.randomUUID().toString(), url, title, fmt))
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
        Engine.awaitEngine()
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

    private fun runOne(task: DownloadTask) {
        runningId = task.id
        Downloads.edit(task.id) { it.copy(status = DownloadTask.Status.RUNNING, message = "Starting") }
        val tmp = File(getExternalFilesDir(null) ?: filesDir, "tmp/${task.id}").apply { mkdirs() }
        try {
            val req = Engine.downloadRequest(task.url, task.format, tmp)
            var lastNotify = 0L
            val onProgress: (Float, Long, String) -> Unit = { progress, eta, line ->
                val p = (progress / 100f).coerceIn(0f, 1f)
                val msg = when {
                    line.contains("[Merger]") || line.contains("[ExtractAudio]") -> "Converting"
                    progress > 0 -> "Downloading"
                    else -> "Preparing"
                }
                Downloads.edit(task.id) { it.copy(progress = p, etaSec = eta, message = msg) }
                val now = System.currentTimeMillis()
                if (now - lastNotify > 700) {
                    lastNotify = now
                    nm.notify(NOTIF_ID, notification(task.title, msg, p, false))
                }
            }
            YoutubeDL.execute(req, task.id, onProgress)
            if (Downloads.get(task.id)?.status == DownloadTask.Status.CANCELED) return
            val file = tmp.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.maxByOrNull { it.length() }
                ?: throw IllegalStateException("yt-dlp finished but no file was produced")
            val mime = mimeOf(file)
            val uri = publish(file, mime)
            Downloads.edit(task.id) {
                it.copy(status = DownloadTask.Status.DONE, progress = 1f, message = "Saved to Download/viyt",
                    resultUri = uri.toString(), mime = mime)
            }
        } catch (t: Throwable) {
            val canceled = Downloads.get(task.id)?.status == DownloadTask.Status.CANCELED
            if (!canceled) {
                Downloads.edit(task.id) { it.copy(status = DownloadTask.Status.FAILED, message = Engine.friendly(t)) }
            }
        } finally {
            tmp.deleteRecursively()
        }
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
