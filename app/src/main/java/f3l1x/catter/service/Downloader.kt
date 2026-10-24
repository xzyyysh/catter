package f3l1x.catter.service

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import f3l1x.catter.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

data class SavedFile(
    val uri: String,
    val fileName: String,
    val size: Long,
)

data class DownloadProgress(
    val bytes: Long,
    val total: Long,
    val speed: Long,
) {
    val isDeterminate: Boolean get() = total > 0L

    val fraction: Float
        get() = if (isDeterminate) (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
}

object Downloader {

    private const val BUFFER_SIZE = 64 * 1024
    private const val PROGRESS_INTERVAL_MS = 120L
    private const val SPEED_WARMUP_MS = 400L

    val requiresLegacyPermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    suspend fun save(
        context: Context,
        ticket: DownloadTicket,
        quality: Quality,
        title: String,
        artist: String,
        onProgress: ((DownloadProgress) -> Unit)? = null,
    ): SavedFile = withContext(Dispatchers.IO) {
        val fileName = fileName(title, artist, quality)
        Logger.d("download start: $fileName")

        val connection = openStream(ticket.url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")

            val total = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
            Logger.d("download size: $total")

            val saved = connection.inputStream.use { input ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStore(context, input, fileName, quality, total, onProgress)
                } else {
                    saveToPublicDirectory(context, input, fileName, quality, total, onProgress)
                }
            }

            Logger.i("download saved: ${saved.uri}")
            saved
        } catch (throwable: Throwable) {
            Logger.e("download failed: $fileName", throwable)
            throw throwable
        } finally {
            connection.disconnect()
        }
    }

    fun fileName(title: String, artist: String, quality: Quality): String {
        val base = buildString {
            append(title.ifBlank { "Catter" })
            if (artist.isNotBlank()) append(" - ").append(artist)
        }
        val clean = base
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .take(80)
        return "$clean (${quality.label}).${quality.extension}"
    }

    private fun saveToMediaStore(
        context: Context,
        input: InputStream,
        fileName: String,
        quality: Quality,
        total: Long,
        onProgress: ((DownloadProgress) -> Unit)?,
    ): SavedFile {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val displayName = uniqueMediaName(resolver, collection, fileName)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, quality.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values) ?: throw IOException("Cannot create media entry")
        val size = try {
            val written = resolver.openOutputStream(uri)?.use { output ->
                copyWithProgress(input, output, total, onProgress)
            } ?: throw IOException("Cannot open output stream")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            written
        } catch (throwable: Throwable) {
            resolver.delete(uri, null, null)
            throw throwable
        }

        return SavedFile(uri = uri.toString(), fileName = displayName, size = size)
    }

    private fun uniqueMediaName(
        resolver: ContentResolver,
        collection: Uri,
        fileName: String,
    ): String {
        val base = fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.', "")

        var index = 1
        var candidate = fileName
        while (mediaNameExists(resolver, collection, candidate)) {
            candidate = if (extension.isEmpty()) "$base ($index)" else "$base ($index).$extension"
            index++
        }

        if (candidate != fileName) Logger.d("duplicate name, using: $candidate")
        return candidate
    }

    private fun mediaNameExists(
        resolver: ContentResolver,
        collection: Uri,
        displayName: String,
    ): Boolean = runCatching {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection =
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val arguments = arrayOf(displayName, "${Environment.DIRECTORY_DOWNLOADS}/")

        resolver.query(collection, projection, selection, arguments, null)?.use { cursor ->
            cursor.count > 0
        } ?: false
    }.getOrElse {
        Logger.w("name lookup failed, relying on provider: $displayName", it)
        false
    }

    private fun saveToPublicDirectory(
        context: Context,
        input: InputStream,
        fileName: String,
        quality: Quality,
        total: Long,
        onProgress: ((DownloadProgress) -> Unit)?,
    ): SavedFile {
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!directory.exists()) directory.mkdirs()

        val file = uniqueFile(directory, fileName)
        file.outputStream().use { output -> copyWithProgress(input, output, total, onProgress) }

        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(quality.mimeType),
            null,
        )

        return SavedFile(uri = file.absolutePath, fileName = file.name, size = file.length())
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val base = fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.', "")
        var candidate = File(directory, fileName)
        var index = 1
        while (candidate.exists()) {
            val name = if (extension.isEmpty()) "$base ($index)" else "$base ($index).$extension"
            candidate = File(directory, name)
            index++
        }
        return candidate
    }

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        total: Long,
        onProgress: ((DownloadProgress) -> Unit)?,
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytes = 0L
        var startedAt = 0L
        var lastEmitAt = 0L
        var speed = 0L

        while (true) {
            val read = input.read(buffer)
            if (read == -1) break

            output.write(buffer, 0, read)
            bytes += read
            if (onProgress == null) continue

            val now = SystemClock.elapsedRealtime()
            if (startedAt == 0L) startedAt = now
            if (now - lastEmitAt >= PROGRESS_INTERVAL_MS) {
                val elapsed = now - startedAt
                if (elapsed >= SPEED_WARMUP_MS) speed = bytes * 1000L / elapsed
                onProgress(DownloadProgress(bytes = bytes, total = total, speed = speed))
                lastEmitAt = now
            }
        }

        output.flush()
        onProgress?.invoke(DownloadProgress(bytes = bytes, total = total, speed = speed))
        return bytes
    }

    private fun openStream(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            YouTube.apiHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
        }
}
