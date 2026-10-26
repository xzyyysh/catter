package f3l1x.catter.util

import android.content.Context
import f3l1x.catter.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val KB = 1_000.0
private const val MB = 1_000_000.0
private const val GB = 1_000_000_000.0

fun formatBytes(bytes: Long): String = when {
    bytes >= GB -> String.format(Locale.US, "%.2f GB", bytes / GB)
    bytes >= MB -> String.format(Locale.US, "%.1f MB", bytes / MB)
    bytes >= KB -> String.format(Locale.US, "%.0f KB", bytes / KB)
    else -> "$bytes B"
}

fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= MB -> String.format(Locale.US, "%.1f MB/s", bytesPerSecond / MB)
    bytesPerSecond >= KB -> String.format(Locale.US, "%.0f KB/s", bytesPerSecond / KB)
    else -> "$bytesPerSecond B/s"
}

fun formatDate(context: Context, timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)

    return if (sameDay) {
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        context.getString(R.string.date_today, time)
    } else {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

