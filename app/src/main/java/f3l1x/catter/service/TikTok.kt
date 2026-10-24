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
import java.net.URLEncoder

object TikTok {

    private const val ENDPOINT = "https://tikwm.com/api/?url="
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Mobile Safari/537.36"

    private const val ATTEMPTS = 2
    private const val RETRY_DELAY_MS = 1100L

    private val postPattern = Regex("/(video|photo)/\\d+")

    fun match(link: String): String? {
        val uri = uriOf(link) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "tiktok.com" && !host.endsWith(".tiktok.com")) return null
        return absolute(uri)
    }

    suspend fun post(target: String): MediaPost = withContext(Dispatchers.IO) {
        val url = canonical(absolute(target))
        Logger.d("tiktok request: $url")

        var failure: Throwable = IOException("Post is not available")

        repeat(ATTEMPTS) { attempt ->
            try {
                return@withContext build(fetch(url))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failure = error
                Logger.w("tiktok request failed, attempt ${attempt + 1}", error)
                if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MS)
            }
        }

        throw failure
    }

    private fun canonical(url: String): String {
        if (postPattern.containsMatchIn(url)) return url

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }

        return try {
            connection.responseCode
            connection.url?.toString()?.substringBefore('?') ?: url
        } catch (failure: Throwable) {
            Logger.w("tiktok redirect failed", failure)
            url
        } finally {
            connection.disconnect()
        }
    }

    private fun fetch(url: String): JSONObject {
        val endpoint = ENDPOINT + URLEncoder.encode(url, "UTF-8")
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code")

            val json = JSONObject(body)
            if (json.optInt("code") != 0) {
                throw IOException(json.optString("msg").ifBlank { "TikTok request failed" })
            }

            return json.optJSONObject("data") ?: throw IOException("Empty response")
        } finally {
            connection.disconnect()
        }
    }

    private fun build(data: JSONObject): MediaPost {
        val author = data.optJSONObject("author")?.optString("unique_id").orEmpty()
        val title = data.optString("title").orEmpty()
        val cover = data.optString("cover").ifBlank { data.optString("origin_cover") }
        val images = data.optJSONArray("images")

        val items = buildList {
            if (images != null && images.length() > 0) {
                for (index in 0 until images.length()) {
                    val url = images.optString(index).orEmpty()
                    if (url.isNotBlank()) add(MediaItem(MediaKind.Photo, url, url))
                }
            } else {
                val url = data.optString("hdplay").ifBlank { data.optString("play") }
                if (url.isNotBlank()) add(MediaItem(MediaKind.Video, url, cover))
            }
        }

        if (items.isEmpty()) throw IOException("No media in this post")

        return MediaPost(
            id = data.optString("id"),
            author = author,
            title = title.ifBlank {
                if (author.isNotBlank()) "$author on TikTok" else "TikTok post"
            }.take(140),
            thumbnailUrl = cover.ifBlank { items.first().thumbnailUrl },
            items = items,
        )
    }

    private fun uriOf(link: String): Uri? {
        val raw = link.trim()
        if (raw.isEmpty()) return null
        return runCatching {
            Uri.parse(if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw")
        }.getOrNull()
    }

    private fun absolute(uri: Uri): String =
        "https://${uri.host}${uri.encodedPath.orEmpty().ifBlank { "/" }}"

    private fun absolute(link: String): String {
        val uri = uriOf(link) ?: return link
        return absolute(uri)
    }
}
