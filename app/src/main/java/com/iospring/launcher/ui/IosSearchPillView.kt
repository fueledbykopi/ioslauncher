package com.iospring.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.iospring.launcher.model.GlassThemeStyle
import com.iospring.launcher.model.IconThemeStyle

/**
 * Signature iOS 16-26 floating "Search" frosted capsule above the Dock.
 * Dynamically measures its text so "Search" is NEVER clipped on any DPI or font scale,
 * and smoothly morphs into page indicator dots during horizontal scrolling.
 */
class IosSearchPillView(context: Context) : View(context) {

    var totalHomePages: Int = 1
        set(value) {
            val clamped = value.coerceAtLeast(1)
            if (field != clamped) {
                field = clamped
                requestLayout()
                invalidate()
            }
        }

    var currentHomePage: Int = 0
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var showSearchLabel: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var glassThemeStyle: GlassThemeStyle = GlassThemeStyle.LIQUID_GLASS
        set(value) {
            if (field != value) {
                field = value
                applyThemeColors()
                invalidate()
            }
        }

    var iconThemeStyle: IconThemeStyle = IconThemeStyle.DARK
        set(value) {
            if (field != value) {
                field = value
                applyThemeColors()
                invalidate()
            }
        }

    private fun applyThemeColors() {
        val darkBias = iconThemeStyle != IconThemeStyle.LIGHT
        when (glassThemeStyle) {
            GlassThemeStyle.LIQUID_GLASS -> {
                bgPaint.color = if (darkBias) 0x58181A22 else 0x34FFFFFF
                topHighlightPaint.color = if (darkBias) 0x24FFFFFF else 0x38FFFFFF
                strokePaint.color = if (darkBias) 0x82FFFFFF.toInt() else 0xA8FFFFFF.toInt()
                strokePaint.strokeWidth = IosDesign.dp(context, 1.15f)
            }
            GlassThemeStyle.FROSTED_GLASS -> {
                bgPaint.color = if (darkBias) 0x721E1F26 else 0x5AF6F7FC
                topHighlightPaint.color = if (darkBias) 0x1EFFFFFF else 0x30FFFFFF
                strokePaint.color = if (darkBias) 0x5AFFFFFF else 0x80FFFFFF.toInt()
                strokePaint.strokeWidth = IosDesign.dp(context, 0.9f)
            }
            GlassThemeStyle.FROSTED_BLUR -> {
                bgPaint.color = if (darkBias) 0x821B1C22.toInt() else 0x6EF2F3F8
                topHighlightPaint.color = 0x14FFFFFF
                strokePaint.color = if (darkBias) 0x34FFFFFF else 0x48FFFFFF
                strokePaint.strokeWidth = IosDesign.dp(context, 0.7f)
            }
            GlassThemeStyle.OBSIDIAN_GLASS -> {
                bgPaint.color = 0x86141418.toInt()
                topHighlightPaint.color = 0x18FFFFFF
                strokePaint.color = 0x48FFFFFF
                strokePaint.strokeWidth = IosDesign.dp(context, 0.95f)
            }
        }
        strokePaint.shader = null
    }

    private var showingDots: Boolean = false
    private val pillRect = RectF()
    private val highlightRect = RectF()
    private var lastShaderL = -1f
    private var lastShaderT = -1f
    private var lastShaderR = -1f
    private var lastShaderB = -1f

    private fun ensureLiquidStrokeShader() {
        if (glassThemeStyle != GlassThemeStyle.LIQUID_GLASS) {
            strokePaint.shader = null
            return
        }
        if (strokePaint.shader != null &&
            lastShaderL == pillRect.left && lastShaderT == pillRect.top &&
            lastShaderR == pillRect.right && lastShaderB == pillRect.bottom
        ) {
            return
        }
        lastShaderL = pillRect.left
        lastShaderT = pillRect.top
        lastShaderR = pillRect.right
        lastShaderB = pillRect.bottom
        val darkBias = iconThemeStyle != IconThemeStyle.LIGHT
        strokePaint.shader = LinearGradient(
            pillRect.left, pillRect.top, pillRect.right, pillRect.bottom,
            intArrayOf(
                if (darkBias) 0x8CFFFFFF.toInt() else 0xB8FFFFFF.toInt(),
                0x20FFFFFF,
                if (darkBias) 0x58FFFFFF else 0x82FFFFFF.toInt()
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x4AFFFFFF
        style = Paint.Style.FILL
    }
    private val topHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x24FFFFFF
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x3AFFFFFF
        style = Paint.Style.STROKE
        strokeWidth = IosDesign.dp(context, 0.8f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = IosDesign.sp(context, 12.2f)
        typeface = IosDesign.TYPEFACE_MEDIUM
        textAlign = Paint.Align.LEFT
    }
    private val activeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val inactiveDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x70FFFFFF
        style = Paint.Style.FILL
    }
    private val glassIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xF2FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = IosDesign.dp(context, 1.5f)
        strokeCap = Paint.Cap.ROUND
    }

    private val resetToSearchRunnable = Runnable {
        showingDots = false
        invalidate()
    }

    init {
        isClickable = true
        isFocusable = true
        applyThemeColors()
    }

    private fun computeSearchPillWidth(): Float {
        val iconSpan = IosDesign.dp(context, 11.5f)
        val iconTextGap = IosDesign.dp(context, 5.5f)
        val textW = textPaint.measureText(SEARCH_LABEL)
        val hPad = IosDesign.dp(context, 14f)
        return maxOf(IosDesign.dp(context, 88f), iconSpan + iconTextGap + textW + hPad * 2f)
    }

    private fun computeDotsPillWidth(): Float {
        val dotR = IosDesign.dp(context, 3.4f)
        val dotGap = IosDesign.dp(context, 12f)
        val count = totalHomePages.coerceIn(1, 10)
        val contentW = (count - 1) * dotGap + dotR * 2f
        return contentW + IosDesign.dp(context, 24f)
    }

    private fun computePillHeight(): Float {
        val fontH = textPaint.descent() - textPaint.ascent()
        return maxOf(IosDesign.dp(context, 28f), fontH + IosDesign.dp(context, 10f))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredW = (maxOf(computeSearchPillWidth(), computeDotsPillWidth()) + IosDesign.dp(context, 16f)).toInt()
        val desiredH = (computePillHeight() + IosDesign.dp(context, 6f)).toInt()
        setMeasuredDimension(
            resolveSize(desiredW, widthMeasureSpec),
            resolveSize(desiredH, heightMeasureSpec)
        )
    }

    fun showPageDotsTemporarily(activePage: Int) {
        currentHomePage = activePage
        showingDots = true
        invalidate()
        removeCallbacks(resetToSearchRunnable)
        if (showSearchLabel) {
            postDelayed(resetToSearchRunnable, 1500L)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(resetToSearchRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pillH = minOf(computePillHeight(), h - IosDesign.dp(context, 2f))
        val top = (h - pillH) / 2f
        val r = pillH / 2f

        if (!showingDots && showSearchLabel) {
            val iconR = IosDesign.dp(context, 4.1f)
            val iconSpan = iconR * 2.45f
            val iconTextGap = IosDesign.dp(context, 5.5f)
            val textW = textPaint.measureText(SEARCH_LABEL)
            val contentW = iconSpan + iconTextGap + textW
            val pillW = minOf(w - IosDesign.dp(context, 4f), maxOf(IosDesign.dp(context, 88f), contentW + IosDesign.dp(context, 28f)))
            val left = (w - pillW) / 2f

            pillRect.set(left, top, left + pillW, top + pillH)
            ensureLiquidStrokeShader()
            canvas.drawRoundRect(pillRect, r, r, bgPaint)
            highlightRect.set(
                pillRect.left + IosDesign.dp(context, 1f),
                pillRect.top + IosDesign.dp(context, 1f),
                pillRect.right - IosDesign.dp(context, 1f),
                pillRect.top + pillH * 0.46f
            )
            canvas.drawRoundRect(highlightRect, r, r, topHighlightPaint)
            canvas.drawRoundRect(pillRect, r, r, strokePaint)

            // Draw magnifying glass + "Search" mathematically centered with zero clipping
            val contentStart = left + (pillW - contentW) / 2f
            val iconCx = contentStart + iconR
            val iconCy = h / 2f - IosDesign.dp(context, 0.7f)
            canvas.drawCircle(iconCx, iconCy, iconR, glassIconPaint)
            canvas.drawLine(
                iconCx + iconR * 0.72f,
                iconCy + iconR * 0.72f,
                iconCx + iconR * 1.45f,
                iconCy + iconR * 1.45f,
                glassIconPaint
            )

            val textX = contentStart + iconSpan + iconTextGap
            val textBaseline = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(SEARCH_LABEL, textX, textBaseline, textPaint)
        } else {
            val dotR = IosDesign.dp(context, 3.4f)
            val dotGap = IosDesign.dp(context, 12f)
            val count = totalHomePages.coerceIn(1, 10)
            val contentW = (count - 1) * dotGap + dotR * 2f
            val pillW = minOf(w - IosDesign.dp(context, 4f), contentW + IosDesign.dp(context, 24f))
            val left = (w - pillW) / 2f

            pillRect.set(left, top, left + pillW, top + pillH)
            ensureLiquidStrokeShader()
            canvas.drawRoundRect(pillRect, r, r, bgPaint)
            canvas.drawRoundRect(pillRect, r, r, strokePaint)

            val startX = w / 2f - contentW / 2f + dotR
            for (i in 0 until count) {
                val cx = startX + i * dotGap
                val paint = if (i == currentHomePage) activeDotPaint else inactiveDotPaint
                canvas.drawCircle(cx, h / 2f, dotR, paint)
            }
        }
    }

    companion object {
        private const val SEARCH_LABEL = "Search"
    }
}
