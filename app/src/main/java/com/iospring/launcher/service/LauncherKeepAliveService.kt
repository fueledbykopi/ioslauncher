package com.iospring.launcher.service

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
import androidx.core.content.ContextCompat
import com.iospring.launcher.MainActivity
import com.iospring.launcher.R

/**
 * Lightweight, silent Foreground Service that keeps iOSpring resident in RAM
 * so aggressive OEM memory killers (e.g. ColorOS / Android 15 LMK) do not evict
 * the Home Launcher between app switches.
 */
class LauncherKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        val notification = buildSilentNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Launcher Memory Protection",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps iOSpring resident in RAM for instant Home gesture response"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildSilentNotification(): Notification {
        val homeIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            homeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("iOSpring Active")
            .setContentText("Running smoothly in memory")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "iospring_keep_alive"
        private const val NOTIFICATION_ID = 1801

        fun syncState(context: Context, enabled: Boolean) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, LauncherKeepAliveService::class.java)
            try {
                if (enabled) {
                    ContextCompat.startForegroundService(appCtx, intent)
                } else {
                    appCtx.stopService(intent)
                }
            } catch (_: Exception) {
                // Safe ignore if system blocks FGS launch from background
            }
        }
    }
}
