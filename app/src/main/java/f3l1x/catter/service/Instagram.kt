package f3l1x.catter.service

import android.net.Uri
import f3l1x.catter.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Instagram {

    private const val ORIGIN = "https://www.instagram.com"
    private const val DESKTOP_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"

    private const val MEDIA_TYPE_PHOTO = 1
    private const val MEDIA_TYPE_VIDEO = 2
    private const val ATTEMPTS = 2
    private const val RETRY_DELAY_MS = 700L

    private val paths = setOf("p", "reel", "reels", "tv")
    private val codePattern = Regex("^[A-Za-z0-9_-]{5,24}$")
    private val scriptPattern =
        Regex("<script type=\"application/json\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)

    private val headers = mapOf(
        "User-Agent" to DESKTOP_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
        "sec-ch-ua" to "\"Not:A-Brand\";v=\"8\", \"Chromium\";v=\"156\", \"Google Chrome\";v=\"156\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Windows\"",
        "sec-fetch-dest" to "document",
        "sec-fetch-mode" to "navigate",
        "sec-fetch-site" to "none",
        "sec-fetch-user" to "?1",
        "upgrade-insecure-requests" to "1",
    )

    fun match(link: String): String? {
        val uri = uriOf(link) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!isInstagramHost(host)) return null

        return shortcode(link) ?: absolute(uri)
    }

    suspend fun post(target: String): MediaPost = withContext(Dispatchers.IO) {
        val code = if (codePattern.matches(target)) target else shortcode(target)
        val url = if (code != null) "$ORIGIN/p/$code/" else absolute(target)

        Logger.d("instagram request: $url")

        repeat(ATTEMPTS) { attempt ->
            val response = fetch(url)
            val resolved = code ?: shortcode(response.url)
            val node = resolved?.let { findPost(response.body, it) }
            if (node != null) return@withContext build(node, resolved)

            Logger.w("instagram payload missing, attempt ${attempt + 1}")
            if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MS)
        }

        throw IOException("Post is not available")
    }

    fun shortcode(link: String): String? {
        val uri = uriOf(link) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!isInstagramHost(host)) return null

        if (host.startsWith("l.")) {
            val target = uri.getQueryParameter("u") ?: return null
            return shortcode(target)
        }

        val segments = uri.pathSegments.orEmpty()
        val marker = segments.indexOfFirst { it in paths }
        val candidate = when {
            marker >= 0 -> segments.getOrNull(marker + 1)
            segments.size == 1 -> segments.firstOrNull()
            else -> null
        }

        return candidate?.takeIf { codePattern.matches(it) }
    }

    private fun build(node: JSONObject, shortcode: String): MediaPost {
        val author = node.optJSONObject("user")?.optString("username").orEmpty()
        val caption = node.optJSONObject("caption")?.optString("text").orEmpty()
        val items = items(node)
        if (items.isEmpty()) throw IOException("No media in this post")

        return MediaPost(
            id = shortcode,
            author = author,
            title = title(caption, author),
            thumbnailUrl = node.optString("display_uri").ifBlank { items.first().thumbnailUrl },
            items = items,
        )
    }

    private fun fetch(url: String): Response {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code")
            return Response(body, connection.url?.toString().orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun findPost(html: String, shortcode: String): JSONObject? {
        for (match in scriptPattern.findAll(html)) {
            val body = match.groupValues[1]
            if (!body.contains(shortcode)) continue

            val node = runCatching { JSONObject(body) }.getOrNull()
                ?: runCatching { JSONArray(body) }.getOrNull()
                ?: continue

            search(node, shortcode)?.let { return it }
        }
        return null
    }

    private fun search(node: Any?, shortcode: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                if (node.optString("code") == shortcode && node.has("media_type")) return node
                val keys = node.keys()
                while (keys.hasNext()) {
                    search(node.opt(keys.next()), shortcode)?.let { return it }
                }
            }

            is JSONArray -> {
                for (index in 0 until node.length()) {
                    search(node.opt(index), shortcode)?.let { return it }
                }
            }
        }
        return null
    }

    private fun items(node: JSONObject): List<MediaItem> {
        val carousel = node.optJSONArray("carousel_media")
        if (carousel != null && carousel.length() > 0) {
            return (0 until carousel.length()).mapNotNull { item(carousel.optJSONObject(it)) }
        }

        return listOfNotNull(item(node))
    }

    private fun item(node: JSONObject?): MediaItem? {
        if (node == null) return null

        val photo = node.optJSONObject("image_versions2")
            ?.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optString("url")
            .orEmpty()

        val video = node.optJSONArray("video_versions")
            ?.optJSONObject(0)
            ?.optString("url")
            .orEmpty()

        val thumbnail = node.optString("display_uri").ifBlank { photo }

        return when {
            node.optInt("media_type") == MEDIA_TYPE_VIDEO && video.isNotBlank() ->
                MediaItem(MediaKind.Video, video, thumbnail)

            node.optInt("media_type") == MEDIA_TYPE_PHOTO && photo.isNotBlank() ->
                MediaItem(MediaKind.Photo, photo, thumbnail)

            video.isNotBlank() -> MediaItem(MediaKind.Video, video, thumbnail)
            photo.isNotBlank() -> MediaItem(MediaKind.Photo, photo, thumbnail)
            else -> null
        }
    }

    private fun title(caption: String, author: String): String {
        val line = caption.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return when {
            line.isNotEmpty() -> line.take(140)
            author.isNotBlank() -> "$author on Instagram"
            else -> "Instagram post"
        }
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

    private fun isInstagramHost(host: String): Boolean =
        host == "instagram.com" || host.endsWith(".instagram.com") || host == "instagr.am"

    private data class Response(val body: String, val url: String)
}
