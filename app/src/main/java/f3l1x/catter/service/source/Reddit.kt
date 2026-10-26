package f3l1x.catter.service.source

import android.net.Uri
import f3l1x.catter.service.model.MediaItem
import f3l1x.catter.service.model.MediaKind
import f3l1x.catter.service.model.MediaPost
import f3l1x.catter.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object Reddit {

    private const val ORIGIN = "https://www.reddit.com"
    private const val MEDIA_ORIGIN = "https://v.redd.it"
    private const val ARCHIVE = "https://arctic-shift.photon-reddit.com/api/posts/ids?ids="
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"

    private val postPath = Regex("/comments/([a-z0-9]+)", RegexOption.IGNORE_CASE)
    private val galleryPath = Regex("/gallery/([a-z0-9]+)", RegexOption.IGNORE_CASE)
    private val sharePath = Regex("/(?:r|u|user)/[^/]+/s/[A-Za-z0-9]+", RegexOption.IGNORE_CASE)
    private val shortPath = Regex("^/[A-Za-z0-9]+/?$")
    private val postIdPattern = Regex("^[a-z0-9]{4,12}$", RegexOption.IGNORE_CASE)
    private val videoIdPattern = Regex("v\\.redd\\.it/([A-Za-z0-9]+)")

    private val postTag = Regex("<shreddit-post(?=[\\s>])[^>]*>")
    private val playerTag = Regex("<shreddit-player(?=[\\s>])[^>]*>")
    private val listItem = Regex("<li(?=[\\s>])")
    private val attributePattern = Regex("(?<![\\w-])([A-Za-z-]+)=\"([^\"]*)\"")
    private val originalPattern = Regex("https://i\\.redd\\.it/[A-Za-z0-9]+\\.[A-Za-z0-9]+")
    private val previewPattern = Regex("https://(?:preview|cf\\.preview|external-preview)\\.redd\\.it/[^\"\\s<]+")
    private val ogImagePattern = Regex("<meta[^>]*property=\"og:image\"[^>]*>")
    private val representationPattern =
        Regex("<Representation\\b([^>]*)>(.*?)</Representation>", RegexOption.DOT_MATCHES_ALL)
    private val baseUrlPattern = Regex("<BaseURL>([^<]+)</BaseURL>")

    private val mediaHosts = setOf(
        "i.redd.it",
        "v.redd.it",
        "preview.redd.it",
        "external-preview.redd.it",
        "packaged-media.redd.it",
        "styles.redditmedia.com",
    )

    private val imageHosts = setOf(
        "i.redd.it",
        "preview.redd.it",
        "cf.preview.redd.it",
        "external-preview.redd.it",
        "i.imgur.com",
        "imgur.com",
        "preview.imgur.com",
    )

    fun match(link: String): String? {
        val uri = uriOf(link) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host in mediaHosts) return null
        if (!isRedditHost(host)) return null

        val path = uri.path.orEmpty()
        val post = postPath.containsMatchIn(path) ||
            galleryPath.containsMatchIn(path) ||
            sharePath.containsMatchIn(path)
        val short = isShortHost(host) && shortPath.matches(path)
        if (!post && !short) return null

        return absolute(uri)
    }

    suspend fun post(target: String): MediaPost = withContext(Dispatchers.IO) {
        val id = postId(target)

        if (id != null) {
            try {
                Logger.d("reddit archive: $id")
                return@withContext archivePost(id, fetchArchive(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.w("reddit archive failed: $id", error)
            }
        }

        try {
            val page = fetchPage(absolute(target))
            Logger.d("reddit request: ${page.url} (${page.body.length} chars)")
            build(page.url, page.body)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.w("reddit request failed", error)
            throw error
        }
    }

    private fun postId(target: String): String? {
        if (postIdPattern.matches(target)) return target

        postKey(uriOf(target)?.path.orEmpty())?.let { return it }

        val finalUrl = canonicalUrl(target) ?: return null
        return postKey(uriOf(finalUrl)?.path.orEmpty())
    }

    private fun canonicalUrl(target: String): String? {
        val connection = open(absolute(target))
        return try {
            connection.responseCode
            connection.url?.toString()
        } catch (failure: Throwable) {
            Logger.w("reddit redirect failed", failure)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchArchive(id: String): JSONObject {
        val connection = open(ARCHIVE + id, "application/json")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")

            val data = JSONObject(readBody(connection)).optJSONArray("data")
            return data?.optJSONObject(0) ?: throw IOException("Post is not available")
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun archivePost(id: String, post: JSONObject): MediaPost {
        val author = post.optString("author")
            .takeUnless { it.isBlank() || it == "[deleted]" }
            .orEmpty()

        val items = archiveItems(mediaSource(post))
        if (items.isEmpty()) throw IOException("No media in this post")

        return MediaPost(
            id = post.optString("id").ifBlank { id },
            author = author,
            title = title(post.optString("title"), author),
            thumbnailUrl = previewUrl(post).ifBlank { items.first().thumbnailUrl },
            items = items,
        )
    }

    private fun mediaSource(post: JSONObject): JSONObject {
        val parent = post.optJSONArray("crosspost_parent_list")?.optJSONObject(0)
        return if (parent != null && hasMedia(parent)) parent else post
    }

    private fun hasMedia(post: JSONObject): Boolean =
        post.optBoolean("is_gallery") ||
            redditVideo(post) != null ||
            post.optString("url_overridden_by_dest").isNotBlank()

    private suspend fun archiveItems(post: JSONObject): List<MediaItem> {
        archiveGallery(post).takeIf { it.isNotEmpty() }?.let { return it }
        archiveVideo(post)?.let { return listOf(it) }
        archiveImage(post)?.let { return listOf(it) }
        return emptyList()
    }

    private fun archiveGallery(post: JSONObject): List<MediaItem> {
        if (!post.optBoolean("is_gallery")) return emptyList()
        val metadata = post.optJSONObject("media_metadata") ?: return emptyList()
        val order = post.optJSONObject("gallery_data")?.optJSONArray("items") ?: return emptyList()

        return buildList {
            for (index in 0 until order.length()) {
                val mediaId = order.optJSONObject(index)?.optString("media_id").orEmpty()
                if (mediaId.isBlank()) continue
                archiveGalleryItem(metadata.optJSONObject(mediaId))?.let { add(it) }
            }
        }
    }

    private fun archiveGalleryItem(metadata: JSONObject?): MediaItem? {
        if (metadata == null) return null

        val sizes = metadata.optJSONObject("s")
        val direct = sizes?.optString("u").orEmpty()
        val type = metadata.optString("e")

        val url = when {
            type.equals("AnimatedImage", ignoreCase = true) ->
                sizes?.optString("gif").orEmpty()
                    .ifBlank { sizes?.optString("mp4").orEmpty() }
                    .ifBlank { direct }

            type.equals("Video", ignoreCase = true) ->
                sizes?.optString("mp4").orEmpty().ifBlank { direct }

            else -> direct
        }
        if (url.isBlank()) return null

        val path = url.substringBefore('?')
        val kind = when {
            type.equals("Video", ignoreCase = true) -> MediaKind.Video
            path.endsWith(".gif", ignoreCase = true) -> MediaKind.Gif
            path.endsWith(".mp4", ignoreCase = true) -> MediaKind.Video
            else -> MediaKind.Photo
        }
        val thumbnail = metadata.optJSONArray("p")?.optJSONObject(0)?.optString("u").orEmpty()
        return MediaItem(kind, url, thumbnail.ifBlank { url })
    }

    private suspend fun archiveVideo(post: JSONObject): MediaItem? {
        val video = redditVideo(post) ?: return null
        val thumbnail = previewUrl(post)
        val id = videoIdPattern.find(video.optString("url"))?.groupValues?.get(1)
            ?: videoIdPattern.find(video.optString("dash_url"))?.groupValues?.get(1)
            ?: videoIdPattern.find(post.optString("url"))?.groupValues?.get(1)
            ?: videoIdPattern.find(post.optString("url_overridden_by_dest"))?.groupValues?.get(1)

        if (id != null) {
            try {
                return muxedVideo(id, thumbnail)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.w("reddit video manifest failed: $id", error)
            }
        }

        val fallback = video.optString("fallback_url")
        if (fallback.isBlank()) return null
        return MediaItem(MediaKind.Video, fallback, thumbnail.ifBlank { fallback })
    }

    private fun redditVideo(post: JSONObject): JSONObject? =
        post.optJSONObject("secure_media")?.optJSONObject("reddit_video")
            ?: post.optJSONObject("media")?.optJSONObject("reddit_video")

    private fun archiveImage(post: JSONObject): MediaItem? {
        val direct = post.optString("url_overridden_by_dest")
            .ifBlank { post.optString("url") }
        val preview = previewUrl(post)
        val url = direct.takeIf { isImageUrl(it) } ?: preview
        if (url.isBlank()) return null

        val path = url.substringBefore('?')
        val kind = when {
            path.endsWith(".gif", ignoreCase = true) -> MediaKind.Gif
            path.endsWith(".mp4", ignoreCase = true) -> MediaKind.Video
            else -> MediaKind.Photo
        }
        return MediaItem(kind, url, preview.ifBlank { url })
    }

    private fun previewUrl(post: JSONObject): String =
        post.optJSONObject("preview")
            ?.optJSONArray("images")
            ?.optJSONObject(0)
            ?.optJSONObject("source")
            ?.optString("url")
            .orEmpty()

    private suspend fun build(url: String, html: String): MediaPost {
        val tags = postTag.findAll(html).map { it.value }.toList()
        val primary = tags.firstOrNull() ?: throw IOException("Post is not available")
        val attrs = attributes(primary)

        val id = attrs["id"]?.removePrefix("t3_")?.takeIf { postIdPattern.matches(it) }
            ?: postPath.find(url)?.groupValues?.get(1)
            ?: galleryPath.find(url)?.groupValues?.get(1)
            ?: throw IOException("Post is not available")

        val author = unescape(attrs["author"].orEmpty())
            .takeUnless { it.isBlank() || it == "[deleted]" }
            .orEmpty()

        val items = mediaItems(html, url, 0)
        if (items.isEmpty()) throw IOException("No media in this post")

        val thumbnail = mainPlayer(html)
            ?.let { attributes(it)["poster"] }
            ?.let(::unescape)
            .orEmpty()
            .ifBlank { ogImage(html) }
            .ifBlank { items.first().thumbnailUrl }

        return MediaPost(
            id = id,
            author = author,
            title = title(unescape(attrs["post-title"].orEmpty()), author),
            thumbnailUrl = thumbnail,
            items = items,
        )
    }

    private suspend fun mediaItems(html: String, pageUrl: String, depth: Int): List<MediaItem> {
        val tags = postTag.findAll(html).map { it.value }.toList()

        if (tags.any { attributes(it)["post-type"] == "gallery" }) {
            val gallery = galleryItems(html)
            if (gallery.isNotEmpty()) return gallery
        }

        for (tag in tags) {
            val href = unescape(attributes(tag)["content-href"].orEmpty())
            if (href.isBlank()) continue

            if (href.contains("v.redd.it")) {
                videoItem(html, href, "")?.let { return listOf(it) }
            }
            imageItem(href, "")?.let { return listOf(it) }
        }

        if (depth == 0) {
            for (tag in tags) {
                val href = unescape(attributes(tag)["content-href"].orEmpty())
                if (!isPostUrl(href)) continue

                val target = absolute(href)
                if (postKey(target) != null && postKey(target) == postKey(pageUrl)) continue

                val items = try {
                    Logger.d("reddit crosspost: $target")
                    mediaItems(fetchPage(target).body, target, depth + 1)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Logger.w("reddit crosspost failed: $href", error)
                    emptyList()
                }

                if (items.isNotEmpty()) return items
            }
        }

        return emptyList()
    }

    private suspend fun galleryItems(html: String): List<MediaItem> {
        val start = html.indexOf("<gallery-carousel")
        if (start < 0) return emptyList()

        val end = html.indexOf("</gallery-carousel>", start)
        val region = if (end > start) html.substring(start, end) else html.substring(start)

        return buildList {
            for (part in region.split(listItem).drop(1)) {
                galleryItem(part.substringBefore("</li>"))?.let { add(it) }
            }
        }
    }

    private suspend fun galleryItem(block: String): MediaItem? {
        val thumbnail = previewPattern.find(block)?.value?.let(::unescape).orEmpty()

        val original = originalPattern.find(block)?.value
        if (original != null) {
            val kind = if (original.endsWith(".gif", ignoreCase = true)) MediaKind.Gif else MediaKind.Photo
            return MediaItem(kind, original, thumbnail.ifBlank { original })
        }

        val player = playerTag.find(block)?.value ?: return null
        val poster = attributes(player)["poster"]?.let(::unescape).orEmpty().ifBlank { thumbnail }

        packagedMedia(player)?.let { return MediaItem(MediaKind.Video, it, poster) }

        val id = videoIdPattern.find(player)?.groupValues?.get(1) ?: return null
        return try {
            muxedVideo(id, poster)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.w("reddit gallery video failed: $id", error)
            null
        }
    }

    private suspend fun videoItem(html: String, href: String, thumbnail: String): MediaItem? {
        val player = mainPlayer(html)
        val poster = player
            ?.let { attributes(it)["poster"] }
            ?.let(::unescape)
            .orEmpty()
            .ifBlank { thumbnail }

        player?.let { packagedMedia(it) }?.let { return MediaItem(MediaKind.Video, it, poster) }

        val source = player?.let { attributes(it)["src"] }?.let(::unescape).orEmpty()
        val id = videoIdPattern.find(source)?.groupValues?.get(1)
            ?: videoIdPattern.find(href)?.groupValues?.get(1)
            ?: return null

        return muxedVideo(id, poster)
    }

    private suspend fun muxedVideo(id: String, thumbnail: String): MediaItem {
        val manifest = fetchText("$MEDIA_ORIGIN/$id/DASHPlaylist.mpd")
        val video = bestTrack(manifest, "video/") ?: throw IOException("No video track")
        val audio = bestTrack(manifest, "audio/")

        return MediaItem(
            kind = MediaKind.Video,
            url = trackUrl(id, video),
            thumbnailUrl = thumbnail,
            audioUrl = audio?.let { trackUrl(id, it) },
        )
    }

    private fun bestTrack(manifest: String, prefix: String): String? {
        var best: String? = null
        var bandwidth = -1

        for (match in representationPattern.findAll(manifest)) {
            val attrs = attributes(match.groupValues[1])
            val mime = attrs["mimeType"] ?: continue
            if (!mime.startsWith(prefix)) continue

            val current = attrs["bandwidth"]?.toIntOrNull() ?: 0
            if (current <= bandwidth) continue

            val base = baseUrlPattern.find(match.groupValues[2])?.groupValues?.get(1) ?: continue
            best = unescape(base)
            bandwidth = current
        }

        return best
    }

    private fun trackUrl(id: String, base: String): String =
        if (base.startsWith("http://") || base.startsWith("https://")) base else "$MEDIA_ORIGIN/$id/$base"

    private fun mainPlayer(html: String): String? {
        val players = playerTag.findAll(html).map { it.value }.toList()
        return players.firstOrNull { attributes(it)["comment-id"] == null }
            ?: players.firstOrNull { attributes(it)["packaged-media-json"] != null }
    }

    private fun packagedMedia(player: String): String? {
        val raw = attributes(player)["packaged-media-json"] ?: return null
        val json = runCatching { JSONObject(unescape(raw)) }.getOrNull() ?: return null
        val permutations = json.optJSONObject("playbackMp4s")?.optJSONArray("permutations") ?: return null

        var best: String? = null
        var bitrate = -1

        for (index in 0 until permutations.length()) {
            val source = permutations.optJSONObject(index)?.optJSONObject("source") ?: continue
            val url = source.optString("url")
            if (url.isBlank()) continue

            val current = source.optInt("bitrateBps")
            if (current > bitrate) {
                bitrate = current
                best = url
            }
        }

        return best
    }

    private fun imageItem(href: String, thumbnail: String): MediaItem? {
        if (!isImageUrl(href)) return null

        val kind = if (href.substringBefore('?').endsWith(".gif", ignoreCase = true)) {
            MediaKind.Gif
        } else {
            MediaKind.Photo
        }
        return MediaItem(kind, href, thumbnail.ifBlank { href })
    }

    private fun ogImage(html: String): String {
        val tag = ogImagePattern.find(html)?.value ?: return ""
        return unescape(attributes(tag)["content"].orEmpty())
    }

    private fun attributes(tag: String): Map<String, String> {
        val result = HashMap<String, String>()
        for (match in attributePattern.findAll(tag)) {
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    private fun unescape(text: String): String =
        text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    private fun title(raw: String, author: String): String {
        val line = raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        // Fallback titles also become file names, so they stay in English.
        return when {
            line.isNotEmpty() -> line.take(140)
            author.isNotBlank() -> "$author on Reddit"
            else -> "Reddit post"
        }
    }

    private fun fetchPage(url: String): Page {
        val connection = open(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return Page(connection.url?.toString() ?: url, readBody(connection))
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchText(url: String): String {
        val connection = open(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return readBody(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection): String {
        val raw = connection.inputStream
        val stream = if (connection.contentEncoding?.contains("gzip", true) == true) {
            GZIPInputStream(raw)
        } else {
            raw
        }
        return stream.bufferedReader().use { it.readText() }
    }

    private fun open(
        url: String,
        accept: String = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }

    private fun uriOf(link: String): Uri? {
        val raw = link.trim()
        if (raw.isEmpty()) return null
        return runCatching {
            Uri.parse(if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw")
        }.getOrNull()
    }

    private fun absolute(target: String): String {
        if (postIdPattern.matches(target)) return "$ORIGIN/comments/$target/"
        val uri = uriOf(target) ?: return target
        return absolute(uri)
    }

    private fun absolute(uri: Uri): String {
        val host = uri.host?.lowercase().orEmpty()
        val origin = if (isShortHost(host)) "https://redd.it" else ORIGIN
        return origin + uri.encodedPath.orEmpty().ifBlank { "/" }
    }

    private fun postKey(url: String): String? =
        postPath.find(url)?.groupValues?.get(1) ?: galleryPath.find(url)?.groupValues?.get(1)

    private fun isPostUrl(url: String): Boolean {
        val uri = uriOf(url) ?: return false
        val host = uri.host?.lowercase() ?: return false
        return isRedditHost(host) && postKey(uri.path.orEmpty()) != null
    }

    private fun isImageUrl(url: String): Boolean {
        val host = uriOf(url)?.host?.lowercase() ?: return false
        return host in imageHosts
    }

    private fun isRedditHost(host: String): Boolean =
        host == "reddit.com" || host.endsWith(".reddit.com") || isShortHost(host)

    private fun isShortHost(host: String): Boolean =
        host == "redd.it" || host == "www.redd.it"

    private data class Page(val url: String, val body: String)
}
