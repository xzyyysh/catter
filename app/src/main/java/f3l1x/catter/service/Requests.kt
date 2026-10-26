package f3l1x.catter.service

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

    fun post(post: MediaPost, item: MediaItem, index: Int): DownloadRequest {
        val multi = post.items.size > 1
        val video = item.kind == MediaKind.Video
        val gif = item.kind == MediaKind.Gif
        val label = when {
            video && multi -> "Video ${index + 1}"
            video -> "Video"
            gif && multi -> "Gif ${index + 1}"
            gif -> "Gif"
            multi -> "Photo ${index + 1}"
            else -> "Photo"
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

    private fun imageExtension(url: String): String {
        val extension = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        return if (extension in setOf("jpg", "jpeg", "png", "webp")) extension else "jpg"
    }
}
