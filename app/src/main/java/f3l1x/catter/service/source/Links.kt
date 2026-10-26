package f3l1x.catter.service.source

enum class Source(val label: String) {
    YouTube("YouTube"),
    Instagram("Instagram"),
    TikTok("TikTok"),
    Pinterest("Pinterest"),
    Reddit("Reddit"),
    X("X"),
}

data class MediaLink(val source: Source, val id: String)

object Links {

    private val urlPattern = Regex("https?://\\S+")

    fun parse(link: String): MediaLink? {
        YouTube.videoId(link)?.let { return MediaLink(Source.YouTube, it) }
        Instagram.match(link)?.let { return MediaLink(Source.Instagram, it) }
        TikTok.match(link)?.let { return MediaLink(Source.TikTok, it) }
        Pinterest.match(link)?.let { return MediaLink(Source.Pinterest, it) }
        Reddit.match(link)?.let { return MediaLink(Source.Reddit, it) }
        X.match(link)?.let { return MediaLink(Source.X, it) }
        return null
    }

    fun fromText(text: String?): String? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (parse(value) != null) return value

        return urlPattern.findAll(value)
            .map { it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"') }
            .firstOrNull { parse(it) != null }
    }
}
