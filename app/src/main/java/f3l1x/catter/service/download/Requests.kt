package f3l1x.catter.service.download

import android.content.Context
import f3l1x.catter.R
import f3l1x.catter.service.model.MediaItem
import f3l1x.catter.service.model.MediaKind
import f3l1x.catter.service.model.MediaPost
import f3l1x.catter.service.source.Quality
import f3l1x.catter.service.source.VideoDetails
import f3l1x.catter.service.source.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Requests {

    fun youtube(details: VideoDetails, quality: Quality): DownloadRequest {
        val kind = if (quality.isAudio) MediaKind.Audio else MediaKind.Video

        return DownloadRequest(
            title = details.title,
            author = details.author,
            thumbnailUrl = details.thumbnailUrl,
            label = quality.label,
            fileName = Downloader.fileName(details.title, details.author, quality.label, quality.extension),
            mimeType = quality.mimeType,
            kind = kind,
            resolve = { YouTube.ticket(details.id, quality) },
        )
    }

    fun post(context: Context, post: MediaPost, item: MediaItem, index: Int): DownloadRequest {
        val multi = post.items.size > 1
        val video = item.kind == MediaKind.Video
        val gif = item.kind == MediaKind.Gif
        val label = when {
            video && multi -> context.getString(R.string.item_video_numbered, index + 1)
            video -> context.getString(R.string.item_video)
            gif && multi -> context.getString(R.string.item_gif_numbered, index + 1)
            gif -> context.getString(R.string.item_gif)
            multi -> context.getString(R.string.item_photo_numbered, index + 1)
            else -> context.getString(R.string.item_photo)
        }
        val extension = when {
            video -> "mp4"
            gif -> "gif"
            else -> imageExtension(item.url)
        }
        val mimeType = when {
            video -> "video/mp4"
            gif -> "image/gif"
            extension == "jpg" -> "image/jpeg"
            else -> "image/$extension"
        }

        return DownloadRequest(
            title = post.title,
            author = post.author,
            thumbnailUrl = post.thumbnailUrl,
            label = label,
            fileName = Downloader.fileName(
                title = post.author.ifBlank { "Catter" },
                artist = post.id,
                label = if (multi) "${index + 1}" else "",
                extension = extension,
            ),
            mimeType = mimeType,
            kind = item.kind,
            resolve = { DownloadTicket(item.url, "", audioUrl = item.audioUrl) },
        )
    }

    fun thumbnail(context: Context, title: String, author: String, url: String): DownloadRequest {
        val extension = imageExtension(url)

        return DownloadRequest(
            title = title,
            author = author,
            thumbnailUrl = url,
            label = context.getString(R.string.item_thumbnail),
            fileName = Downloader.fileName(
                title = title.ifBlank { "Catter" },
                artist = author,
                label = "thumbnail",
                extension = extension,
            ),
            mimeType = mimeTypeOf(extension),
            kind = MediaKind.Photo,
            resolve = { DownloadTicket(resolveThumbnail(url), "") },
        )
    }

    private suspend fun resolveThumbnail(url: String): String = withContext(Dispatchers.IO) {
        val candidates = if (url.contains("maxresdefault")) {
            listOf(url, url.replace("maxresdefault", "hqdefault"))
        } else {
            listOf(url)
        }
        candidates.firstOrNull { reachable(it) }
            ?: throw IOException("Thumbnail is not available")
    }

    private fun reachable(url: String): Boolean = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
        }
        try {
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    private fun mimeTypeOf(extension: String): String = when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    private fun imageExtension(url: String): String {
        val extension = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        return if (extension in setOf("jpg", "jpeg", "png", "webp")) extension else "jpg"
    }
}
