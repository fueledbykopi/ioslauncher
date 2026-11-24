package com.iospring.launcher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Optional, ultra-lightweight notification counter for authentic iOS red badges.
 * Keeps only a tiny packageName -> count map in memory. Zero polling, zero wakeups.
 */
class BadgeNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        recomputeBadges()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        recomputeBadges()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        recomputeBadges()
    }

    private fun recomputeBadges() {
        try {
            val active = activeNotifications ?: return
            val map = HashMap<String, Int>()
            for (sbn in active) {
                if (sbn.isOngoing) continue
                val pkg = sbn.packageName ?: continue
                map[pkg] = (map[pkg] ?: 0) + 1
            }
            BadgeStore.replaceAll(map)
        } catch (_: Exception) {
            // Safe ignore if listener disconnects mid-query
        }
    }
}

object BadgeStore {
    private val counts = ConcurrentHashMap<String, Int>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun replaceAll(newCounts: Map<String, Int>) {
        counts.clear()
        counts.putAll(newCounts)
        listeners.forEach { it.invoke() }
    }

    fun countForPackage(packageName: String): Int = counts[packageName] ?: 0

    fun countForPackages(packages: Collection<String>): Int {
        var total = 0
        for (pkg in packages) {
            total += counts[pkg] ?: 0
        }
        return total
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
}
