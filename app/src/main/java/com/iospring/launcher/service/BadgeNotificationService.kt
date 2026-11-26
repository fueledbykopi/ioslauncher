package com.iospring.launcher.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.iospring.launcher.model.LockNotificationItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Optional, ultra-lightweight notification counter & lock screen notification feed.
 * Keeps only a tiny packageName -> count map and max 6 text summaries in memory.
 * Zero polling, zero wakeups, zero bitmap allocations.
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
            val pm = packageManager
            val map = HashMap<String, Int>()
            val lockItems = ArrayList<LockNotificationItem>(6)
            val seenKeys = HashSet<String>(8)

            val sorted = active.sortedByDescending { it.postTime }
            for (sbn in sorted) {
                if (sbn.isOngoing) continue
                val pkg = sbn.packageName ?: continue
                if (pkg == packageName) continue
                map[pkg] = (map[pkg] ?: 0) + 1

                if (lockItems.size < 6) {
                    val extras = sbn.notification?.extras
                    val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
                    val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
                    if (title.isEmpty() && text.isEmpty()) continue
                    val dedupeKey = "$pkg|$title|$text"
                    if (!seenKeys.add(dedupeKey)) continue
                    val appName = try {
                        val ai = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(ai)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                    } catch (_: Exception) {
                        pkg
                    }
                    lockItems.add(
                        LockNotificationItem(
                            key = sbn.key ?: dedupeKey,
                            packageName = pkg,
                            appName = appName,
                            title = title.ifEmpty { appName },
                            text = text,
                            postTime = sbn.postTime
                        )
                    )
                }
            }
            BadgeStore.replaceAll(map, lockItems)
        } catch (_: Exception) {
            // Safe ignore if listener disconnects mid-query
        }
    }
}

object BadgeStore {
    private val counts = ConcurrentHashMap<String, Int>()
    @Volatile
    private var lockNotifications: List<LockNotificationItem> = emptyList()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun replaceAll(newCounts: Map<String, Int>, recentLockItems: List<LockNotificationItem> = emptyList()) {
        counts.clear()
        counts.putAll(newCounts)
        lockNotifications = recentLockItems
        listeners.forEach { it.invoke() }
    }

    fun recentNotifications(): List<LockNotificationItem> = lockNotifications

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
