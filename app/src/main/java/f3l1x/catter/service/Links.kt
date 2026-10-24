package f3l1x.catter.service

enum class Source(val label: String) {
    YouTube("YouTube"),
    Instagram("Instagram"),
    TikTok("TikTok"),
}

data class MediaLink(val source: Source, val id: String)

object Links {

    fun parse(link: String): MediaLink? {
        YouTube.videoId(link)?.let { return MediaLink(Source.YouTube, it) }
        Instagram.match(link)?.let { return MediaLink(Source.Instagram, it) }
        TikTok.match(link)?.let { return MediaLink(Source.TikTok, it) }
        return null
    }
}
