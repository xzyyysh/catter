package f3l1x.catter.service

enum class MediaKind { Video, Audio, Photo, Gif }

data class MediaItem(
    val kind: MediaKind,
    val url: String,
    val thumbnailUrl: String,
    val audioUrl: String? = null,
)

data class MediaPost(
    val id: String,
    val author: String,
    val title: String,
    val thumbnailUrl: String,
    val items: List<MediaItem>,
)
