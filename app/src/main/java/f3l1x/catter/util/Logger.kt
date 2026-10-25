package f3l1x.catter.util

import android.util.Log

object Logger {

    private const val TAG = "catter-downloader"

    @Volatile
    var enabled: Boolean = false

    fun d(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun i(message: String) {
        if (enabled) Log.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (enabled) Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (enabled) Log.e(TAG, message, throwable)
    }
}
