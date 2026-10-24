package f3l1x.catter.service

import android.content.Context
import f3l1x.catter.data.DownloadRecord
import f3l1x.catter.data.DownloadStore
import f3l1x.catter.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

object DownloadManager {

    data class Task(
        val id: String,
        val title: String,
        val thumbnailUrl: String,
        val qualityLabel: String,
        val progress: DownloadProgress? = null,
    )

    data class Finished(val id: String, val error: String?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _finished = MutableSharedFlow<Finished>(extraBufferCapacity = 16)
    val finished: SharedFlow<Finished> = _finished.asSharedFlow()

    fun start(
        context: Context,
        details: VideoDetails,
        quality: Quality,
        destination: Destination,
    ): String {
        val appContext = context.applicationContext
        val id = UUID.randomUUID().toString()
        _tasks.update { it + Task(id, details.title, details.thumbnailUrl, quality.label) }

        scope.launch {
            val outcome = runCatching {
                val ticket = YouTube.ticket(details.id, quality)
                Downloader.save(
                    context = appContext,
                    ticket = ticket,
                    quality = quality,
                    title = details.title,
                    artist = details.author,
                    destination = destination,
                ) { stats ->
                    _tasks.update { tasks ->
                        tasks.map { if (it.id == id) it.copy(progress = stats) else it }
                    }
                }
            }

            outcome.onSuccess { saved ->
                DownloadStore.add(
                    DownloadRecord(
                        id = id,
                        title = details.title,
                        author = details.author,
                        thumbnailUrl = details.thumbnailUrl,
                        qualityLabel = quality.label,
                        format = quality.format.uppercase(),
                        mimeType = quality.mimeType,
                        uri = saved.uri,
                        fileName = saved.fileName,
                        size = saved.size,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }.onFailure {
                Logger.e("download failed: ${details.id}", it)
            }

            _tasks.update { tasks -> tasks.filterNot { it.id == id } }
            _finished.emit(Finished(id = id, error = outcome.exceptionOrNull()?.message))
        }

        return id
    }
}
