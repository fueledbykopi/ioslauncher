package com.iospring.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.iospring.launcher.model.GlassThemeStyle

/**
 * Signature iOS 16-18 floating "Search" frosted capsule above the Dock.
 * Smoothly morphs into page indicator dots during horizontal scrolling, then
 * settles back into the tappable "🔍 Search" pill.
 */
class IosSearchPillView(context: Context) : View(context) {

    var totalHomePages: Int = 1
        set(value) {
            field = value.coerceAtLeast(1)
            invalidate()
        }

    var currentHomePage: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var showSearchLabel: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var glassThemeStyle: GlassThemeStyle = GlassThemeStyle.LIQUID_GLASS
        set(value) {
            if (field != value) {
                field = value
                applyThemeColors()
                invalidate()
            }
        }

    private fun applyThemeColors() {
        when (glassThemeStyle) {
            GlassThemeStyle.LIQUID_GLASS -> {
                bgPaint.color = 0x32FFFFFF
                strokePaint.color = 0x98FFFFFF.toInt()
                strokePaint.strokeWidth = IosDesign.dp(context, 1.05f)
            }
            GlassThemeStyle.FROSTED_BLUR -> {
                bgPaint.color = 0x54F2F2F8
                strokePaint.color = 0x54FFFFFF
                strokePaint.strokeWidth = IosDesign.dp(context, 0.85f)
            }
            GlassThemeStyle.OBSIDIAN_GLASS -> {
                bgPaint.color = 0x7A18181C
                strokePaint.color = 0x48FFFFFF
                strokePaint.strokeWidth = IosDesign.dp(context, 0.95f)
            }
        }
    }

    private var showingDots: Boolean = false
    private val pillRect = RectF()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x4AFFFFFF
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x3AFFFFFF
        style = Paint.Style.STROKE
        strokeWidth = IosDesign.dp(context, 0.8f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = IosDesign.sp(context, 12f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
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
        color = 0xEEFFFFFF.toInt()
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

        val pillH = IosDesign.dp(context, 28f)
        val top = (h - pillH) / 2f

        if (!showingDots && showSearchLabel) {
            val pillW = IosDesign.dp(context, 86f)
            val left = (w - pillW) / 2f
            pillRect.set(left, top, left + pillW, top + pillH)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, bgPaint)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, strokePaint)

            // Draw minimalist magnifying glass icon + "Search"
            val iconR = IosDesign.dp(context, 4.2f)
            val iconCx = left + IosDesign.dp(context, 19f)
            val iconCy = h / 2f - IosDesign.dp(context, 0.8f)
            canvas.drawCircle(iconCx, iconCy, iconR, glassIconPaint)
            canvas.drawLine(
                iconCx + iconR * 0.72f,
                iconCy + iconR * 0.72f,
                iconCx + iconR * 1.45f,
                iconCy + iconR * 1.45f,
                glassIconPaint
            )

            val textBaseline = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText("Search", left + IosDesign.dp(context, 52f), textBaseline, textPaint)
        } else {
            val dotR = IosDesign.dp(context, 3.4f)
            val dotGap = IosDesign.dp(context, 12f)
            val count = totalHomePages.coerceIn(1, 10)
            val contentW = (count - 1) * dotGap + dotR * 2f
            val pillW = contentW + IosDesign.dp(context, 24f)
            val left = (w - pillW) / 2f
            pillRect.set(left, top, left + pillW, top + pillH)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, bgPaint)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, strokePaint)

            val startX = w / 2f - contentW / 2f + dotR
            for (i in 0 until count) {
                val cx = startX + i * dotGap
                val paint = if (i == currentHomePage) activeDotPaint else inactiveDotPaint
                canvas.drawCircle(cx, h / 2f, dotR, paint)
            }
        }
    }
}
