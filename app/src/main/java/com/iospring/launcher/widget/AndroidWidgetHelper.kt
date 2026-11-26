package com.iospring.launcher.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import kotlin.math.ceil

/**
 * Lightweight native Android AppWidget utility for provider discovery and grid span calculation.
 */
object AndroidWidgetHelper {

    const val APPWIDGET_HOST_ID: Int = 0x1059

    data class PickerWidgetEntry(
        val appLabel: String,
        val widgetLabel: String,
        val spanX: Int,
        val spanY: Int,
        val providerInfo: AppWidgetProviderInfo
    )

    /**
     * Computes clean iOS Home grid span (spanX x spanY) for any Android AppWidgetProviderInfo.
     */
    fun computeGridSpan(
        context: Context,
        info: AppWidgetProviderInfo,
        maxCols: Int,
        maxRows: Int
    ): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val tw = info.targetCellWidth
            val th = info.targetCellHeight
            if (tw > 0 && th > 0) {
                return Pair(tw.coerceIn(1, maxCols), th.coerceIn(1, maxRows))
            }
        }
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val minWdp = (info.minWidth / density).coerceAtLeast(40f)
        val minHdp = (info.minHeight / density).coerceAtLeast(40f)
        // Standard Android formula: cellSpan = ceil((minDp + 30) / 70)
        val spanX = ceil((minWdp + 30f) / 70f).toInt().coerceIn(1, maxCols)
        val spanY = ceil((minHdp + 30f) / 70f).toInt().coerceIn(1, maxRows)
        return Pair(spanX, spanY)
    }

    /**
     * Enumerates all installed Android system & third-party widgets (call on Dispatchers.IO).
     */
    fun loadAvailableAndroidWidgets(
        context: Context,
        maxCols: Int,
        maxRows: Int
    ): List<PickerWidgetEntry> {
        val manager = AppWidgetManager.getInstance(context) ?: return emptyList()
        val pm = context.packageManager
        val providers = try {
            manager.installedProviders.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        val entries = ArrayList<PickerWidgetEntry>(providers.size)
        for (info in providers) {
            val pkg = info.provider?.packageName ?: continue
            val appLabel = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo)?.toString()?.trim().orEmpty().ifEmpty { pkg }
            } catch (_: Exception) {
                pkg
            }
            val widgetLabel = try {
                info.loadLabel(pm)?.trim().orEmpty().ifEmpty { appLabel }
            } catch (_: Exception) {
                appLabel
            }
            val (sx, sy) = computeGridSpan(context, info, maxCols, maxRows)
            entries.add(
                PickerWidgetEntry(
                    appLabel = appLabel,
                    widgetLabel = widgetLabel,
                    spanX = sx,
                    spanY = sy,
                    providerInfo = info
                )
            )
        }
        entries.sortWith(compareBy<PickerWidgetEntry> { it.appLabel.lowercase() }.thenBy { it.widgetLabel.lowercase() })
        return entries
    }
}
