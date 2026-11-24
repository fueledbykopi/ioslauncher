package com.iospring.launcher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Shared iOS 18 frosted-glass styling, typography tokens, and haptic utilities.
 */
object IosDesign {

    val IOS_BLUE: Int = 0xFF0A84FF.toInt()
    val IOS_RED: Int = 0xFFFF3B30.toInt()
    val IOS_GREEN: Int = 0xFF30D158.toInt()
    val IOS_ORANGE: Int = 0xFFFF9F0A.toInt()
    val IOS_PURPLE: Int = 0xFFBF5AF2.toInt()
    val IOS_PINK: Int = 0xFFFF375F.toInt()
    val IOS_TEAL: Int = 0xFF64D2FF.toInt()
    val IOS_DARK_SURFACE: Int = 0xCC1C1C1E.toInt()
    const val IOS_LIGHT_GLASS: Int = 0x42FFFFFF
    const val IOS_DOCK_GLASS: Int = 0x44EAEAF0
    const val IOS_DOCK_DARK_GLASS: Int = 0x661C1C1E
    const val IOS_GLASS_STROKE: Int = 0x38FFFFFF

    val TINT_PRESETS = listOf(
        "iOS Blue" to IOS_BLUE,
        "Mint" to IOS_GREEN,
        "Coral" to IOS_PINK,
        "Amber" to IOS_ORANGE,
        "Lavender" to IOS_PURPLE,
        "Sky" to IOS_TEAL,
        "Silver" to 0xFFE5E5EA.toInt()
    )

    fun dp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density

    fun dpInt(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun sp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.scaledDensity

    fun createGlassDrawable(
        context: Context,
        cornerRadiusDp: Float,
        fillColor: Int = IOS_LIGHT_GLASS,
        strokeColor: Int = IOS_GLASS_STROKE,
        strokeWidthDp: Float = 0.8f
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, cornerRadiusDp)
            setColor(fillColor)
            setStroke(dpInt(context, strokeWidthDp).coerceAtLeast(1), strokeColor)
        }
    }

    fun createPillDrawable(
        context: Context,
        fillColor: Int,
        strokeColor: Int = IOS_GLASS_STROKE
    ): GradientDrawable = createGlassDrawable(context, 999f, fillColor, strokeColor, 0.8f)

    fun hapticTick(view: View, enabled: Boolean) {
        if (!enabled) return
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            HapticFeedbackConstants.KEYBOARD_TAP
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(flag)
    }

    fun hapticLongPress(view: View, enabled: Boolean) {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun isDarkColor(color: Int): Boolean {
        val lum = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return lum < 0.5
    }
}
