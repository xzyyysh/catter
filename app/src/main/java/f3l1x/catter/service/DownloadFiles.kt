package f3l1x.catter.service

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import f3l1x.catter.data.DownloadRecord
import f3l1x.catter.util.Logger
import java.io.File

object DownloadFiles {

    fun exists(context: Context, record: DownloadRecord): Boolean = runCatching {
        val uri = Uri.parse(record.uri)
        when {
            uri.scheme != "content" -> File(record.uri).exists()
            DocumentsContract.isDocumentUri(context, uri) ->
                DocumentFile.fromSingleUri(context, uri)?.exists() ?: false
            else -> context.contentResolver
                .query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                ?.use { it.count > 0 } ?: false
        }
    }.getOrElse {
        Logger.w("existence check failed: ${record.fileName}", it)
        false
    }

    fun delete(context: Context, record: DownloadRecord): Boolean = runCatching {
        val uri = Uri.parse(record.uri)
        when {
            uri.scheme != "content" -> {
                val file = File(record.uri)
                val deleted = file.exists() && file.delete()
                if (deleted) {
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                }
                deleted
            }
            DocumentsContract.isDocumentUri(context, uri) ->
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            else -> context.contentResolver.delete(uri, null, null) > 0
        }
    }.getOrElse {
        Logger.w("delete failed: ${record.fileName}", it)
        false
    }

    fun viewIntent(context: Context, record: DownloadRecord): Intent? = runCatching {
        val uri = Uri.parse(record.uri)
        val target = if (uri.scheme == "content") {
            uri
        } else {
            FileProvider.getUriForFile(context, "${context.packageName}.files", File(record.uri))
        }
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(target, record.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.getOrElse {
        Logger.w("open failed: ${record.fileName}", it)
        null
    }
}
