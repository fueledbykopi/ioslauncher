package com.iospring.launcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Lightweight event-driven BroadcastReceiver for incremental package updates
 * (PACKAGE_ADDED, PACKAGE_REMOVED, PACKAGE_CHANGED, PACKAGE_REPLACED).
 * Avoids wasteful full rescans and consumes 0% idle battery.
 */
class PackageChangeReceiver(
    private val onPackageAdded: (packageName: String) -> Unit,
    private val onPackageRemoved: (packageName: String) -> Unit,
    private val onPackageChanged: (packageName: String) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val pkg = intent.data?.schemeSpecificPart ?: return
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                if (replacing) onPackageChanged(pkg) else onPackageAdded(pkg)
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                if (!replacing) onPackageRemoved(pkg)
            }
            Intent.ACTION_PACKAGE_CHANGED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                onPackageChanged(pkg)
            }
        }
    }

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(this, filter)
        }
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
        } catch (_: IllegalArgumentException) {
            // Already unregistered
        }
    }
}
