package eu.blueseaeye.bse.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import eu.blueseaeye.bse.BseApplication
import eu.blueseaeye.bse.MainActivity

/**
 * Serwis pierwszoplanowy utrzymujący pracę monitora przy zgaszonym ekranie.
 * Android wymaga usługi typu mediaPlayback, aby synteza i tony odchyłki oraz
 * alarmy działały w tle — odpowiednik trybu audio w tle (UIBackgroundModes)
 * z wersji iOS.
 */
class HelmForegroundService : Service() {

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                startAsForeground()
                // Gdy system wskrzesił usługę po ubiciu procesu (START_STICKY,
                // intent == null), MainActivity się NIE odtworzyła, więc pętla
                // monitora nie działa i aplikacja milczałaby mimo aktywnej
                // usługi. Wznów monitor i — zależnie od ustawień użytkownika —
                // odczyt. To domyka „ciszę po ubiciu procesu” na agresywnych
                // systemach (Samsung One UI, Xiaomi HyperOS).
                val relaunchedBySystem = intent == null
                if (relaunchedBySystem) {
                    val container = (application as BseApplication).container
                    container.monitor.start()
                    container.monitor.resumeIfNeeded(launchedFromService = true)
                }
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("BSE — odczyt aktywny")
            .setContentText("Aplikacja czyta dane z urządzenia BlueSeaEye.")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Odczyt w tle",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Utrzymuje odczyt danych z urządzenia BlueSeaEye przy zgaszonym ekranie."
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "bse.foreground"
        private const val NOTIFICATION_ID = 4200
        const val ACTION_STOP = "eu.blueseaeye.bse.STOP_FOREGROUND"

        fun start(context: Context) {
            val intent = Intent(context, HelmForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, HelmForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
