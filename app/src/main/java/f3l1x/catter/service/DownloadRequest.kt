package f3l1x.catter.service

enum class MediaKind { Video, Audio, Photo, Gif }

data class DownloadRequest(
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val label: String,
    val fileName: String,
    val mimeType: String,
    val kind: MediaKind,
    val resolve: suspend () -> DownloadTicket,
)
