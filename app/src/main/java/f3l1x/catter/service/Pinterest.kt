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

object Pinterest {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"

    private const val ATTEMPTS = 2
    private const val RETRY_DELAY_MS = 700L
    private const val MARKER = "{\"data\":{\"v3GetPinQueryv2\""

    private val pinPattern = Regex("/pin/(\\d+)")
    private val hlsPattern = Regex("\\.m3u8$", RegexOption.IGNORE_CASE)

    fun match(link: String): String? {
        val uri = uriOf(link) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!isPinterestHost(host)) return null

        val url = absolute(uri)
        if (isShortHost(host)) return url
        return url.takeIf { pinPattern.containsMatchIn(it) }
    }

    suspend fun post(target: String): MediaPost = withContext(Dispatchers.IO) {
        val url = canonical(target)
        Logger.d("pinterest request: $url")

        var failure: Throwable = IOException("Pin is not available")

        repeat(ATTEMPTS) { attempt ->
            try {
                return@withContext build(url, fetch(url))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failure = error
                Logger.w("pinterest request failed, attempt ${attempt + 1}", error)
                if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MS)
            }
        }

        throw failure
    }

    private fun canonical(url: String): String {
        if (pinPattern.containsMatchIn(url)) return url

        val connection = open(url)
        return try {
            connection.responseCode
            connection.url?.toString() ?: url
        } catch (failure: Throwable) {
            Logger.w("pinterest redirect failed", failure)
            url
        } finally {
            connection.disconnect()
        }
    }

    private fun fetch(url: String): String {
        val connection = open(url)
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            )
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }

    private fun build(url: String, html: String): MediaPost {
        val pinId = pinPattern.find(url)?.groupValues?.get(1)
        val pin = pinObject(html, pinId) ?: throw IOException("Pin is not available")

        val author = author(pin)
        val items = items(pin)
        if (items.isEmpty()) throw IOException("No media in this pin")

        val thumbnail = pin.optJSONObject("images_736x")?.optString("url").orEmpty()
            .ifBlank { pin.optJSONObject("images_orig")?.optString("url").orEmpty() }
            .ifBlank { items.first().thumbnailUrl }

        return MediaPost(
            id = pin.optString("entityId").ifBlank { pinId.orEmpty() },
            author = author,
            title = title(pin, author),
            thumbnailUrl = thumbnail,
            items = items,
        )
    }

    private fun pinObject(html: String, pinId: String?): JSONObject? {
        var fallback: JSONObject? = null
        var index = html.indexOf(MARKER)

        while (index >= 0) {
            val end = matchingBrace(html, index)
            if (end > index) {
                val root = runCatching { JSONObject(html.substring(index, end + 1)) }.getOrNull()
                val pin = root
                    ?.optJSONObject("data")
                    ?.optJSONObject("v3GetPinQueryv2")
                    ?.optJSONObject("data")

                if (pin != null) {
                    if (pinId != null && pin.optString("entityId") == pinId) return pin
                    if (fallback == null) fallback = pin
                }
            }
            index = html.indexOf(MARKER, index + MARKER.length)
        }

        return fallback
    }

    private fun matchingBrace(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false

        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return index
                    }
                }
            }
        }

        return -1
    }

    private fun author(pin: JSONObject): String =
        pin.optJSONObject("pinner")?.optString("username").orEmpty()
            .ifBlank { pin.optJSONObject("nativeCreator")?.optString("username").orEmpty() }
            .ifBlank { pin.optJSONObject("closeupAttribution")?.optString("fullName").orEmpty() }

    private fun items(pin: JSONObject): List<MediaItem> {
        val carousel = carouselItems(pin)
        if (carousel.isNotEmpty()) return carousel

        val story = storyItems(pin)
        if (story.isNotEmpty()) {
            val original = original(pin)
            if (story.size == 1 && original != null && story.first().kind != MediaKind.Video) {
                return listOf(original)
            }
            return story
        }

        return listOfNotNull(original(pin))
    }

    private fun carouselItems(pin: JSONObject): List<MediaItem> {
        val slots = pin.optJSONObject("carouselData")?.optJSONArray("carouselSlots") ?: return emptyList()

        return buildList {
            for (index in 0 until slots.length()) {
                val slot = slots.optJSONObject(index) ?: continue
                val url = slot.optJSONObject("images_1200x")?.optString("url").orEmpty()
                    .ifBlank { slot.optJSONObject("images_736x")?.optString("url").orEmpty() }
                if (url.isNotBlank()) add(imageItem(url))
            }
        }
    }

    private fun storyItems(pin: JSONObject): List<MediaItem> {
        val pages = pin.optJSONObject("storyPinData")?.optJSONArray("pages") ?: return emptyList()

        return buildList {
            for (pageIndex in 0 until pages.length()) {
                val blocks = pages.optJSONObject(pageIndex)?.optJSONArray("blocks") ?: continue
                for (blockIndex in 0 until blocks.length()) {
                    val block = blocks.optJSONObject(blockIndex) ?: continue
                    blockItem(block)?.let { add(it) }
                }
            }
        }
    }

    private fun blockItem(block: JSONObject): MediaItem? {
        block.optJSONObject("videoDataV2")?.let { videoItem(it) }?.let { return it }

        val image = block.optJSONObject("images_750x")?.optString("url").orEmpty()
        if (image.isNotBlank()) return imageItem(image)
        return null
    }

    private fun original(pin: JSONObject): MediaItem? {
        val url = pin.optJSONObject("images_orig")?.optString("url").orEmpty()
            .ifBlank { pin.optJSONObject("images_736x")?.optString("url").orEmpty() }
        if (url.isBlank()) return null
        return imageItem(url)
    }

    private fun videoItem(data: JSONObject): MediaItem? {
        val detail = data.optJSONObject("videoList720P")?.optJSONObject("v720P")
            ?: data.optJSONObject("videoListMobile")?.optJSONObject("vHLSV3MOBILE")
            ?: data.optJSONObject("videoList")?.optJSONObject("vHLSV3MOBILE")

        val url = detail?.optString("url").orEmpty()
        if (url.isBlank()) return null

        return MediaItem(MediaKind.Video, mp4(url), detail?.optString("thumbnail").orEmpty())
    }

    private fun mp4(url: String): String {
        if (!hlsPattern.containsMatchIn(url)) return url

        return url
            .replace("/videos/iht/hls/", "/videos/mc/720p/")
            .replace(hlsPattern, ".mp4")
    }

    private fun imageItem(url: String): MediaItem {
        val path = url.substringBefore('?')
        val kind = if (path.endsWith(".gif", ignoreCase = true)) MediaKind.Gif else MediaKind.Photo
        return MediaItem(kind, url, url)
    }

    private fun title(pin: JSONObject, author: String): String {
        for (key in listOf("title", "gridTitle", "description")) {
            val line = pin.optString(key)
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                .orEmpty()
            if (line.isNotEmpty()) return line.take(140)
        }

        // Fallback titles also become file names, so they stay in English.
        return if (author.isNotBlank()) "$author on Pinterest" else "Pinterest pin"
    }

    private fun uriOf(link: String): Uri? {
        val raw = link.trim()
        if (raw.isEmpty()) return null
        return runCatching {
            Uri.parse(if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw")
        }.getOrNull()
    }

    private fun absolute(uri: Uri): String =
        "https://${uri.host}${uri.encodedPath.orEmpty().ifBlank { "/" }}" +
            uri.encodedQuery?.let { "?$it" }.orEmpty()

    private fun isPinterestHost(host: String): Boolean =
        host == "pinterest.com" ||
            host.startsWith("pinterest.") ||
            host.contains(".pinterest.") ||
            isShortHost(host)

    private fun isShortHost(host: String): Boolean =
        host == "pin.it" || host.endsWith(".pin.it")
}
