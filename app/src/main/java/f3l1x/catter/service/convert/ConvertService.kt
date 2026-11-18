package f3l1x.catter.service.convert

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ConvertService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        ConvertNotifications.ensureChannels(this)
        startForegroundNotification(ConvertNotifications.progress(this, ConvertManager.tasks.value))

        scope.launch {
            ConvertManager.tasks.collect { tasks ->
                if (tasks.isNotEmpty()) {
                    postProgress(tasks)
                }
            }
        }
    }

    private fun startForegroundNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ConvertNotifications.ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(ConvertNotifications.ID_PROGRESS, notification)
        }
    }

    private fun postProgress(tasks: List<ConvertManager.Task>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationManagerCompat.from(this).notify(
            ConvertNotifications.ID_PROGRESS,
            ConvertNotifications.progress(this, tasks),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification(ConvertNotifications.progress(this, ConvertManager.tasks.value))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ConvertService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConvertService::class.java))
        }
    }
}
