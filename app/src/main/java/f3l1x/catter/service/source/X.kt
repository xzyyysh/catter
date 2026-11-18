package f3l1x.catter.service.source

import android.net.Uri
import f3l1x.catter.service.model.MediaItem
import f3l1x.catter.service.model.MediaKind
import f3l1x.catter.service.model.MediaPost
import f3l1x.catter.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object X {

    private const val API = "https://api.x-downloader.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"

    private const val POLL_INTERVAL_MS = 2_000L
    private const val POLL_LIMIT = 20

    private val statusPath = Regex("/status/(\\d+)", RegexOption.IGNORE_CASE)
    private val idPattern = Regex("^\\d{5,25}$")

    fun match(link: String): String? {
        val uri = uriOf(link) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!isXHost(host)) return null
        return statusPath.find(uri.path.orEmpty())?.groupValues?.get(1)
    }

    suspend fun post(target: String): MediaPost = withContext(Dispatchers.IO) {
        val id = target.takeIf { idPattern.matches(it) }
            ?: statusPath.find(target)?.groupValues?.get(1)
            ?: throw IOException("Post is not available")

        val url = "https://x.com/i/status/$id"
        Logger.d("x request: $id")

        coroutineScope {
            val video = async { media(url, ".mp4") }
            val audio = async { runCatching { media(url, ".mp3") }.getOrNull() }
            build(id, video.await(), audio.await())
        }
    }

    private suspend fun media(url: String, type: String): JSONObject {
        val response = postJson(
            "$API/request",
            JSONObject().put("url", url).put("type", type),
        )
        return awaitReady(response)
    }

    private suspend fun awaitReady(response: JSONObject): JSONObject {
        var current = response
        var attempts = 0

        while (current.optString("status") != "finished") {
            if (current.optString("status") == "error") throw IOException("Media is not available")
            if (++attempts > POLL_LIMIT) throw IOException("Media is not available")
            delay(POLL_INTERVAL_MS)
            current = getJson("$API/download/${current.optString("urlHash")}")
        }

        return current
    }

    private fun build(id: String, video: JSONObject, audio: JSONObject?): MediaPost {
        val filename = video.optString("filename")
        if (filename.isBlank()) throw IOException("Video is not available")

        val title = video.optString("title").ifBlank { "X video" }
        val thumbnail = fileUrl(video, video.optString("thumbnail"))

        return MediaPost(
            id = id,
            author = author(title),
            title = title,
            thumbnailUrl = thumbnail,
            items = buildList {
                add(MediaItem(MediaKind.Video, fileUrl(video, filename), thumbnail))
                if (audio != null) {
                    val audioFile = audio.optString("filename")
                    if (audioFile.isNotBlank()) {
                        add(MediaItem(MediaKind.Audio, fileUrl(audio, audioFile), thumbnail))
                    }
                }
            },
        )
    }

    private fun author(title: String): String {
        val prefix = title.substringBefore(" - ")
        return prefix.takeIf { prefix != title && it.isNotBlank() } ?: ""
    }

    private fun fileUrl(response: JSONObject, filename: String): String {
        val host = response.optString("host").ifBlank { "api.x-downloader.com" }
        return "https://$host/$filename"
    }

    private fun postJson(endpoint: String, body: JSONObject): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun getJson(endpoint: String): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun uriOf(link: String): Uri? {
        val raw = link.trim()
        if (raw.isEmpty()) return null
        return runCatching {
            Uri.parse(if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw")
        }.getOrNull()
    }

    private fun isXHost(host: String): Boolean =
        host == "x.com" || host.endsWith(".x.com") ||
            host == "twitter.com" || host.endsWith(".twitter.com")
}
