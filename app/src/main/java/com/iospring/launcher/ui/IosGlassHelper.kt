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
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import com.iospring.launcher.model.GlassThemeStyle
import com.iospring.launcher.model.IconThemeStyle

/**
 * Shared Apple iOS Liquid Glass, Frosted Glass & Frosted Blur styling,
 * shared Typography/Interpolator singletons (zero per-view allocations), and haptic utilities.
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

    // Shared immutable font & interpolator singletons to cut per-view memory allocations
    val TYPEFACE_REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val TYPEFACE_MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val TYPEFACE_MEDIUM_BOLD: Typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    val TYPEFACE_LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val TYPEFACE_LIGHT_BOLD: Typeface = Typeface.create("sans-serif-light", Typeface.BOLD)
    val RELEASE_OVERSHOOT = OvershootInterpolator(1.15f)

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
     * Multi-stop iOS frosted surface with subtle vertical luminosity gradient and crisp rim.
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
     * Authentic Apple iOS material surface builder supporting:
     * - LIQUID_GLASS: Apple iOS 26 optical refractive glass with top caustic sheen & directional specular rim
     * - FROSTED_GLASS: Apple etched satin frosted glass with luminous top chamfer bevel & crystalline edge
     * - FROSTED_BLUR: Apple iOS UIVisualEffectView creamy Gaussian frosted material with soft vibrancy veil
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
        val darkBias = iconStyle != IconThemeStyle.LIGHT

        return when (glassStyle) {
            GlassThemeStyle.LIQUID_GLASS -> {
                val strokePx = dp(context, 1.15f)
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

            GlassThemeStyle.FROSTED_GLASS -> {
                // Apple Frosted Glass: luminous etched satin frost + crisp inner top chamfer + crystalline hairline rim
                val strokePx = dp(context, 0.9f)
                val innerBevelPx = dp(context, 1.15f)
                if (strongSurface) {
                    IosFrostedGlassDrawable(
                        cornerRadiusPx = radiusPx,
                        strokeWidthPx = strokePx,
                        innerBevelWidthPx = innerBevelPx,
                        topFill = if (darkBias) 0xB428282E.toInt() else 0x80F8F9FE.toInt(),
                        midFill = if (darkBias) 0xA21D1D22.toInt() else 0x6BECEEF7,
                        bottomFill = if (darkBias) 0xAE18181D.toInt() else 0x74DFE2EE,
                        topChamferGlow = if (darkBias) 0x2AFFFFFF else 0x52FFFFFF,
                        rimTop = if (darkBias) 0x5AFFFFFF else 0x9EFFFFFF.toInt(),
                        rimMid = if (darkBias) 0x24FFFFFF else 0x48FFFFFF,
                        rimBottom = if (darkBias) 0x36FFFFFF else 0x64FFFFFF
                    )
                } else {
                    IosFrostedGlassDrawable(
                        cornerRadiusPx = radiusPx,
                        strokeWidthPx = strokePx,
                        innerBevelWidthPx = innerBevelPx,
                        topFill = if (darkBias) 0x7825252B else 0x5CF7F8FD,
                        midFill = if (darkBias) 0x641B1B20 else 0x48EAEBF4,
                        bottomFill = if (darkBias) 0x7016161B else 0x52DCDFEC,
                        topChamferGlow = if (darkBias) 0x22FFFFFF else 0x46FFFFFF,
                        rimTop = if (darkBias) 0x54FFFFFF else 0x8CFFFFFF.toInt(),
                        rimMid = if (darkBias) 0x1EFFFFFF else 0x3AFFFFFF,
                        rimBottom = if (darkBias) 0x30FFFFFF else 0x52FFFFFF
                    )
                }
            }

            GlassThemeStyle.FROSTED_BLUR -> {
                // Apple UIVisualEffectView Frosted Blur: milky diffused atmospheric veil + whisper-thin edge
                val strokePx = dp(context, 0.7f)
                if (strongSurface) {
                    IosFrostedBlurDrawable(
                        cornerRadiusPx = radiusPx,
                        strokeWidthPx = strokePx,
                        topFill = if (darkBias) 0xC8242429.toInt() else 0x90F4F5FA.toInt(),
                        bottomFill = if (darkBias) 0xBC18181D.toInt() else 0x7CE0E2EC,
                        innerVeil = if (darkBias) 0x14FFFFFF else 0x28FFFFFF,
                        rimColor = if (darkBias) 0x32FFFFFF else 0x52FFFFFF
                    )
                } else {
                    IosFrostedBlurDrawable(
                        cornerRadiusPx = radiusPx,
                        strokeWidthPx = strokePx,
                        topFill = if (darkBias) 0x8E222227.toInt() else 0x72F2F3F8,
                        bottomFill = if (darkBias) 0x7C16161B else 0x5ADCE0EC,
                        innerVeil = if (darkBias) 0x10FFFFFF else 0x22FFFFFF,
                        rimColor = if (darkBias) 0x2AFFFFFF else 0x46FFFFFF
                    )
                }
            }

            GlassThemeStyle.OBSIDIAN_GLASS -> {
                val strokePx = dp(context, 0.95f)
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
            GlassThemeStyle.FROSTED_GLASS -> if (strong) 26f else 18f
            GlassThemeStyle.FROSTED_BLUR -> if (strong) 36f else 28f
            GlassThemeStyle.OBSIDIAN_GLASS -> if (strong) 24f else 18f
        }
    }

    fun vibrancySaturationFor(glassStyle: GlassThemeStyle): Float {
        return when (glassStyle) {
            GlassThemeStyle.LIQUID_GLASS -> 1.18f
            GlassThemeStyle.FROSTED_GLASS -> 1.24f
            GlassThemeStyle.FROSTED_BLUR -> 1.42f
            GlassThemeStyle.OBSIDIAN_GLASS -> 1.12f
        }
    }

    fun windowBlurRadiusFor(glassStyle: GlassThemeStyle, strong: Boolean = false): Int {
        return when (glassStyle) {
            GlassThemeStyle.LIQUID_GLASS -> if (strong) 28 else 16
            GlassThemeStyle.FROSTED_GLASS -> if (strong) 34 else 20
            GlassThemeStyle.FROSTED_BLUR -> if (strong) 48 else 32
            GlassThemeStyle.OBSIDIAN_GLASS -> if (strong) 32 else 20
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

/**
 * Zero-allocation Apple Frosted Glass Drawable.
 * Authentic satin etched Apple glass sheet featuring:
 * 1. Smooth 3-stop satin cool-frost body
 * 2. Crisp luminous top-edge chamfer reflection (Apple's hallmark frosted bevel)
 * 3. Precision crystalline hairline outer rim
 */
class IosFrostedGlassDrawable(
    private val cornerRadiusPx: Float,
    private val strokeWidthPx: Float,
    private val innerBevelWidthPx: Float,
    private val topFill: Int,
    private val midFill: Int,
    private val bottomFill: Int,
    private val topChamferGlow: Int,
    private val rimTop: Int,
    private val rimMid: Int,
    private val rimBottom: Int
) : Drawable() {

    private val rect = RectF()
    private val innerRect = RectF()
    private val chamferRect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val chamferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val innerBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = innerBevelWidthPx
    }
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
        val inset = strokeWidthPx + innerBevelWidthPx * 0.5f
        innerRect.set(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset
        )
        val h = rect.height().coerceAtLeast(1f)
        chamferRect.set(
            innerRect.left,
            innerRect.top,
            innerRect.right,
            innerRect.top + (h * 0.24f)
        )
        fillPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            intArrayOf(topFill, midFill, bottomFill),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        chamferPaint.shader = LinearGradient(
            chamferRect.left, chamferRect.top, chamferRect.left, chamferRect.bottom,
            topChamferGlow,
            0x00FFFFFF,
            Shader.TileMode.CLAMP
        )
        innerBevelPaint.shader = LinearGradient(
            innerRect.left, innerRect.top, innerRect.left, innerRect.bottom,
            intArrayOf(topChamferGlow, 0x00FFFFFF, 0x10000000),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP
        )
        rimPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            intArrayOf(rimTop, rimMid, rimBottom),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val r = minOf(cornerRadiusPx, rect.height() * 0.5f, rect.width() * 0.5f)
        val innerR = (r - strokeWidthPx).coerceAtLeast(0f)
        canvas.drawRoundRect(rect, r, r, fillPaint)
        canvas.drawRoundRect(chamferRect, innerR, innerR, chamferPaint)
        canvas.drawRoundRect(innerRect, innerR, innerR, innerBevelPaint)
        canvas.drawRoundRect(rect, r, r, rimPaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        chamferPaint.alpha = alpha
        innerBevelPaint.alpha = alpha
        rimPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        chamferPaint.colorFilter = colorFilter
        innerBevelPaint.colorFilter = colorFilter
        rimPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Zero-allocation Apple UIVisualEffectView Frosted Blur Drawable.
 * Creamy Gaussian diffused backdrop material with soft atmospheric luminance
 * and Apple's whisper-thin hairline separator border.
 */
class IosFrostedBlurDrawable(
    private val cornerRadiusPx: Float,
    private val strokeWidthPx: Float,
    private val topFill: Int,
    private val bottomFill: Int,
    private val innerVeil: Int,
    private val rimColor: Int
) : Drawable() {

    private val rect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = rimColor
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
        fillPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            topFill,
            bottomFill,
            Shader.TileMode.CLAMP
        )
        val halfVeil = ((Color.alpha(innerVeil) / 2) shl 24) or (innerVeil and 0x00FFFFFF)
        veilPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            intArrayOf(innerVeil, 0x00FFFFFF, halfVeil),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val r = minOf(cornerRadiusPx, rect.height() * 0.5f, rect.width() * 0.5f)
        canvas.drawRoundRect(rect, r, r, fillPaint)
        canvas.drawRoundRect(rect, r, r, veilPaint)
        canvas.drawRoundRect(rect, r, r, rimPaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        veilPaint.alpha = alpha
        rimPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        veilPaint.colorFilter = colorFilter
        rimPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
