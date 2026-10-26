package f3l1x.catter.service.download

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

    data class Failure(
        val id: String,
        val title: String,
        val thumbnailUrl: String,
        val qualityLabel: String,
        val message: String,
        val request: DownloadRequest,
        val destination: Destination,
    )

    private class Request(
        val id: String,
        val job: DownloadRequest,
        val destination: Destination,
        val signal: DownloadSignal = DownloadSignal(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = ArrayDeque<Request>()

    private var worker: Job? = null
    private var active: Request? = null

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _failures = MutableStateFlow<List<Failure>>(emptyList())
    val failures: StateFlow<List<Failure>> = _failures.asStateFlow()

    fun start(context: Context, request: DownloadRequest, destination: Destination): String {
        val appContext = context.applicationContext
        val id = UUID.randomUUID().toString()
        _tasks.update { it + Task(id, request.title, request.thumbnailUrl, request.label) }
        DownloadNotifications.ensureChannels(appContext)
        runCatching { DownloadService.start(appContext) }
            .onFailure { Logger.w("foreground service start failed", it) }

        queue.addLast(Request(id, request, destination))
        if (worker?.isActive != true) worker = scope.launch { drain(appContext) }

        return id
    }

    fun dismissFailure(id: String) {
        _failures.update { failures -> failures.filterNot { it.id == id } }
    }

    fun retry(context: Context, id: String) {
        val failure = _failures.value.firstOrNull { it.id == id } ?: return
        _failures.update { failures -> failures.filterNot { it.id == id } }
        start(context, failure.request, failure.destination)
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

    private suspend fun run(context: Context, queued: Request) {
        val job = queued.job

        try {
            val ticket = job.resolve()
            val saved = Downloader.save(
                context = context,
                ticket = ticket,
                name = job.fileName,
                mimeType = job.mimeType,
                kind = job.kind,
                title = job.title,
                artist = job.author,
                destination = queued.destination,
                signal = queued.signal,
            ) { stats ->
                _tasks.update { tasks ->
                    tasks.map { if (it.id == queued.id) it.copy(progress = stats) else it }
                }
            }

            val record = DownloadRecord(
                id = queued.id,
                title = job.title,
                author = job.author,
                thumbnailUrl = job.thumbnailUrl,
                qualityLabel = job.label,
                format = job.fileName.substringAfterLast('.', "").uppercase(),
                mimeType = job.mimeType,
                uri = saved.uri,
                fileName = saved.fileName,
                size = saved.size,
                createdAt = System.currentTimeMillis(),
            )

            DownloadStore.add(record)
            DownloadNotifications.completed(context, record)
        } catch (cancelled: DownloadCancelled) {
            Logger.d("download cancelled: ${job.title}")
        } catch (throwable: Throwable) {
            Logger.e("download failed: ${job.title}", throwable)
            DownloadNotifications.failed(context, job.title, throwable.message)
            _failures.update { failures ->
                failures + Failure(
                    id = queued.id,
                    title = job.title,
                    thumbnailUrl = job.thumbnailUrl,
                    qualityLabel = job.label,
                    // Raw exception text, kept as-is because it is technical detail.
                    message = throwable.message ?: throwable.toString(),
                    request = job,
                    destination = queued.destination,
                )
            }
        } finally {
            _tasks.update { tasks -> tasks.filterNot { it.id == queued.id } }
        }
    }

    private fun markRunning(id: String) {
        _tasks.update { tasks ->
            tasks.map { if (it.id == id) it.copy(status = DownloadStatus.Running) else it }
        }
    }
}
