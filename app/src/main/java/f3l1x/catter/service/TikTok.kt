package f3l1x.catter.service

import android.net.Uri
import f3l1x.catter.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object TikTok {

    private const val DESKTOP_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"
    private const val MOBILE_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

    private const val REFERER = "https://www.tiktok.com/"
    private const val HOME = "https://www.tiktok.com/"
    private const val CHAIN_TOKEN = "tt_chain_token"
    private const val ATTEMPTS = 2

    private val cookies: CookieManager = run {
        val installed = CookieHandler.getDefault()
        if (installed is CookieManager) {
            installed
        } else {
            CookieManager(null, CookiePolicy.ACCEPT_ALL).also { CookieHandler.setDefault(it) }
        }
    }

    private val scriptPattern = Regex(
        "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\"[^>]*>(.*?)</script>",
        RegexOption.DOT_MATCHES_ALL,
    )

    fun match(link: String): String? {
        val uri = uriOf(link) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "tiktok.com" && !host.endsWith(".tiktok.com")) return null
        return absolute(uri)
    }

    suspend fun post(target: String): MediaPost = withContext(Dispatchers.IO) {
        val url = absolute(target)
        val agents = if (url.contains("/photo/")) {
            listOf(MOBILE_AGENT, DESKTOP_AGENT)
        } else {
            listOf(DESKTOP_AGENT, MOBILE_AGENT)
        }

        Logger.d("tiktok request: $url")

        repeat(ATTEMPTS) { attempt ->
            val agent = agents.getOrElse(attempt) { agents.last() }
            val item = findItem(fetch(url, agent))
            if (item != null) {
                ensureSession()
                return@withContext build(item, agent)
            }

            Logger.w("tiktok payload missing, attempt ${attempt + 1}")
        }

        throw IOException("Post is not available")
    }

    private fun ensureSession() {
        if (stored().any { it.name == CHAIN_TOKEN }) return

        runCatching { fetch(HOME, DESKTOP_AGENT) }
            .onFailure { Logger.w("tiktok session warmup failed", it) }

        Logger.d("tiktok session: ${stored().joinToString(",") { it.name }}")
    }

    private fun stored(): List<HttpCookie> = cookies.cookieStore.get(URI(HOME))

    private fun cookieHeader(): String = stored().joinToString("; ") { "${it.name}=${it.value}" }

    private fun build(item: JSONObject, agent: String): MediaPost {
        val author = item.optJSONObject("author")?.optString("uniqueId").orEmpty()
        val desc = item.optString("desc").orEmpty()
        val video = item.optJSONObject("video")
        val images = item.optJSONObject("imagePost")?.optJSONArray("images")

        val items = buildList {
            if (images != null && images.length() > 0) {
                for (index in 0 until images.length()) {
                    val url = images.optJSONObject(index)
                        ?.optJSONObject("imageURL")
                        ?.optJSONArray("urlList")
                        ?.optString(0)
                        .orEmpty()
                    if (url.isNotBlank()) add(MediaItem(MediaKind.Photo, url, url))
                }
            } else {
                val url = videoUrl(video)
                if (url.isNotBlank()) {
                    add(MediaItem(MediaKind.Video, url, video?.optString("cover").orEmpty()))
                }
            }
        }

        if (items.isEmpty()) throw IOException("No media in this post")

        val cover = item.optJSONObject("imagePost")?.optString("cover").orEmpty()
            .ifBlank { video?.optString("cover").orEmpty() }
            .ifBlank { items.first().thumbnailUrl }

        return MediaPost(
            id = item.optString("id"),
            author = author,
            title = title(desc, author),
            thumbnailUrl = cover,
            items = items,
            headers = downloadHeaders(agent),
        )
    }

    private fun videoUrl(video: JSONObject?): String {
        if (video == null) return ""

        var best = ""
        var bestWidth = -1
        val bitrates = video.optJSONArray("bitrateInfo")

        if (bitrates != null) {
            for (index in 0 until bitrates.length()) {
                val address = bitrates.optJSONObject(index)?.optJSONObject("PlayAddr") ?: continue
                val url = address.optJSONArray("UrlList")?.optString(0).orEmpty()
                val width = address.optInt("Width")
                if (url.isNotBlank() && width > bestWidth) {
                    best = url
                    bestWidth = width
                }
            }
        }

        return best.ifBlank { video.optString("playAddr") }
    }

    private fun downloadHeaders(agent: String): Map<String, String> = buildMap {
        put("User-Agent", agent)
        put("Referer", REFERER)

        val cookie = cookieHeader()
        if (cookie.isNotBlank()) put("Cookie", cookie)
    }

    private fun fetch(url: String, agent: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", agent)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }

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

    private fun findItem(html: String): JSONObject? {
        val match = scriptPattern.find(html) ?: return null
        val json = runCatching { JSONObject(match.groupValues[1]) }.getOrNull() ?: return null
        return search(json)
    }

    private fun search(node: Any?): JSONObject? {
        when (node) {
            is JSONObject -> {
                val item = node.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
                if (item != null) return item

                val keys = node.keys()
                while (keys.hasNext()) {
                    search(node.opt(keys.next()))?.let { return it }
                }
            }

            is JSONArray -> {
                for (index in 0 until node.length()) {
                    search(node.opt(index))?.let { return it }
                }
            }
        }
        return null
    }

    private fun title(desc: String, author: String): String {
        val line = desc.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return when {
            line.isNotEmpty() -> line.take(140)
            author.isNotBlank() -> "$author on TikTok"
            else -> "TikTok post"
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
}
