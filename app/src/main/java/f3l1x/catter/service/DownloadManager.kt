package f3l1x.catter.service

import android.content.Context
import f3l1x.catter.data.DownloadRecord
import f3l1x.catter.data.DownloadStore
import f3l1x.catter.util.Logger
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
        val signal: DownloadSignal = DownloadSignal(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = ArrayDeque<Request>()

    private var worker: Job? = null
    private var active: Request? = null

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
        active?.takeIf { it.id == id }?.signal?.cancel()
        _tasks.update { tasks -> tasks.filterNot { it.id == id } }
    }

    private suspend fun drain(context: Context) {
        while (true) {
            val request = queue.removeFirstOrNull() ?: break
            active = request
            markRunning(request.id)
            run(context, request)
            active = null
        }

        DownloadService.stop(context)
    }

    private suspend fun run(context: Context, request: Request) {
        try {
            val ticket = YouTube.ticket(request.details.id, request.quality)
            val saved = Downloader.save(
                context = context,
                ticket = ticket,
                quality = request.quality,
                title = request.details.title,
                artist = request.details.author,
                destination = request.destination,
                signal = request.signal,
            ) { stats ->
                _tasks.update { tasks ->
                    tasks.map { if (it.id == request.id) it.copy(progress = stats) else it }
                }
            }

            val record = DownloadRecord(
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

            DownloadStore.add(record)
            DownloadNotifications.completed(context, record)
        } catch (cancelled: DownloadCancelled) {
            Logger.d("download cancelled: ${request.details.id}")
        } catch (throwable: Throwable) {
            Logger.e("download failed: ${request.details.id}", throwable)
            DownloadNotifications.failed(context, request.details.title, throwable.message)
        } finally {
            _tasks.update { tasks -> tasks.filterNot { it.id == request.id } }
        }
    }

    private fun markRunning(id: String) {
        _tasks.update { tasks ->
            tasks.map { if (it.id == id) it.copy(status = DownloadStatus.Running) else it }
        }
    }
}
