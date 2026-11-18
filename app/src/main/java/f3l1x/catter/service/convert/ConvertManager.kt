package f3l1x.catter.service.convert

import android.content.Context
import android.net.Uri
import f3l1x.catter.data.DownloadRecord
import f3l1x.catter.data.DownloadStore
import f3l1x.catter.service.download.DownloadNotifications
import f3l1x.catter.service.download.Downloader
import f3l1x.catter.service.download.SaveLocation
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

enum class ConvertStatus { Queued, Running }

data class ConvertJob(
    val sourceTitle: String,
    val sourceUri: String,
    val preset: ConvertPreset,
    val treeUri: String?,
    val thumbnailUrl: String,
)

object ConvertManager {

    data class Task(
        val id: String,
        val title: String,
        val presetLabel: String,
        val status: ConvertStatus = ConvertStatus.Queued,
        val progress: Float? = null,
    )

    data class Failure(
        val id: String,
        val title: String,
        val presetLabel: String,
        val message: String,
        val job: ConvertJob,
    )

    private class Request(val id: String, val job: ConvertJob)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = ArrayDeque<Request>()
    private var worker: Job? = null
    private var active: Request? = null
    private var running: Job? = null

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _failures = MutableStateFlow<List<Failure>>(emptyList())
    val failures: StateFlow<List<Failure>> = _failures.asStateFlow()

    fun start(context: Context, job: ConvertJob): String {
        val appContext = context.applicationContext
        val id = UUID.randomUUID().toString()
        _tasks.update { it + Task(id, job.sourceTitle, job.preset.label) }
        DownloadNotifications.ensureChannels(appContext)
        ConvertNotifications.ensureChannels(appContext)
        runCatching { ConvertService.start(appContext) }
            .onFailure { Logger.w("convert service start failed", it) }

        queue.addLast(Request(id, job))
        if (worker?.isActive != true) worker = scope.launch { drain(appContext) }
        return id
    }

    fun cancel(id: String) {
        queue.removeAll { it.id == id }
        if (active?.id == id) running?.cancel()
        _tasks.update { tasks -> tasks.filterNot { it.id == id } }
    }

    fun dismissFailure(id: String) {
        _failures.update { failures -> failures.filterNot { it.id == id } }
    }

    fun retry(context: Context, id: String) {
        val failure = _failures.value.firstOrNull { it.id == id } ?: return
        _failures.update { failures -> failures.filterNot { it.id == id } }
        start(context, failure.job)
    }

    private suspend fun drain(context: Context) {
        while (true) {
            val request = queue.removeFirstOrNull() ?: break
            active = request
            markRunning(request.id)
            val job = scope.launch { run(context, request) }
            running = job
            job.join()
            active = null
            running = null
        }
        ConvertService.stop(context)
    }

    private suspend fun run(context: Context, queued: Request) {
        val job = queued.job
        val stamp = System.nanoTime()
        val input = File(context.cacheDir, "catter-convert-$stamp-src")
        val output = File(context.cacheDir, "catter-convert-$stamp.${job.preset.extension}")
        val passLog = File(context.cacheDir, "catter-convert-$stamp-pass")

        fun report(fraction: Float) {
            _tasks.update { tasks ->
                tasks.map { if (it.id == queued.id) it.copy(progress = fraction) else it }
            }
        }

        try {
            withContext(Dispatchers.IO) { stash(context, job.sourceUri, input) }
            val duration = runCatching { Converter.durationMs(input.absolutePath) }.getOrDefault(0L)
            val target = job.preset.targetBytes
            if (target > 0L) {
                if (duration <= 0L) throw IOException("Media duration is unknown")
                val seconds = duration / 1000.0
                val videoKbps = ((target * 0.93 * 8.0) / seconds / 1000.0).toInt() - 128
                if (videoKbps < 50) throw IOException("Video is too long to fit in 8MB")
                val videoArgs = listOf("-c:v", "libx264", "-preset", "veryfast", "-b:v", "${videoKbps}k")
                val pass1 = listOf("-y", "-i", input.absolutePath) + videoArgs +
                    listOf("-pass", "1", "-passlogfile", passLog.absolutePath, "-an", "-f", "null", "/dev/null")
                val pass2 = listOf("-y", "-i", input.absolutePath) + videoArgs +
                    listOf(
                        "-pass", "2", "-passlogfile", passLog.absolutePath,
                        "-c:a", "aac", "-b:a", "128k",
                        "-movflags", "+faststart",
                        output.absolutePath,
                    )
                Converter.convert(pass1.toTypedArray(), duration) { report(it * 0.5f) }
                Converter.convert(pass2.toTypedArray(), duration) { report(0.5f + it * 0.5f) }
            } else {
                val arguments = buildList {
                    add("-y")
                    addAll(job.preset.arguments?.invoke(input.absolutePath, output.absolutePath).orEmpty())
                }
                Converter.convert(arguments.toTypedArray(), duration, ::report)
            }

            val fileName = Downloader.fileName(
                title = job.sourceTitle,
                artist = "",
                label = job.preset.label,
                extension = job.preset.extension,
            )
            val destination = SaveLocation.destination(job.treeUri, job.preset.kind)
            val saved = Downloader.publish(context, output, fileName, job.preset.mimeType, destination)

            DownloadStore.add(
                DownloadRecord(
                    id = queued.id,
                    title = job.sourceTitle,
                    author = "",
                    thumbnailUrl = job.thumbnailUrl,
                    qualityLabel = job.preset.label,
                    format = job.preset.extension.uppercase(),
                    mimeType = job.preset.mimeType,
                    uri = saved.uri,
                    fileName = saved.fileName,
                    size = saved.size,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            ConvertNotifications.completed(context, DownloadStore.records.value.first { it.id == queued.id })
        } catch (cancelled: CancellationException) {
            Logger.d("conversion cancelled: ${job.sourceTitle}")
        } catch (throwable: Throwable) {
            Logger.e("conversion failed: ${job.sourceTitle}", throwable)
            ConvertNotifications.failed(context, job.sourceTitle, throwable.message)
            _failures.update { failures ->
                failures + Failure(
                    id = queued.id,
                    title = job.sourceTitle,
                    presetLabel = job.preset.label,
                    message = throwable.message ?: throwable.toString(),
                    job = job,
                )
            }
        } finally {
            input.delete()
            output.delete()
            context.cacheDir
                .listFiles { file -> file.name.startsWith(passLog.name) }
                ?.forEach { it.delete() }
            _tasks.update { tasks -> tasks.filterNot { it.id == queued.id } }
        }
    }

    private fun stash(context: Context, source: String, target: File) {
        val uri = Uri.parse(source)
        if (uri.scheme == "content") {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                target.outputStream().use { stream.copyTo(it, BUFFER_SIZE) }
            } ?: throw IOException("Source is not readable")
        } else {
            File(source).copyTo(target, overwrite = true)
        }
    }

    private fun markRunning(id: String) {
        _tasks.update { tasks ->
            tasks.map { if (it.id == id) it.copy(status = ConvertStatus.Running) else it }
        }
    }

    private const val BUFFER_SIZE = 64 * 1024
}
