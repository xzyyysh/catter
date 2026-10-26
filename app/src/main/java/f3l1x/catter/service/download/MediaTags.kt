package f3l1x.catter.service.download

import f3l1x.catter.util.Logger
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

object MediaTags {

    fun write(file: File, title: String, artist: String): Boolean = runCatching {
        val audio = AudioFileIO.read(file)
        val tag = audio.tag ?: audio.createDefaultTag()

        tag.setField(FieldKey.TITLE, title.ifBlank { "Unknown" })
        if (artist.isNotBlank()) tag.setField(FieldKey.ARTIST, artist)
        audio.commit()

        Logger.d("tags written: ${file.name}")
        true
    }.getOrElse {
        Logger.w("tag write failed: ${file.name}", it)
        false
    }
}
