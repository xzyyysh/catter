package f3l1x.catter.service

import android.content.Context
import f3l1x.catter.data.DownloadRecord
import f3l1x.catter.data.DownloadStore
import f3l1x.catter.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class DownloadStatus { Queued, Running }

object DownloadManager {

    data class Task(
        val id: String,
        val title: String,
        val thumbnailUrl: String,
        val qualityLabel: String,
        val status: DownloadStatus = DownloadStatus.Queued,
        val progress: DownloadProgress? = null,
    )

    private class Request(
        val id: String,
        val details: VideoDetails,
        val quality: Quality,
        val destination: Destination,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = ArrayDeque<Request>()

    private var worker: Job? = null
    private var activeJob: Job? = null
    private var activeId: String? = null

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    fun start(
        context: Context,
        details: VideoDetails,
        quality: Quality,
        destination: Destination,
    ): String {
        val appContext = context.applicationContext
        val id = UUID.randomUUID().toString()
        _tasks.update { it + Task(id, details.title, details.thumbnailUrl, quality.label) }
        DownloadNotifications.ensureChannels(appContext)
        runCatching { DownloadService.start(appContext) }
            .onFailure { Logger.w("foreground service start failed", it) }

        queue.addLast(Request(id, details, quality, destination))
        if (worker?.isActive != true) worker = scope.launch { drain(appContext) }

        return id
    }

    fun cancel(id: String) {
        queue.removeAll { it.id == id }
        if (activeId == id) activeJob?.cancel()
        _tasks.update { tasks -> tasks.filterNot { it.id == id } }
    }

    private suspend fun drain(context: Context) {
        while (true) {
            val request = queue.removeFirstOrNull() ?: break
            activeId = request.id
            markRunning(request.id)

            val job = scope.launch { run(context, request) }
            activeJob = job
            job.join()
            activeJob = null
            activeId = null
        }

        DownloadService.stop(context)
    }

    private suspend fun run(context: Context, request: Request) {
        val outcome = runCatching {
            val ticket = YouTube.ticket(request.details.id, request.quality)
            Downloader.save(
                context = context,
                ticket = ticket,
                quality = request.quality,
                title = request.details.title,
                artist = request.details.author,
                destination = request.destination,
            ) { stats ->
                _tasks.update { tasks ->
                    tasks.map { if (it.id == request.id) it.copy(progress = stats) else it }
                }
            }
        }

        if (outcome.exceptionOrNull() is CancellationException) {
            _tasks.update { tasks -> tasks.filterNot { it.id == request.id } }
            Logger.d("download cancelled: ${request.details.id}")
            return
        }

        val record = outcome.getOrNull()?.let { saved ->
            DownloadRecord(
                id = request.id,
                title = request.details.title,
                author = request.details.author,
                thumbnailUrl = request.details.thumbnailUrl,
                qualityLabel = request.quality.label,
                format = request.quality.format.uppercase(),
                mimeType = request.quality.mimeType,
                uri = saved.uri,
                fileName = saved.fileName,
                size = saved.size,
                createdAt = System.currentTimeMillis(),
            )
        }

        if (record != null) {
            DownloadStore.add(record)
            DownloadNotifications.completed(context, record)
        } else {
            val failure = outcome.exceptionOrNull()
            Logger.e("download failed: ${request.details.id}", failure)
            DownloadNotifications.failed(context, request.details.title, failure?.message)
        }

        _tasks.update { tasks -> tasks.filterNot { it.id == request.id } }
    }

    private fun markRunning(id: String) {
        _tasks.update { tasks ->
            tasks.map { if (it.id == id) it.copy(status = DownloadStatus.Running) else it }
        }
    }
}
