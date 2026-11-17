package com.viyt.app.download

import com.viyt.app.core.DownloadFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val format: DownloadFormat,
    val status: Status = Status.QUEUED,
    val progress: Float = 0f,
    val etaSec: Long = -1,
    val message: String? = null,
    val resultUri: String? = null,
    val mime: String? = null,
    /** Set for music downloads: tagged audio saved to Music/ViVibe and kept for offline play. */
    val song: SongMeta? = null,
) {
    enum class Status { QUEUED, RUNNING, DONE, FAILED, CANCELED }
    val active get() = status == Status.QUEUED || status == Status.RUNNING
}

data class SongMeta(
    val videoId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val thumb: String?,
    val duration: String?,
    val mp3: Boolean,
)

/** In-memory download list shared by the service and the UI. */
object Downloads {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    fun add(t: DownloadTask) = _tasks.update { listOf(t) + it }

    fun get(id: String) = _tasks.value.firstOrNull { it.id == id }

    fun edit(id: String, f: (DownloadTask) -> DownloadTask) =
        _tasks.update { list -> list.map { if (it.id == id) f(it) else it } }

    fun nextQueued() = _tasks.value.lastOrNull { it.status == DownloadTask.Status.QUEUED }

    fun clearFinished() = _tasks.update { list -> list.filter { it.active } }
}
