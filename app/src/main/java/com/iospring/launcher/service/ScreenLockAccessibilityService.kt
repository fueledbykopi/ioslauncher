package com.iospring.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/**
 * Zero-overhead, zero-event AccessibilityService dedicated exclusively to
 * Double-Tap to Screen Off via GLOBAL_ACTION_LOCK_SCREEN (Android 9+).
 *
 * Subscribes to ZERO accessibility events so it consumes 0% idle CPU & 0% battery,
 * and preserves native Fingerprint / Face Unlock (unlike legacy DeviceAdmin.lockNow).
 */
class ScreenLockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: zero event subscriptions for 0% CPU / battery impact
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instanceRef?.get() === this) {
            instanceRef = null
        }
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instanceRef: WeakReference<ScreenLockAccessibilityService>? = null

        fun isConnected(): Boolean = instanceRef?.get() != null

        fun isEnabledInSettings(context: Context): Boolean {
            if (isConnected()) return true
            return try {
                val enabled = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()
                enabled.contains("${context.packageName}/${ScreenLockAccessibilityService::class.java.name}") ||
                    enabled.contains("${context.packageName}/.service.ScreenLockAccessibilityService")
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Turns off and locks the screen cleanly without disabling biometrics.
         * Returns true if the global lock action succeeded.
         */
        fun lockScreenNow(): Boolean {
            val svc = instanceRef?.get() ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }.getOrDefault(false)
            } else {
                false
            }
        }
    }
}
