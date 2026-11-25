package com.iospring.launcher.core

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.LruCache
import com.iospring.launcher.model.IconThemeStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Generates authentic iOS continuous-curvature superellipse (squircle) paths and
 * renders Android icons into crisp iOS 18 Light, Dark, or Tinted icon bitmaps.
 */
object SquircleMath {
    /**
     * Builds a smooth Lamé superellipse path (|x|^n + |y|^n = 1, n = 4.6)
     * closely matching Apple's continuous icon corner curvature (~22.37%).
     */
    fun createSquirclePath(rect: RectF, exponent: Float = 4.6f, segments: Int = 72): Path {
        val path = Path()
        val cx = rect.centerX()
        val cy = rect.centerY()
        val rx = rect.width() / 2f
        val ry = rect.height() / 2f
        val invN = 2.0 / exponent

        for (i in 0..segments) {
            val theta = (2.0 * PI * i) / segments
            val cosT = cos(theta)
            val sinT = sin(theta)
            val x = cx + rx * (sign(cosT) * abs(cosT).pow(invN)).toFloat()
            val y = cy + ry * (sign(sinT) * abs(sinT).pow(invN)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}

class IconCacheManager(private val context: Context) {

    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)
    private val maxCacheBytes: Int = run {
        val maxMem = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        (maxMem / 16).coerceIn(4 * 1024, 12 * 1024) // 4MB - 12MB cap for low RAM footprint
    }

    private val cache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val density = context.resources.displayMetrics.density
    private val iconSizePx: Int = (58f * density).toInt().coerceIn(96, 168)

    private val maskPath: Path = SquircleMath.createSquirclePath(
        RectF(0f, 0f, iconSizePx.toFloat(), iconSizePx.toFloat()),
        exponent = 4.8f
    )

    private var themeStyle: IconThemeStyle = IconThemeStyle.LIGHT
    private var tintColor: Int = Color.parseColor("#0A84FF")
    private var iosIconPackEnabled: Boolean = true

    fun updateTheme(style: IconThemeStyle, tint: Int, useIconPack: Boolean = iosIconPackEnabled) {
        if (themeStyle != style || tintColor != tint || iosIconPackEnabled != useIconPack) {
            themeStyle = style
            tintColor = tint
            iosIconPackEnabled = useIconPack
            cache.evictAll()
        }
    }

    fun trimMemory(aggressive: Boolean) {
        if (aggressive) {
            cache.evictAll()
        } else {
            // Keep Home & Dock icons hot in RAM while cutting memory usage in half
            cache.trimToSize((cache.size() / 2).coerceAtLeast(1024))
        }
    }

    fun clearMemory() {
        cache.evictAll()
    }

    fun getIcon(packageName: String, className: String, label: String = ""): Bitmap {
        val packFlag = if (iosIconPackEnabled) 1 else 0
        val key = "$packageName/$className|${themeStyle.ordinal}|$tintColor|$packFlag"
        cache.get(key)?.let { return it }

        val rendered = if (iosIconPackEnabled) {
            val packGlyph = IosIconPackRenderer.matchGlyph(packageName, label)
            if (packGlyph != null) {
                renderBuiltInPackSquircle(packGlyph)
            } else {
                renderIosSquircle(loadRawDrawable(packageName, className))
            }
        } else {
            renderIosSquircle(loadRawDrawable(packageName, className))
        }
        cache.put(key, rendered)
        return rendered
    }

    private fun renderBuiltInPackSquircle(glyph: IosIconPackRenderer.IconGlyph): Bitmap {
        val size = iconSizePx
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val saveCount = canvas.save()
        canvas.clipPath(maskPath)
        IosIconPackRenderer.drawPackIcon(canvas, size, glyph, themeStyle, tintColor)
        drawSpecularRim(canvas, size)
        canvas.restoreToCount(saveCount)
        return out
    }

    private fun loadRawDrawable(packageName: String, className: String): Drawable? {
        return try {
            val activities = launcherApps?.getActivityList(packageName, Process.myUserHandle()).orEmpty()
            val match = activities.firstOrNull { it.componentName.className == className }
                ?: activities.firstOrNull()
            match?.getBadgedIcon(0) ?: context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun renderIosSquircle(raw: Drawable?): Bitmap {
        val size = iconSizePx
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // Draw content onto a temporary layer and clip cleanly with squircle path
        val saveCount = canvas.save()
        canvas.clipPath(maskPath)

        when (themeStyle) {
            IconThemeStyle.LIGHT -> drawLightIcon(canvas, raw, size)
            IconThemeStyle.DARK -> drawDarkIcon(canvas, raw, size)
            IconThemeStyle.TINTED -> drawTintedIcon(canvas, raw, size, tintColor)
        }

        drawSpecularRim(canvas, size)
        canvas.restoreToCount(saveCount)
        return out
    }

    private fun drawSpecularRim(canvas: Canvas, size: Int) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (1.15f * density).coerceAtLeast(1f)
            shader = LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                if (themeStyle == IconThemeStyle.LIGHT) 0x4CFFFFFF else 0x36FFFFFF,
                0x16000000,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(maskPath, borderPaint)
    }

    private fun drawLightIcon(canvas: Canvas, raw: Drawable?, size: Int) {
        if (raw == null) {
            drawFallbackBackdrop(canvas, size, 0xFF3A3A3C.toInt(), 0xFF1C1C1E.toInt())
            return
        }
        if (raw is AdaptiveIconDrawable) {
            // Zoom slightly so Android's 108dp adaptive safe zone fills the iOS squircle edge-to-edge
            val bleed = (size * 0.14f).toInt()
            raw.background?.let { bg ->
                bg.setBounds(-bleed, -bleed, size + bleed, size + bleed)
                bg.draw(canvas)
            } ?: drawFallbackBackdrop(canvas, size, 0xFFFFFFFF.toInt(), 0xFFF2F2F7.toInt())

            raw.foreground?.let { fg ->
                fg.setBounds(-bleed, -bleed, size + bleed, size + bleed)
                fg.draw(canvas)
            }
        } else {
            // Legacy icon: sample whether edges are transparent; if so place on crisp white iOS tile
            val sample = renderDrawableToBitmap(raw, size)
            val hasTransparentCorners = isCornerTransparent(sample)
            if (hasTransparentCorners) {
                drawFallbackBackdrop(canvas, size, 0xFFFFFFFF.toInt(), 0xFFF2F2F7.toInt())
                val pad = (size * 0.12f).toInt()
                raw.setBounds(pad, pad, size - pad, size - pad)
                raw.draw(canvas)
            } else {
                canvas.drawBitmap(sample, 0f, 0f, null)
            }
            sample.recycle()
        }
    }

    private fun drawDarkIcon(canvas: Canvas, raw: Drawable?, size: Int) {
        // Authentic iOS 18 Dark Icon charcoal gradient plate
        drawFallbackBackdrop(canvas, size, 0xFF2E2E32.toInt(), 0xFF171719.toInt())
        if (raw == null) return

        val adaptiveFg = (raw as? AdaptiveIconDrawable)?.foreground
        if (adaptiveFg != null) {
            val bleed = (size * 0.13f).toInt()
            adaptiveFg.setBounds(-bleed, -bleed, size + bleed, size + bleed)
            adaptiveFg.draw(canvas)
        } else {
            // Slightly dim overly bright white plates on legacy icons while preserving color vibrancy
            val pad = (size * 0.11f).toInt()
            val tmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val tmpCanvas = Canvas(tmp)
            raw.setBounds(pad, pad, size - pad, size - pad)
            raw.draw(tmpCanvas)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(tmp, 0f, 0f, paint)
            tmp.recycle()
        }
    }

    private fun drawTintedIcon(canvas: Canvas, raw: Drawable?, size: Int, tint: Int) {
        // Deep iOS 18 glass dark backdrop + tinted monochrome foreground
        val darkTintTop = blendColors(0xFF202024.toInt(), tint, 0.16f)
        val darkTintBottom = blendColors(0xFF121215.toInt(), tint, 0.08f)
        drawFallbackBackdrop(canvas, size, darkTintTop, darkTintBottom)
        if (raw == null) return

        val tmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val tmpCanvas = Canvas(tmp)
        val adaptiveFg = (raw as? AdaptiveIconDrawable)?.foreground
        if (adaptiveFg != null) {
            val bleed = (size * 0.14f).toInt()
            adaptiveFg.setBounds(-bleed, -bleed, size + bleed, size + bleed)
            adaptiveFg.draw(tmpCanvas)
        } else {
            val pad = (size * 0.12f).toInt()
            raw.setBounds(pad, pad, size - pad, size - pad)
            raw.draw(tmpCanvas)
        }

        // Convert to luminous grayscale then tint with chosen iOS 18 hue
        val r = Color.red(tint) / 255f
        val g = Color.green(tint) / 255f
        val b = Color.blue(tint) / 255f
        val matrix = ColorMatrix(
            floatArrayOf(
                0.35f * r + 0.15f, 0.50f * r, 0.15f * r, 0f, r * 48f,
                0.35f * g, 0.50f * g + 0.15f, 0.15f * g, 0f, g * 48f,
                0.35f * b, 0.50f * b, 0.15f * b + 0.15f, 0f, b * 48f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(tmp, 0f, 0f, paint)
        tmp.recycle()
    }

    private fun drawFallbackBackdrop(canvas: Canvas, size: Int, topColor: Int, bottomColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                topColor, bottomColor, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    }

    private fun renderDrawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(c)
        return bmp
    }

    private fun isCornerTransparent(bmp: Bitmap): Boolean {
        val pad = (bmp.width * 0.08f).toInt().coerceAtLeast(2)
        val w = bmp.width - 1 - pad
        val h = bmp.height - 1 - pad
        val points = intArrayOf(
            bmp.getPixel(pad, pad),
            bmp.getPixel(w, pad),
            bmp.getPixel(pad, h),
            bmp.getPixel(w, h)
        )
        return points.count { Color.alpha(it) < 220 } >= 2
    }

    private fun blendColors(base: Int, overlay: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(base) * inv + Color.red(overlay) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(base) * inv + Color.green(overlay) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(base) * inv + Color.blue(overlay) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
