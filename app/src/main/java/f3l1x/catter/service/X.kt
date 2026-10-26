package f3l1x.catter.service

import android.net.Uri
import f3l1x.catter.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object X {

    private const val ENDPOINT = "https://cdn.syndication.twimg.com/tweet-result?id="
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"

    private const val ATTEMPTS = 2
    private const val RETRY_DELAY_MS = 700L

    private val statusPath = Regex("/status/(\\d+)", RegexOption.IGNORE_CASE)
    private val idPattern = Regex("^\\d{5,25}$")
    private val urlPattern = Regex("https?://\\S+")

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

        Logger.d("x request: $id")
        var failure: Throwable = IOException("Post is not available")

        repeat(ATTEMPTS) { attempt ->
            try {
                return@withContext build(id, fetch(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failure = error
                Logger.w("x request failed, attempt ${attempt + 1}", error)
                if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MS)
            }
        }

        throw failure
    }

    private fun fetch(id: String): JSONObject {
        val connection = (URL("$ENDPOINT$id&lang=en&token=x").openConnection() as HttpURLConnection).apply {
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

    private fun build(id: String, tweet: JSONObject): MediaPost {
        val media = videoMedia(tweet) ?: throw IOException("No video in this post")
        val url = bestMp4(media) ?: throw IOException("Video is not available")

        val author = tweet.optJSONObject("user")?.optString("screen_name").orEmpty()
        val thumbnail = media.optString("media_url_https")

        return MediaPost(
            id = tweet.optString("id_str").ifBlank { id },
            author = author,
            title = title(tweet.optString("text"), author),
            thumbnailUrl = thumbnail,
            items = listOf(MediaItem(MediaKind.Video, url, thumbnail.ifBlank { url })),
        )
    }

    private fun videoMedia(tweet: JSONObject): JSONObject? {
        val media = tweet.optJSONArray("mediaDetails") ?: return null
        for (index in 0 until media.length()) {
            val item = media.optJSONObject(index) ?: continue
            if (item.optJSONObject("video_info") != null) return item
        }
        return null
    }

    private fun bestMp4(media: JSONObject): String? {
        val variants = media.optJSONObject("video_info")?.optJSONArray("variants") ?: return null

        var best: String? = null
        var bitrate = -1

        for (index in 0 until variants.length()) {
            val variant = variants.optJSONObject(index) ?: continue
            if (variant.optString("content_type") != "video/mp4") continue

            val url = variant.optString("url")
            if (url.isBlank()) continue

            val current = variant.optInt("bitrate")
            if (current > bitrate) {
                bitrate = current
                best = url
            }
        }

        return best
    }

    private fun title(text: String, author: String): String {
        val line = text.lineSequence()
            .map { it.replace(urlPattern, "").trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()

        return when {
            line.isNotEmpty() -> line.take(140)
            // Fallback titles also become file names, so they stay in English.
            author.isNotBlank() -> "$author on X"
            else -> "X video"
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
