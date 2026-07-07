package eu.blueseaeye.bse.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Powiadomienia bezpieczeństwa (utrata połączenia). Odpowiednik
 * SafetyNotificationController z wersji iOS.
 */
class SafetyNotifications(context: Context) {

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alerty bezpieczeństwa",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Powiadomienia o utracie połączenia z urządzeniem BlueSeaEye."
            enableVibration(true)
        }
        val systemManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        systemManager.createNotificationChannel(channel)
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    fun scheduleConnectionLostAlert(details: String) {
        if (!hasPermission()) return
        val notification: Notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Utracono połączenie")
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(CONNECTION_LOST_ID, notification) }
    }

    fun clearConnectionLostAlert() {
        runCatching { manager.cancel(CONNECTION_LOST_ID) }
    }

    companion object {
        const val CHANNEL_ID = "bse.safety"
        private const val CONNECTION_LOST_ID = 4201
    }
}
