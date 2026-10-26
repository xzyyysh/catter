package f3l1x.catter.service

import android.os.Build
import android.os.Environment
import java.io.File

sealed interface Destination {
    data class Media(val relativePath: String) : Destination
    data class Tree(val treeUri: String, val relativePath: String) : Destination
    data class Folder(val directory: File) : Destination
}

object SaveLocation {

    // Folder names are part of the on-disk path, so they are never translated.
    const val BASE_FOLDER = "CatterApp"
    const val VIDEO_FOLDER = "videos"
    const val AUDIO_FOLDER = "audios"
    const val PHOTO_FOLDER = "photos"

    fun destination(treeUri: String?, kind: MediaKind): Destination {
        val sub = when (kind) {
            MediaKind.Audio -> AUDIO_FOLDER
            MediaKind.Video -> VIDEO_FOLDER
            MediaKind.Photo, MediaKind.Gif -> PHOTO_FOLDER
        }

        if (!treeUri.isNullOrBlank()) {
            return Destination.Tree(treeUri = treeUri, relativePath = "$BASE_FOLDER/$sub")
        }

        val relative = "${Environment.DIRECTORY_DOWNLOADS}/$BASE_FOLDER/$sub"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Destination.Media(relative)
        } else {
            Destination.Folder(
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "$BASE_FOLDER/$sub",
                ),
            )
        }
    }

    fun label(treeLabel: String?): String = "${treeLabel ?: "Download"}/$BASE_FOLDER"
}
