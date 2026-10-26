package f3l1x.catter.service

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

object Mp4Muxer {

    private const val MIN_BUFFER_BYTES = 1024 * 1024

    fun mux(video: File, audio: File, output: File, signal: DownloadSignal) {
        val extractors = mutableListOf<MediaExtractor>()
        var muxer: MediaMuxer? = null
        var started = false

        try {
            val videoExtractor = MediaExtractor().apply { setDataSource(video.absolutePath) }
            extractors += videoExtractor
            val audioExtractor = MediaExtractor().apply { setDataSource(audio.absolutePath) }
            extractors += audioExtractor

            val videoTrack = firstTrack(videoExtractor, "video/")
                ?: throw IOException("No video track")
            val audioTrack = firstTrack(audioExtractor, "audio/")
                ?: throw IOException("No audio track")

            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)

            val videoFormat = videoExtractor.getTrackFormat(videoTrack)
            val audioFormat = audioExtractor.getTrackFormat(audioTrack)

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoTarget = muxer.addTrack(videoFormat)
            val audioTarget = muxer.addTrack(audioFormat)
            muxer.start()
            started = true

            val capacity = maxOf(
                maxInput(videoFormat),
                maxInput(audioFormat),
                MIN_BUFFER_BYTES,
            )
            val buffer = ByteBuffer.allocateDirect(capacity)
            val info = MediaCodec.BufferInfo()
            var videoDone = false
            var audioDone = false
            var wrote = false

            while (!videoDone || !audioDone) {
                signal.throwIfCancelled()

                val videoTime = if (videoDone) Long.MAX_VALUE else videoExtractor.sampleTime
                val audioTime = if (audioDone) Long.MAX_VALUE else audioExtractor.sampleTime
                if (videoTime == Long.MAX_VALUE && audioTime == Long.MAX_VALUE) break

                val takeVideo = videoTime <= audioTime
                val extractor = if (takeVideo) videoExtractor else audioExtractor
                val target = if (takeVideo) videoTarget else audioTarget

                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    if (takeVideo) videoDone = true else audioDone = true
                    continue
                }

                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags

                muxer.writeSampleData(target, buffer, info)
                wrote = true
                extractor.advance()
            }

            if (!wrote) throw IOException("No samples were muxed")

            muxer.stop()
            started = false
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            extractors.forEach { runCatching { it.release() } }
        }
    }

    private fun firstTrack(extractor: MediaExtractor, prefix: String): Int? {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(prefix)) return index
        }
        return null
    }

    private fun maxInput(format: MediaFormat): Int =
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        } else {
            0
        }
}
