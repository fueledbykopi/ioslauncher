package com.iospring.launcher.core

import android.app.Activity
import android.app.WallpaperManager
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs

/**
 * Safe insets extracted for OPPO Android 15 (ColorOS 15) edge-to-edge gesture navigation.
 */
data class GestureSafeInsets(
    val statusBarTop: Int = 0,
    val navBarBottom: Int = 0,
    val gestureLeft: Int = 0,
    val gestureRight: Int = 0,
    val gestureBottom: Int = 0
)

/**
 * Handles OPPO ColorOS 15 / Android 15 gesture navigation compatibility:
 * - True edge-to-edge transparent status & navigation bars
 * - Protects bottom Home/Recents swipe-up zone & left/right Back gesture edges
 * - Smooth native wallpaper parallax via WallpaperManager.setWallpaperOffsets()
 * - Adaptive status bar icon contrast based on system wallpaper luminance
 * - GPU-accelerated Android 12+ RenderEffect blur for frosted glass overlays
 */
object ColorOsCompat {

    private var lastWallpaperSteps: Int = -1
    private var lastWallpaperProgress: Float = -1f
    private var lastStatusBarSyncMs: Long = 0L

    fun configureEdgeToEdge(activity: ComponentActivity) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        val window: Window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        syncStatusBarAppearanceWithWallpaper(activity)
    }

    fun syncStatusBarAppearanceWithWallpaper(activity: Activity, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && lastStatusBarSyncMs != 0L && now - lastStatusBarSyncMs < 30_000L) {
            return
        }
        lastStatusBarSyncMs = now
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        val isLightWallpaper = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val wm = WallpaperManager.getInstance(activity)
                val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                if (colors != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (colors.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
                } else if (colors != null) {
                    val primary = colors.primaryColor.toArgb()
                    val lum = (0.299 * Color.red(primary) + 0.587 * Color.green(primary) + 0.114 * Color.blue(primary)) / 255.0
                    lum > 0.68
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
        controller.isAppearanceLightStatusBars = isLightWallpaper
        controller.isAppearanceLightNavigationBars = isLightWallpaper
    }

    fun observeInsets(rootView: View, onInsetsChanged: (GestureSafeInsets) -> Unit) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val sysGestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            val mandatory = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())

            val density = rootView.resources.displayMetrics.density
            val minEdgePx = (24f * density).toInt()
            val maxEdgePx = (40f * density).toInt()
            val minBottomPx = (22f * density).toInt()
            val maxBottomPx = (56f * density).toInt()

            val safe = GestureSafeInsets(
                statusBarTop = sysBars.top,
                navBarBottom = sysBars.bottom,
                gestureLeft = sysGestures.left.coerceIn(minEdgePx, maxEdgePx),
                gestureRight = sysGestures.right.coerceIn(minEdgePx, maxEdgePx),
                gestureBottom = maxOf(sysGestures.bottom, mandatory.bottom, sysBars.bottom)
                    .coerceIn(minBottomPx, maxBottomPx)
            )
            onInsetsChanged(safe)
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    /**
     * Checks whether a touch starting at (x, y) lies inside ColorOS 15's reserved
     * system gesture edge zones (left/right back or bottom home/recents bar).
     */
    fun isInSystemGestureZone(
        x: Float,
        y: Float,
        viewWidth: Int,
        viewHeight: Int,
        insets: GestureSafeInsets,
        enabled: Boolean
    ): Boolean {
        if (!enabled || viewWidth <= 0 || viewHeight <= 0) return false
        val maxEdge = (viewWidth * 0.20f).toInt()
        val maxBottom = (viewHeight * 0.20f).toInt()
        val leftZone = insets.gestureLeft.coerceIn(0, maxEdge)
        val rightZone = insets.gestureRight.coerceIn(0, maxEdge)
        val bottomZone = insets.gestureBottom.coerceIn(0, maxBottom)
        if (x <= leftZone || x >= viewWidth - rightZone) return true
        if (y >= viewHeight - bottomZone) return true
        return false
    }

    fun isInBottomGestureZone(
        y: Float,
        viewHeight: Int,
        insets: GestureSafeInsets,
        enabled: Boolean
    ): Boolean {
        if (!enabled || viewHeight <= 0) return false
        val bottomZone = insets.gestureBottom.coerceIn(0, (viewHeight * 0.20f).toInt())
        return y >= viewHeight - bottomZone
    }

    /**
     * Updates OPPO system wallpaper horizontal parallax smoothly during page swiping.
     */
    fun updateWallpaperParallax(
        activity: Activity,
        rootView: View,
        normalizedProgress: Float,
        totalPages: Int
    ) {
        if (!normalizedProgress.isFinite()) return
        val token = rootView.windowToken ?: return
        val steps = (totalPages - 1).coerceAtLeast(1)
        val clamped = normalizedProgress.coerceIn(0f, 1f)
        if (steps == lastWallpaperSteps && abs(clamped - lastWallpaperProgress) < 0.0005f) {
            return
        }
        try {
            val wm = WallpaperManager.getInstance(activity)
            if (steps != lastWallpaperSteps) {
                wm.setWallpaperOffsetSteps(1f / steps, 0f)
                lastWallpaperSteps = steps
            }
            wm.setWallpaperOffsets(token, clamped, 0.5f)
            lastWallpaperProgress = clamped
        } catch (_: Exception) {
            // Safe no-op on restricted ROMs or headless tests
        }
    }

    /**
     * Applies subtle Android 11+ wallpaper zoom-out when opening Spotlight / Folder / Edit mode.
     * Invoked via reflection because setWallpaperZoomOut is a @SystemApi on public SDK stubs.
     */
    fun setWallpaperZoom(activity: Activity, rootView: View, zoomFraction: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val token = rootView.windowToken ?: return
        try {
            val wm = WallpaperManager.getInstance(activity)
            val method = WallpaperManager::class.java.getMethod(
                "setWallpaperZoomOut",
                IBinder::class.java,
                Float::class.javaPrimitiveType
            )
            method.invoke(wm, token, zoomFraction.coerceIn(0f, 1f))
        } catch (_: Exception) {
            // Ignore on devices/ROMs restricting wallpaper zoom
        }
    }

    /**
     * Applies GPU-accelerated RenderEffect blur (Android 12+) to a target view when overlays open.
     */
    fun applyContentBlur(targetView: View, radiusPx: Float, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled && radiusPx > 0.5f) {
                targetView.setRenderEffect(
                    RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                )
            } else {
                targetView.setRenderEffect(null)
            }
        }
    }

    fun isOppoColorOs(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return brand.contains("oppo") ||
            manufacturer.contains("oppo") ||
            brand.contains("realme") ||
            brand.contains("oneplus")
    }
}
