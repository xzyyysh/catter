package f3l1x.catter.service

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import f3l1x.catter.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object Downloader {

    private const val BUFFER_SIZE = 64 * 1024

    val requiresLegacyPermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    suspend fun save(
        context: Context,
        ticket: DownloadTicket,
        quality: Quality,
        title: String,
        artist: String,
    ): String = withContext(Dispatchers.IO) {
        val fileName = fileName(title, artist, quality)
        Logger.d("download start: $fileName")

        val connection = openStream(ticket.url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")

            val location = connection.inputStream.use { input ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStore(context, input, fileName, quality, title, artist)
                } else {
                    saveToPublicDirectory(context, input, fileName, quality)
                }
            }

            Logger.i("download saved: $location")
            location
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
        title: String,
        artist: String,
    ): String {
        val resolver = context.contentResolver
        val collection = if (quality.isAudio) {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, quality.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (quality.isAudio) {
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
            } else {
                put(MediaStore.Video.Media.TITLE, title)
                put(MediaStore.Video.Media.ARTIST, artist)
            }
        }

        val uri = resolver.insert(collection, values) ?: throw IOException("Cannot create media entry")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                input.copyTo(output, BUFFER_SIZE)
            } ?: throw IOException("Cannot open output stream")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (throwable: Throwable) {
            resolver.delete(uri, null, null)
            throw throwable
        }

        return "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
    }

    private fun saveToPublicDirectory(
        context: Context,
        input: InputStream,
        fileName: String,
        quality: Quality,
    ): String {
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!directory.exists()) directory.mkdirs()

        val file = uniqueFile(directory, fileName)
        file.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }

        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(quality.mimeType),
            null,
        )

        return file.absolutePath
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

    private fun openStream(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            YouTube.apiHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
        }
}
