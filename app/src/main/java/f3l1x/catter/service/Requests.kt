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
        val label = when {
            video && multi -> "Video ${index + 1}"
            video -> "Video"
            multi -> "Photo ${index + 1}"
            else -> "Photo"
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
                extension = if (video) "mp4" else "jpg",
            ),
            mimeType = if (video) "video/mp4" else "image/jpeg",
            kind = item.kind,
            resolve = { DownloadTicket(item.url, "") },
        )
    }
}
