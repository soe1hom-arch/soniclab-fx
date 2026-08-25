package com.soniclab.fx.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.soniclab.fx.R
import com.soniclab.fx.audio.DspChain
import com.soniclab.fx.audio.FxSettings
import com.soniclab.fx.ui.MainActivity

/**
 * Foreground service that keeps the system-wide audio effect alive.
 * The effect registration is handled by the EffectRegistrationManager;
 * this service only maintains the notification and prevents the process
 * from being killed.
 */
class FxOverlayService : Service() {

    private val dspChain = DspChain()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("SonicLab FX — active"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_SETTINGS -> {
                val settings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_SETTINGS, FxSettings::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_SETTINGS)
                }
                if (settings != null) {
                    dspChain.updateSettings(settings)
                    updateNotification(if (settings.enabled) "SonicLab FX — active" else "SonicLab FX — bypassed")
                }
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        dspChain.reset()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "SonicLab FX",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "System-wide audio effect active" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SonicLab FX")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_UPDATE_SETTINGS = "com.soniclab.fx.UPDATE_SETTINGS"
        const val ACTION_STOP = "com.soniclab.fx.STOP"
        const val EXTRA_SETTINGS = "settings"
        private const val CHANNEL_ID = "soniclab_fx"
        private const val NOTIFICATION_ID = 1001
    }
}
