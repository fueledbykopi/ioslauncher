package com.iospring.launcher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.iospring.launcher.MainActivity
import com.iospring.launcher.R
import com.iospring.launcher.data.LauncherRepository
import com.iospring.launcher.ui.IosLockScreenActivity

/**
 * Lightweight, silent Foreground Service that:
 * 1. Keeps iOSpring resident in RAM so aggressive OEM memory killers (ColorOS / Android 15 LMK)
 *    do not evict the Home Launcher between app switches.
 * 2. Optionally listens for ACTION_SCREEN_OFF (zero polling, 0% idle battery) when iOS Lock Screen
 *    is enabled so the custom iOS Lock Screen is ready instantaneously on wake.
 */
class LauncherKeepAliveService : Service() {

    private var screenReceiverRegistered: Boolean = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            if (!lockScreenActive) return
            if (IosLockScreenActivity.isShowing) return

            // Never interrupt active phone calls or incoming ringtones
            val audio = getSystemService(AudioManager::class.java)
            val mode = audio?.mode ?: AudioManager.MODE_NORMAL
            if (mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_RINGTONE ||
                mode == AudioManager.MODE_IN_COMMUNICATION
            ) {
                return
            }

            try {
                val lockIntent = Intent(this@LauncherKeepAliveService, IosLockScreenActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
                }
                startActivity(lockIntent)
            } catch (_: Exception) {
                // Safe ignore if OS blocks background start on restricted states
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        val repo = LauncherRepository(applicationContext)
        val prefs = repo.loadPreferences()
        repo.close()
        lockScreenActive = intent?.getBooleanExtra(EXTRA_LOCK_SCREEN, prefs.iosLockScreenEnabled)
            ?: prefs.iosLockScreenEnabled

        val notification = buildSilentNotification(lockScreenActive)
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
            serviceRunning = true
            syncScreenReceiver(lockScreenActive)
        } catch (_: Exception) {
            serviceRunning = false
            syncScreenReceiver(false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        syncScreenReceiver(false)
        serviceRunning = false
        super.onDestroy()
    }

    private fun syncScreenReceiver(enabled: Boolean) {
        if (enabled && !screenReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(screenOffReceiver, filter)
            }
            screenReceiverRegistered = true
        } else if (!enabled && screenReceiverRegistered) {
            try {
                unregisterReceiver(screenOffReceiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
            screenReceiverRegistered = false
        }
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Launcher & Lock Screen Service",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps iOSpring resident in RAM and powers the optional iOS Lock Screen"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildSilentNotification(withLockScreen: Boolean): Notification {
        val homeIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            homeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (withLockScreen) "Launcher & iOS Lock Screen active" else "Running smoothly in memory"
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("iOSpring Active")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "iospring_keep_alive"
        private const val NOTIFICATION_ID = 1801
        private const val EXTRA_LOCK_SCREEN = "extra_lock_screen"

        @Volatile
        private var serviceRunning: Boolean = false
        @Volatile
        private var lockScreenActive: Boolean = false

        fun syncState(context: Context, keepInRam: Boolean, lockScreenEnabled: Boolean = false) {
            val shouldRun = keepInRam || lockScreenEnabled
            if (shouldRun && serviceRunning && lockScreenActive == lockScreenEnabled) return
            if (!shouldRun && !serviceRunning) return

            lockScreenActive = lockScreenEnabled
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, LauncherKeepAliveService::class.java).apply {
                putExtra(EXTRA_LOCK_SCREEN, lockScreenEnabled)
            }
            try {
                if (shouldRun) {
                    ContextCompat.startForegroundService(appCtx, intent)
                } else {
                    serviceRunning = false
                    appCtx.stopService(intent)
                }
            } catch (_: Exception) {
                // Safe ignore if system blocks FGS launch from background
            }
        }
    }
}
