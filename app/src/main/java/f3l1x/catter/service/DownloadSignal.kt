package f3l1x.catter.service

import java.util.concurrent.atomic.AtomicBoolean

class DownloadSignal {

    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun throwIfCancelled() {
        if (cancelled.get()) throw DownloadCancelled()
    }
}

class DownloadCancelled : Exception("Download cancelled")
