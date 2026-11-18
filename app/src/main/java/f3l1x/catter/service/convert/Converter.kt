package f3l1x.catter.service.convert

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Converter {

    suspend fun durationMs(path: String): Long = withContext(Dispatchers.IO) {
        val session = FFprobeKit.getMediaInformation(path)
        val duration = session.mediaInformation?.duration?.toDoubleOrNull()
            ?: throw IOException("Media is not readable")
        (duration * 1000).toLong()
    }

    suspend fun convert(
        arguments: Array<String>,
        durationMs: Long = 0L,
        onProgress: (Float) -> Unit = {},
    ): Unit = suspendCancellableCoroutine { continuation ->
        val session = FFmpegKit.executeWithArgumentsAsync(
            arguments,
            { result ->
                when {
                    ReturnCode.isSuccess(result.returnCode) -> continuation.resume(Unit)
                    ReturnCode.isCancel(result.returnCode) ->
                        continuation.resumeWithException(IOException("Conversion cancelled"))
                    else ->
                        continuation.resumeWithException(
                            IOException("Conversion failed with code ${result.returnCode?.value}"),
                        )
                }
            },
            null,
            { statistics ->
                if (durationMs > 0) {
                    onProgress((statistics.time / durationMs).toFloat().coerceIn(0f, 1f))
                }
            },
        )
        continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }
}
