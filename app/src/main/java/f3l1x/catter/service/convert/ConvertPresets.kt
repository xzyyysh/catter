package f3l1x.catter.service.convert

import f3l1x.catter.service.model.MediaKind

enum class ConvertSourceKind { Video, Audio, Image }

data class ConvertPreset(
    val label: String,
    val extension: String,
    val mimeType: String,
    val kind: MediaKind,
    val accepts: Set<ConvertSourceKind>,
    val arguments: ((input: String, output: String) -> List<String>)? = null,
    val targetBytes: Long = 0L,
)

object ConvertPresets {

    val all: List<ConvertPreset> get() = listOf(MP3, FLAC, WAV, M4A, MP4, MKV, DISCORD, WEBP, PNG)

    fun forKind(kind: ConvertSourceKind): List<ConvertPreset> =
        all.filter { kind in it.accepts }

    fun sourceKind(mimeType: String?, fileName: String): ConvertSourceKind? {
        val mime = mimeType?.lowercase().orEmpty()
        return when {
            mime.startsWith("video/") -> ConvertSourceKind.Video
            mime.startsWith("audio/") -> ConvertSourceKind.Audio
            mime.startsWith("image/") -> ConvertSourceKind.Image
            else -> byExtension(fileName)
        }
    }

    private val MP3 = ConvertPreset(
        label = "MP3 320",
        extension = "mp3",
        mimeType = "audio/mpeg",
        kind = MediaKind.Audio,
        accepts = setOf(ConvertSourceKind.Video, ConvertSourceKind.Audio),
        arguments = { input, output ->
            listOf("-i", input, "-vn", "-c:a", "libmp3lame", "-b:a", "320k", output)
        },
    )

    private val FLAC = ConvertPreset(
        label = "FLAC",
        extension = "flac",
        mimeType = "audio/flac",
        kind = MediaKind.Audio,
        accepts = setOf(ConvertSourceKind.Video, ConvertSourceKind.Audio),
        arguments = { input, output ->
            listOf("-i", input, "-vn", "-c:a", "flac", output)
        },
    )

    private val WAV = ConvertPreset(
        label = "WAV",
        extension = "wav",
        mimeType = "audio/wav",
        kind = MediaKind.Audio,
        accepts = setOf(ConvertSourceKind.Audio),
        arguments = { input, output ->
            listOf("-i", input, "-vn", "-c:a", "pcm_s16le", output)
        },
    )

    private val M4A = ConvertPreset(
        label = "M4A 256",
        extension = "m4a",
        mimeType = "audio/mp4",
        kind = MediaKind.Audio,
        accepts = setOf(ConvertSourceKind.Video, ConvertSourceKind.Audio),
        arguments = { input, output ->
            listOf("-i", input, "-vn", "-c:a", "aac", "-b:a", "256k", output)
        },
    )

    private val MP4 = ConvertPreset(
        label = "MP4 H.264",
        extension = "mp4",
        mimeType = "video/mp4",
        kind = MediaKind.Video,
        accepts = setOf(ConvertSourceKind.Video),
        arguments = { input, output ->
            listOf(
                "-i", input,
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "18",
                "-c:a", "aac", "-b:a", "192k",
                "-movflags", "+faststart",
                output,
            )
        },
    )

    private val MKV = ConvertPreset(
        label = "MKV",
        extension = "mkv",
        mimeType = "video/x-matroska",
        kind = MediaKind.Video,
        accepts = setOf(ConvertSourceKind.Video),
        arguments = { input, output ->
            listOf("-i", input, "-map", "0", "-c", "copy", output)
        },
    )

    private val DISCORD = ConvertPreset(
        label = "Discord 8MB",
        extension = "mp4",
        mimeType = "video/mp4",
        kind = MediaKind.Video,
        accepts = setOf(ConvertSourceKind.Video),
        targetBytes = 8L * 1024 * 1024,
    )

    private val WEBP = ConvertPreset(
        label = "WebP",
        extension = "webp",
        mimeType = "image/webp",
        kind = MediaKind.Photo,
        accepts = setOf(ConvertSourceKind.Image),
        arguments = { input, output ->
            listOf("-i", input, "-c:v", "libwebp", "-lossless", "1", output)
        },
    )

    private val PNG = ConvertPreset(
        label = "PNG",
        extension = "png",
        mimeType = "image/png",
        kind = MediaKind.Photo,
        accepts = setOf(ConvertSourceKind.Image),
        arguments = { input, output ->
            listOf("-i", input, output)
        },
    )

    private fun byExtension(fileName: String): ConvertSourceKind? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            in setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "ts", "flv") -> ConvertSourceKind.Video
            in setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma") -> ConvertSourceKind.Audio
            in setOf("jpg", "jpeg", "png", "webp", "bmp", "heic") -> ConvertSourceKind.Image
            else -> null
        }
    }
}
