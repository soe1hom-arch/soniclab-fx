package com.soniclab.fx.service

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
import com.soniclab.fx.R
import com.soniclab.fx.audio.DspChain
import com.soniclab.fx.audio.FxSettings
import com.soniclab.fx.audio.SpatialProcessor
import com.soniclab.fx.ui.MainActivity

class FxOverlayService : Service() {

    private val dspChain = DspChain()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("SonicLab FX — active"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("SonicLab FX — active"))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_SETTINGS -> {
                val s = FxSettings(
                    enabled = intent.getBooleanExtra("enabled", false),
                    eqBandGains = intent.getFloatArrayExtra("eqGains") ?: FloatArray(FxSettings.BAND_COUNT),
                    bassGainDb = intent.getFloatExtra("bass", 0f),
                    trebleGainDb = intent.getFloatExtra("treble", 0f),
                    balance = intent.getFloatExtra("balance", 0f),
                    reverbMix = intent.getFloatExtra("reverbMix", 0f),
                    reverbRoomSize = intent.getFloatExtra("reverbRoom", 0.5f),
                    preampGainDb = intent.getFloatExtra("preamp", 0f),
                    enhanceEnabled = intent.getBooleanExtra("enhance", false),
                    autoNormalize = intent.getBooleanExtra("autoNorm", false),
                    spatialMode = intent.getIntExtra("spatialMode", SpatialProcessor.MODE_OFF),
                    spatial3d = intent.getBooleanExtra("spatial3d", false),
                    spatial8d = intent.getBooleanExtra("spatial8d", false),
                    surround = intent.getBooleanExtra("surround", false),
                    spatialWidth = intent.getFloatExtra("spatialWidth", 0.6f),
                    spatialRotation = intent.getFloatExtra("spatialRotation", 8f),
                    spatialPanDepth = intent.getFloatExtra("spatialPanDepth", 0.6f),
                )
                dspChain.updateSettings(s)
                updateNotification(if (s.enabled) "SonicLab FX — active" else "SonicLab FX — bypassed")
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
        val channel = NotificationChannel(CHANNEL_ID, "SonicLab FX", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "System-wide audio effect active" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SonicLab FX").setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent).setOngoing(true).build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_UPDATE_SETTINGS = "com.soniclab.fx.UPDATE_SETTINGS"
        const val ACTION_STOP = "com.soniclab.fx.STOP"
        private const val CHANNEL_ID = "soniclab_fx"
        private const val NOTIFICATION_ID = 1001
    }
}
