package f3l1x.catter.service.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import f3l1x.catter.MainActivity
import f3l1x.catter.R
import f3l1x.catter.data.DownloadRecord
import f3l1x.catter.util.formatBytes
import f3l1x.catter.util.formatSpeed

object DownloadNotifications {

    const val CHANNEL_PROGRESS = "downloads_progress"
    const val CHANNEL_RESULT = "downloads_result"
    const val ID_PROGRESS = 1001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val progress = NotificationChannel(
            CHANNEL_PROGRESS,
            context.getString(R.string.notification_channel_progress),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_progress_description)
            setShowBadge(false)
            setSound(null, null)
        }

        val result = NotificationChannel(
            CHANNEL_RESULT,
            context.getString(R.string.notification_channel_result),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_result_description)
        }

        manager.createNotificationChannels(listOf(progress, result))
    }

    fun progress(context: Context, tasks: List<DownloadManager.Task>): Notification {
        val task = tasks.firstOrNull { it.status == DownloadStatus.Running } ?: tasks.firstOrNull()

        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(task?.title ?: context.getString(R.string.app_name))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))

        val stats = task?.progress
        when {
            stats == null -> {
                builder.setProgress(0, 0, true)
                builder.setContentText(
                    task?.let {
                        context.getString(R.string.notification_preparing, it.qualityLabel)
                    } ?: context.getString(R.string.notification_preparing_download),
                )
            }

            stats.isDeterminate -> {
                builder.setProgress(100, (stats.fraction * 100).toInt().coerceIn(0, 100), false)
                builder.setContentText(
                    "${formatBytes(stats.bytes)} / ${formatBytes(stats.total)}  ·  ${formatSpeed(stats.speed)}",
                )
            }

            else -> {
                builder.setProgress(0, 0, true)
                builder.setContentText(
                    "${formatBytes(stats.bytes)}  ·  ${formatSpeed(stats.speed)}",
                )
            }
        }

        if (tasks.size > 1) {
            builder.setSubText(context.getString(R.string.notification_more, tasks.size - 1))
        }
        return builder.build()
    }

    fun completed(context: Context, record: DownloadRecord) {
        val intent = DownloadFiles.viewIntent(context, record)
        val pending = PendingIntent.getActivity(
            context,
            record.id.hashCode(),
            intent ?: appIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(context.getString(R.string.notification_complete))
            .setContentText(record.title)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${record.title}\n${formatBytes(record.size)}",
                ),
            )
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        notify(context, record.id.hashCode(), notification)
    }

    fun failed(context: Context, title: String, error: String?) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(context.getString(R.string.notification_failed))
            .setContentText(error?.take(120) ?: title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n${error.orEmpty()}"))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()

        notify(context, (title.hashCode() and 0xFFFF) + 2000, notification)
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun appIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        appIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
