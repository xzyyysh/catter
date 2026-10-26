package f3l1x.catter.service

data class DownloadTicket(
    val url: String,
    val fileName: String,
    val headers: Map<String, String> = emptyMap(),
    val audioUrl: String? = null,
)

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
