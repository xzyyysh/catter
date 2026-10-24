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

    const val BASE_FOLDER = "CatterApp"
    const val VIDEO_FOLDER = "videos"
    const val AUDIO_FOLDER = "audios"

    fun destination(treeUri: String?, quality: Quality): Destination {
        val sub = if (quality.isAudio) AUDIO_FOLDER else VIDEO_FOLDER

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
