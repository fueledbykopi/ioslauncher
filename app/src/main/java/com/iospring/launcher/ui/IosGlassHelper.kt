package com.iospring.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.iospring.launcher.model.GlassThemeStyle
import com.iospring.launcher.model.IconThemeStyle

/**
 * Shared Apple iOS Liquid Glass & Frosted Blur styling, typography tokens, and haptic utilities.
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

    /**
     * Multi-stop iOS 18 frosted glass surface with top luminous sheen and crisp specular rim.
     */
    fun createFrostedSurface(
        context: Context,
        cornerRadiusDp: Float,
        topFillColor: Int = 0x56F5F5FA,
        bottomFillColor: Int = 0x36D8D8E2,
        strokeColor: Int = 0x54FFFFFF,
        strokeWidthDp: Float = 0.9f
    ): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topFillColor, bottomFillColor)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, cornerRadiusDp)
            setStroke(dpInt(context, strokeWidthDp).coerceAtLeast(1), strokeColor)
        }
    }

    /**
     * Authentic latest Apple iOS surface builder supporting:
     * - LIQUID_GLASS: Optical refractive translucent glass with directional specular edge & top caustic sheen
     * - FROSTED_BLUR: Classic rich iOS 18 milky frosted glass
     * - OBSIDIAN_GLASS: Deep smoked dark glass with titanium rim
     */
    fun createThemeSurface(
        context: Context,
        cornerRadiusDp: Float,
        glassStyle: GlassThemeStyle,
        iconStyle: IconThemeStyle = IconThemeStyle.LIGHT,
        strongSurface: Boolean = false
    ): Drawable {
        val radiusPx = dp(context, cornerRadiusDp)
        val strokePx = dp(context, if (glassStyle == GlassThemeStyle.LIQUID_GLASS) 1.15f else 0.9f)
        val darkBias = iconStyle != IconThemeStyle.LIGHT

        return when (glassStyle) {
            GlassThemeStyle.LIQUID_GLASS -> {
                if (strongSurface) {
                    IosLiquidGlassDrawable(
                        cornerRadiusPx = radiusPx,
                        strokeWidthPx = strokePx,
                        topFill = if (darkBias) 0x8224242A.toInt() else 0x52FFFFFF,
                        midFill = if (darkBias) 0x6A16161C.toInt() else 0x2EEAF0FA,
                        bottomFill = if (darkBias) 0x7A1C1C22.toInt() else 0x42DCE4F4,
                        topSpecSheen = if (darkBias) 0x30FFFFFF else 0x56FFFFFF,
                        rimTop = if (darkBias) 0x78FFFFFF else 0xB8FFFFFF.toInt(),
                        rimMid = if (darkBias) 0x26FFFFFF else 0x40FFFFFF,
                        rimBottom = if (darkBias) 0x52FFFFFF else 0x84FFFFFF.toInt()
                    )
                } else {
                    IosLiquidGlassDrawable(
                        cornerRadiusPx = radiusPx,
                        strokeWidthPx = strokePx,
                        topFill = if (darkBias) 0x4A222228.toInt() else 0x38FFFFFF,
                        midFill = if (darkBias) 0x30141418.toInt() else 0x1AF0F4FF,
                        bottomFill = if (darkBias) 0x3E1C1C22.toInt() else 0x2CE2E8F6,
                        topSpecSheen = if (darkBias) 0x28FFFFFF else 0x4CFFFFFF,
                        rimTop = if (darkBias) 0x6EFFFFFF else 0xAAFFFFFF.toInt(),
                        rimMid = if (darkBias) 0x20FFFFFF else 0x38FFFFFF,
                        rimBottom = if (darkBias) 0x48FFFFFF else 0x78FFFFFF
                    )
                }
            }

            GlassThemeStyle.FROSTED_BLUR -> {
                if (strongSurface) {
                    createFrostedSurface(
                        context = context,
                        cornerRadiusDp = cornerRadiusDp,
                        topFillColor = if (darkBias) 0xB828282C.toInt() else 0x70F5F5FA,
                        bottomFillColor = if (darkBias) 0xA818181C.toInt() else 0x56DCDCE6,
                        strokeColor = if (darkBias) 0x38FFFFFF else 0x62FFFFFF
                    )
                } else {
                    createFrostedSurface(
                        context = context,
                        cornerRadiusDp = cornerRadiusDp,
                        topFillColor = if (darkBias) 0x782C2C30 else 0x5CF2F2F8,
                        bottomFillColor = if (darkBias) 0x5C161618 else 0x40D6D6E0,
                        strokeColor = if (darkBias) 0x36FFFFFF else 0x5CFFFFFF
                    )
                }
            }

            GlassThemeStyle.OBSIDIAN_GLASS -> {
                IosLiquidGlassDrawable(
                    cornerRadiusPx = radiusPx,
                    strokeWidthPx = strokePx,
                    topFill = if (strongSurface) 0xCC1E1E22.toInt() else 0x781A1A20,
                    midFill = if (strongSurface) 0xC0121216.toInt() else 0x58101014,
                    bottomFill = if (strongSurface) 0xC816161B.toInt() else 0x6C141419,
                    topSpecSheen = 0x22FFFFFF,
                    rimTop = 0x58FFFFFF,
                    rimMid = 0x1CFFFFFF,
                    rimBottom = 0x34FFFFFF
                )
            }
        }
    }

    fun blurRadiusFor(glassStyle: GlassThemeStyle, strong: Boolean = false): Float {
        return when (glassStyle) {
            GlassThemeStyle.LIQUID_GLASS -> if (strong) 22f else 14f
            GlassThemeStyle.FROSTED_BLUR -> if (strong) 30f else 24f
            GlassThemeStyle.OBSIDIAN_GLASS -> if (strong) 24f else 18f
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

/**
 * Zero-allocation, GPU-friendly Apple Liquid Glass Drawable.
 * Simulates curved optical refraction with:
 * 1. Translucent 3-stop optical glass body
 * 2. Top-edge curved caustic sheen
 * 3. Directional top/bottom bright specular rim stroke
 */
class IosLiquidGlassDrawable(
    private val cornerRadiusPx: Float,
    private val strokeWidthPx: Float,
    private val topFill: Int,
    private val midFill: Int,
    private val bottomFill: Int,
    private val topSpecSheen: Int,
    private val rimTop: Int,
    private val rimMid: Int,
    private val rimBottom: Int
) : Drawable() {

    private val rect = RectF()
    private val sheenRect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val halfStroke = strokeWidthPx * 0.5f
        rect.set(
            bounds.left + halfStroke,
            bounds.top + halfStroke,
            bounds.right - halfStroke,
            bounds.bottom - halfStroke
        )
        val h = rect.height().coerceAtLeast(1f)
        sheenRect.set(
            rect.left + strokeWidthPx,
            rect.top + strokeWidthPx,
            rect.right - strokeWidthPx,
            rect.top + (h * 0.42f)
        )
        fillPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            intArrayOf(topFill, midFill, bottomFill),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        sheenPaint.shader = LinearGradient(
            sheenRect.left, sheenRect.top, sheenRect.left, sheenRect.bottom,
            topSpecSheen,
            0x00FFFFFF,
            Shader.TileMode.CLAMP
        )
        rimPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right * 0.35f + rect.left * 0.65f, rect.bottom,
            intArrayOf(rimTop, rimMid, rimBottom),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val r = minOf(cornerRadiusPx, rect.height() * 0.5f, rect.width() * 0.5f)
        canvas.drawRoundRect(rect, r, r, fillPaint)
        canvas.drawRoundRect(sheenRect, r, r, sheenPaint)
        canvas.drawRoundRect(rect, r, r, rimPaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        sheenPaint.alpha = alpha
        rimPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        sheenPaint.colorFilter = colorFilter
        rimPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
