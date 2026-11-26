package com.iospring.launcher.core

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.iospring.launcher.model.GlassThemeStyle
import com.iospring.launcher.model.IconThemeStyle
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Generates authentic iOS continuous-curvature superellipse (squircle) paths and
 * renders Android icons into crisp iOS 18+ Light, Dark, or Tinted icon bitmaps.
 */
object SquircleMath {
    private val pathCache = LruCache<Int, Path>(12)

    /**
     * Builds a smooth Lamé superellipse path (|x|^n + |y|^n = 1, n = 4.6)
     * closely matching Apple's continuous icon corner curvature (~22.37%).
     */
    fun createSquirclePath(rect: RectF, exponent: Float = 4.6f, segments: Int = 64): Path {
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

    /**
     * Returns a cached unit-origin square squircle path of size [sizePx] x [sizePx]
     * to avoid recomputing trigonometric curves on every folder/icon resize.
     */
    fun getCachedSquareSquircle(sizePx: Int, exponent: Float = 4.6f): Path {
        val clamped = sizePx.coerceAtLeast(1)
        val key = (clamped shl 4) xor ((exponent * 10f).toInt() and 0xF)
        pathCache.get(key)?.let { return it }
        val built = createSquirclePath(RectF(0f, 0f, clamped.toFloat(), clamped.toFloat()), exponent)
        pathCache.put(key, built)
        return built
    }
}

class IconCacheManager(private val context: Context) {

    private val packageManager = context.packageManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iconExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "iospring-icon").apply {
            priority = Thread.NORM_PRIORITY - 1
            isDaemon = true
        }
    }
    private val themeGeneration = AtomicInteger(0)

    private val maxCacheKb: Int = run {
        val maxMemKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        (maxMemKb / 20).coerceIn(3 * 1024, 10 * 1024) // Tight 3MB - 10MB cap for minimal RAM footprint
    }

    private val cache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    private val density = context.resources.displayMetrics.density
    private val iconSizePx: Int = (56f * density).toInt().coerceIn(96, 144)

    private val maskPath: Path = SquircleMath.getCachedSquareSquircle(iconSizePx, exponent = 4.8f)

    @Volatile
    private var themeStyle: IconThemeStyle = IconThemeStyle.DARK
    @Volatile
    private var glassStyle: GlassThemeStyle = GlassThemeStyle.LIQUID_GLASS
    @Volatile
    private var tintColor: Int = Color.parseColor("#0A84FF")
    @Volatile
    private var iosIconPackEnabled: Boolean = true

    @Volatile
    private var cachedTintFilter: ColorMatrixColorFilter = buildTintColorFilter(tintColor)

    fun updateTheme(
        style: IconThemeStyle,
        tint: Int,
        useIconPack: Boolean = iosIconPackEnabled,
        glass: GlassThemeStyle = glassStyle
    ) {
        if (themeStyle != style || tintColor != tint || iosIconPackEnabled != useIconPack || glassStyle != glass) {
            themeStyle = style
            if (tintColor != tint) {
                tintColor = tint
                cachedTintFilter = buildTintColorFilter(tint)
            }
            iosIconPackEnabled = useIconPack
            glassStyle = glass
            themeGeneration.incrementAndGet()
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

    fun close() {
        iconExecutor.shutdownNow()
        cache.evictAll()
    }

    private fun cacheKey(packageName: String, className: String): String {
        val packFlag = if (iosIconPackEnabled) 1 else 0
        return "$packageName/$className|${themeStyle.ordinal}|${glassStyle.ordinal}|$tintColor|$packFlag"
    }

    /**
     * Zero-blocking O(1) RAM cache lookup for smooth 120Hz scrolling.
     */
    fun getCachedIcon(packageName: String, className: String): Bitmap? {
        return cache.get(cacheKey(packageName, className))
    }

    /**
     * Loads icon asynchronously on a low-priority background thread if not yet cached,
     * delivering the bitmap on the Main thread only if the theme generation hasn't changed.
     */
    fun loadIconAsync(
        packageName: String,
        className: String,
        label: String = "",
        onLoaded: (Bitmap) -> Unit
    ) {
        val cached = getCachedIcon(packageName, className)
        if (cached != null) {
            onLoaded(cached)
            return
        }
        val gen = themeGeneration.get()
        iconExecutor.execute {
            if (themeGeneration.get() != gen) return@execute
            val bmp = getIcon(packageName, className, label)
            if (themeGeneration.get() == gen) {
                mainHandler.post {
                    if (themeGeneration.get() == gen) {
                        onLoaded(bmp)
                    }
                }
            }
        }
    }

    fun getIcon(packageName: String, className: String, label: String = ""): Bitmap {
        val key = cacheKey(packageName, className)
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
        // Fast direct ComponentName IPC (avoids expensive getActivityList enumeration)
        return try {
            if (className.isNotEmpty()) {
                packageManager.getActivityIcon(ComponentName(packageName, className))
            } else {
                packageManager.getApplicationIcon(packageName)
            }
        } catch (_: Exception) {
            try {
                packageManager.getApplicationIcon(packageName)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun renderIosSquircle(raw: Drawable?): Bitmap {
        val size = iconSizePx
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

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
        val s = size.toFloat()
        val isLight = themeStyle == IconThemeStyle.LIGHT
        when (glassStyle) {
            GlassThemeStyle.LIQUID_GLASS -> {
                // Apple iOS 26 Liquid Glass: curved diagonal optical meniscus + dual-corner specular rim
                val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, s * 0.45f, s * 0.48f,
                        if (isLight) 0x3EFFFFFF else 0x2EFFFFFF,
                        0x00FFFFFF,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, s, s * 0.48f, sheenPaint)
                val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (1.6f * density).coerceAtLeast(1.6f)
                    shader = LinearGradient(
                        0f, 0f, s, s,
                        intArrayOf(
                            if (isLight) 0xB4FFFFFF.toInt() else 0x84FFFFFF.toInt(),
                            0x1AFFFFFF,
                            0x1AFFFFFF,
                            if (isLight) 0x6AFFFFFF else 0x4CFFFFFF
                        ),
                        floatArrayOf(0f, 0.36f, 0.68f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawPath(maskPath, rimPaint)
            }

            GlassThemeStyle.FROSTED_GLASS -> {
                // Apple Frosted Glass: subtle top satin chamfer glow + crisp crystalline rim
                val chamferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, s * 0.28f,
                        if (isLight) 0x28FFFFFF else 0x1CFFFFFF,
                        0x00FFFFFF,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, s, s * 0.28f, chamferPaint)
                val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (1.25f * density).coerceAtLeast(1.2f)
                    shader = LinearGradient(
                        0f, 0f, 0f, s,
                        intArrayOf(
                            if (isLight) 0x7AFFFFFF else 0x52FFFFFF,
                            0x1EFFFFFF,
                            0x18000000
                        ),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawPath(maskPath, rimPaint)
            }

            GlassThemeStyle.FROSTED_BLUR,
            GlassThemeStyle.OBSIDIAN_GLASS -> {
                // Classic Apple iOS 18 soft precision hairline border
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (1.1f * density).coerceAtLeast(1f)
                    shader = LinearGradient(
                        0f, 0f, 0f, s,
                        if (isLight) 0x4CFFFFFF else 0x36FFFFFF,
                        0x16000000,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawPath(maskPath, borderPaint)
            }
        }
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
            // Draw directly onto the canvas (zero extra scratch bitmap allocation!)
            val pad = (size * 0.11f).toInt()
            raw.setBounds(pad, pad, size - pad, size - pad)
            raw.draw(canvas)
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

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = cachedTintFilter
        }
        canvas.drawBitmap(tmp, 0f, 0f, paint)
        tmp.recycle()
    }

    private fun buildTintColorFilter(tint: Int): ColorMatrixColorFilter {
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
        return ColorMatrixColorFilter(matrix)
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
