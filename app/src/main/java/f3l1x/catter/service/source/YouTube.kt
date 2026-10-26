package f3l1x.catter.service.source

import android.net.Uri
import android.os.SystemClock
import f3l1x.catter.service.download.DownloadTicket
import f3l1x.catter.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

enum class Quality(
    val label: String,
    val format: String,
    val audioBitrate: Int,
    val videoQuality: Int,
) {
    Video1080("1080p", "mp4", 128, 1080),
    Video720("720p", "mp4", 128, 720),
    Video360("360p", "mp4", 128, 360),
    Video240("240p", "mp4", 128, 240),
    Video144("144p", "mp4", 128, 144),
    Audio320("320kbps", "mp3", 320, 720),
    Audio256("256kbps", "mp3", 256, 720),
    Audio128("128kbps", "mp3", 128, 720);

    val isAudio: Boolean get() = format == "mp3"

    val extension: String get() = format

    val mimeType: String get() = if (isAudio) "audio/mpeg" else "video/mp4"

    companion object {
        val video: List<Quality> = entries.filterNot { it.isAudio }
        val audio: List<Quality> = entries.filter { it.isAudio }
    }
}

data class VideoDetails(
    val id: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
)

object YouTube {

    const val CATEGORY = "cnv.cx"

    private const val KEY_ENDPOINT = "https://cnv.cx/v2/sanity/key"
    private const val CONVERT_ENDPOINT = "https://cnv.cx/v2/converter"
    private const val OEMBED_ENDPOINT = "https://www.youtube.com/oembed"
    private const val ORIGIN = "https://iframe.y2meta-uk.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Mobile Safari/537.36"

    private const val DETAILS_TTL_MS = 5 * 60 * 1000L
    private const val KEY_TTL_MS = 5 * 60 * 1000L
    private const val TICKET_FALLBACK_TTL_MS = 60 * 1000L
    private const val TICKET_SAFETY_MS = 20 * 1000L

    private val allowedHosts = setOf(
        "youtu.be",
        "www.youtu.be",
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtube-nocookie.com",
        "www.youtube-nocookie.com",
    )

    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")

    private val detailsCache = ConcurrentHashMap<String, Cached<VideoDetails>>()
    private val keyCache = ConcurrentHashMap<String, Cached<String>>()
    private val ticketCache = ConcurrentHashMap<String, Cached<DownloadTicket>>()

    internal val apiHeaders: Map<String, String>
        get() = mapOf(
            "Origin" to ORIGIN,
            "Referer" to "$ORIGIN/",
            "User-Agent" to USER_AGENT,
            "Accept" to "*/*",
        )

    fun videoId(link: String): String? {
        val raw = link.trim()
        if (raw.isEmpty()) return null

        val uri = runCatching {
            Uri.parse(if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw")
        }.getOrNull() ?: return null

        val host = uri.host?.lowercase() ?: return null
        if (host !in allowedHosts && !host.endsWith(".youtube.com")) return null

        val segments = uri.pathSegments.orEmpty()
        val candidate = when {
            host.endsWith("youtu.be") -> segments.firstOrNull()
            !uri.getQueryParameter("v").isNullOrBlank() -> uri.getQueryParameter("v")
            segments.size >= 2 && segments[0] in setOf("shorts", "embed", "live", "v", "watch") -> segments[1]
            else -> null
        }

        return candidate?.takeIf { idPattern.matches(it) }
    }

    suspend fun details(id: String): VideoDetails = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        detailsCache[id]?.takeIf { it.expiresAt > now }?.let { cached ->
            Logger.d("details cache hit: $id")
            return@withContext cached.value
        }

        Logger.d("details request: $id")
        try {
            val watchUrl = URLEncoder.encode("https://www.youtube.com/watch?v=$id", "UTF-8")
            val body = httpGet("$OEMBED_ENDPOINT?url=$watchUrl&format=json", apiHeaders)
            val json = JSONObject(body)

            val result = VideoDetails(
                id = id,
                // Fallback titles also become file names, so they stay in English.
                title = json.optString("title").ifBlank { "YouTube video" },
                author = json.optString("author_name"),
                thumbnailUrl = "https://i.ytimg.com/vi/$id/maxresdefault.jpg",
            )
            detailsCache[id] = Cached(result, now + DETAILS_TTL_MS)
            result
        } catch (throwable: Throwable) {
            Logger.e("details failed: $id", throwable)
            throw throwable
        }
    }

    suspend fun prefetchKey(id: String) = withContext(Dispatchers.IO) {
        runCatching { keyFor(id) }
            .onFailure { Logger.w("key prefetch failed: $id", it) }
        Unit
    }

    suspend fun ticket(id: String, quality: Quality): DownloadTicket = withContext(Dispatchers.IO) {
        val cacheKey = "$id:${quality.name}"
        val now = System.currentTimeMillis()
        ticketCache[cacheKey]?.takeIf { it.expiresAt > now }?.let { cached ->
            Logger.d("ticket cache hit: $cacheKey")
            return@withContext cached.value
        }

        Logger.d("ticket request: $cacheKey")
        try {
            val ticket = runCatching { convert(id, quality, keyFor(id)) }
                .getOrElse { failure ->
                    Logger.w("convert failed, refreshing key: $cacheKey", failure)
                    keyCache.remove(id)
                    convert(id, quality, keyFor(id))
                }

            ticketCache[cacheKey] = Cached(ticket, expiryOf(ticket.url))
            ticket
        } catch (throwable: Throwable) {
            Logger.e("ticket failed: $cacheKey", throwable)
            throw throwable
        }
    }

    private fun keyFor(id: String): String {
        val now = System.currentTimeMillis()
        keyCache[id]?.takeIf { it.expiresAt > now }?.let { cached ->
            Logger.d("key cache hit: $id")
            return cached.value
        }

        val body = httpGet("$KEY_ENDPOINT?id=$id", apiHeaders)
        val key = JSONObject(body).optString("key")
        if (key.isBlank()) throw IOException("Converter key unavailable")

        keyCache[id] = Cached(key, now + KEY_TTL_MS)
        return key
    }

    private fun convert(id: String, quality: Quality, key: String): DownloadTicket {
        val form = listOf(
            "link" to "https://youtu.be/$id",
            "format" to quality.format,
            "audioBitrate" to quality.audioBitrate.toString(),
            "videoQuality" to quality.videoQuality.toString(),
            "filenameStyle" to "pretty",
            "vCodec" to "h264",
        ).joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }

        val body = httpPost(CONVERT_ENDPOINT, form, apiHeaders + ("key" to key))
        val json = JSONObject(body)
        if (json.optBoolean("error")) {
            throw IOException(json.optString("errorMsg").ifBlank { "Conversion failed" })
        }

        val url = json.optString("url")
        if (url.isBlank()) throw IOException("No download url returned")

        return DownloadTicket(url = url, fileName = json.optString("filename"), headers = apiHeaders)
    }

    private fun expiryOf(url: String): Long {
        val exp = runCatching { Uri.parse(url).getQueryParameter("exp")?.toLongOrNull() }.getOrNull()
        return if (exp != null) {
            exp - TICKET_SAFETY_MS
        } else {
            System.currentTimeMillis() + TICKET_FALLBACK_TTL_MS
        }
    }

    internal fun httpGet(url: String, headers: Map<String, String>): String =
        request(url, "GET", null, headers)

    internal fun httpPost(url: String, body: String, headers: Map<String, String>): String =
        request(url, "POST", body, headers)

    private fun request(url: String, method: String, body: String?, headers: Map<String, String>): String {
        val shortUrl = url.substringBefore('?')
        val startedAt = SystemClock.elapsedRealtime()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
        }

        try {
            connection.connect()
            val connectedAt = SystemClock.elapsedRealtime()

            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray()) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val finishedAt = SystemClock.elapsedRealtime()

            Logger.d(
                "$method $shortUrl -> $code " +
                    "connect=${connectedAt - startedAt}ms read=${finishedAt - connectedAt}ms",
            )
            if (code !in 200..299) throw IOException("HTTP $code: ${text.take(140)}")
            return text
        } catch (throwable: Throwable) {
            Logger.e("$method $shortUrl failed after ${SystemClock.elapsedRealtime() - startedAt}ms", throwable)
            throw throwable
        } finally {
            connection.disconnect()
        }
    }

    private data class Cached<T>(val value: T, val expiresAt: Long)
}
