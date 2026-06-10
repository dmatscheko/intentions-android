package at.matscheko.intentions.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import at.matscheko.intentions.MainActivity
import at.matscheko.intentions.R
import at.matscheko.intentions.core.SnifferRepository

/**
 * Foreground service that keeps the broadcast sniffer registered (and a
 * persistent notification visible) so monitoring continues when the app's UI is
 * closed.
 */
class SnifferService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SnifferRepository.stop(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        SnifferRepository.start(this)
        return START_STICKY
    }

    override fun onDestroy() {
        SnifferRepository.stop(this)
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Broadcast sniffer", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SnifferService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Monitoring broadcasts")
            .setContentText("Intentions is recording incoming broadcasts")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "sniffer"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "at.matscheko.intentions.SNIFFER_STOP"

        fun start(context: android.content.Context) {
            ContextCompat.startForegroundService(context, Intent(context, SnifferService::class.java))
        }

        fun stop(context: android.content.Context) {
            context.startService(Intent(context, SnifferService::class.java).setAction(ACTION_STOP))
        }
    }
}
