package com.iospring.launcher.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import com.iospring.launcher.core.SquircleMath
import kotlin.random.Random

/**
 * Single-View, zero-nesting iOS 18 App Icon & Folder Icon renderer.
 * Draws the squircle icon (or 3x3 frosted folder preview), crisp shadowed label,
 * red iOS notification badge, and jiggle-mode (-) badge in one GPU draw pass.
 */
class IosAppIconView(context: Context) : View(context) {

    var itemId: String = ""
    var packageNameRef: String = ""
    var classNameRef: String = ""
    var label: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var iconBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    var isFolder: Boolean = false
    var folderIcons: List<Bitmap> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var showLabel: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var largeMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var badgeCount: Int = 0
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var darkGlassFolder: Boolean = false

    var onRemoveBadgeClick: (() -> Unit)? = null

    private var jiggleAnimator: ObjectAnimator? = null
    private var isJiggling: Boolean = false

    private val iconRect = RectF()
    private val removeHitRect = RectF()
    private var folderClipPath: Path? = null
    private var lastFolderSize: Float = -1f

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val iconShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x26000000
        style = Paint.Style.FILL
    }
    private val shadowRect = RectF()
    private val releaseInterpolator = OvershootInterpolator(1.15f)

    private val folderBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val folderBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = IosDesign.dp(context, 1.0f)
        color = 0x52FFFFFF
    }

    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = IosDesign.sp(context, 11.8f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        setShadowLayer(IosDesign.dp(context, 3.0f), 0f, IosDesign.dp(context, 1.2f), 0xC0000000.toInt())
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = IosDesign.IOS_RED
        style = Paint.Style.FILL
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = IosDesign.sp(context, 10.5f)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val removeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCDEDEE4.toInt()
        style = Paint.Style.FILL
    }
    private val removeMinusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1C1C1E.toInt()
        strokeWidth = IosDesign.dp(context, 2.1f)
        strokeCap = Paint.Cap.ROUND
    }

    init {
        isClickable = true
        isFocusable = true
    }

    fun setJiggling(jiggle: Boolean) {
        if (isJiggling == jiggle) return
        isJiggling = jiggle
        jiggleAnimator?.cancel()
        jiggleAnimator = null
        if (jiggle) {
            val amp = 1.9f + Random.nextFloat() * 0.7f
            val dir = if (Random.nextBoolean()) 1f else -1f
            jiggleAnimator = ObjectAnimator.ofFloat(this, ROTATION, -amp * dir, amp * dir).apply {
                duration = 125L + Random.nextLong(28L)
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                startDelay = Random.nextLong(90L)
                start()
            }
        } else {
            animate().rotation(0f).setDuration(110L).start()
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        jiggleAnimator?.cancel()
        jiggleAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isJiggling && event.action == MotionEvent.ACTION_UP) {
            if (removeHitRect.contains(event.x, event.y) && onRemoveBadgeClick != null) {
                onRemoveBadgeClick?.invoke()
                return true
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().scaleX(0.88f).scaleY(0.88f).alpha(0.86f).setDuration(90L).setInterpolator(null).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(190L).setInterpolator(releaseInterpolator).start()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val showText = showLabel
        val labelSpace = if (showText) IosDesign.dp(context, 18f) else 0f
        val maxIconSize = minOf(
            w - IosDesign.dp(context, if (largeMode) 10f else 15f),
            h - labelSpace - IosDesign.dp(context, 8f)
        ).coerceAtLeast(IosDesign.dp(context, 36f))

        val capDp = if (largeMode) 68f else 60f
        val iconSize = minOf(maxIconSize, IosDesign.dp(context, capDp))
        val left = (w - iconSize) / 2f
        val top = ((h - labelSpace) - iconSize) / 2f
        iconRect.set(left, top, left + iconSize, top + iconSize)

        // Subtle iOS ambient elevation shadow under every squircle icon
        val shadowOff = IosDesign.dp(context, 2.2f)
        val shadowCorner = iconSize * 0.23f
        shadowRect.set(
            iconRect.left + shadowOff * 0.35f,
            iconRect.top + shadowOff,
            iconRect.right - shadowOff * 0.35f,
            iconRect.bottom + shadowOff
        )
        canvas.drawRoundRect(shadowRect, shadowCorner, shadowCorner, iconShadowPaint)

        if (isFolder) {
            drawFolderIcon(canvas, iconRect)
        } else {
            iconBitmap?.let { bmp ->
                canvas.drawBitmap(bmp, null, iconRect, bitmapPaint)
            }
        }

        // Red iOS Notification Badge (hidden while jiggling, like real iOS)
        if (badgeCount > 0 && !isJiggling) {
            drawNotificationBadge(canvas, iconRect, badgeCount)
        }

        // Jiggle Mode (-) Remove Badge at top-left
        if (isJiggling) {
            drawRemoveBadge(canvas, iconRect)
        }

        // Crisp iOS App Label below icon
        if (showText && label.isNotEmpty()) {
            val maxTextW = (w - IosDesign.dp(context, 4f)).coerceAtLeast(24f)
            val ellipsized = TextUtils.ellipsize(label, labelPaint, maxTextW, TextUtils.TruncateAt.END)
            val textY = iconRect.bottom + IosDesign.dp(context, 14f)
            canvas.drawText(ellipsized, 0, ellipsized.length, w / 2f, textY, labelPaint)
        }
    }

    private fun drawFolderIcon(canvas: Canvas, rect: RectF) {
        if (lastFolderSize != rect.width() || folderClipPath == null) {
            lastFolderSize = rect.width()
            folderClipPath = SquircleMath.createSquirclePath(RectF(0f, 0f, rect.width(), rect.height()))
        }
        val save = canvas.save()
        canvas.translate(rect.left, rect.top)
        folderBgPaint.color = if (darkGlassFolder) 0x802C2C2E.toInt() else 0x58F2F2F7
        folderClipPath?.let { path ->
            canvas.drawPath(path, folderBgPaint)
            canvas.save()
            canvas.clipPath(path)

            // 3x3 mini app grid inside folder
            val pad = rect.width() * 0.13f
            val gap = rect.width() * 0.06f
            val cell = (rect.width() - pad * 2f - gap * 2f) / 3f
            val miniRect = RectF()
            val count = minOf(folderIcons.size, 9)
            for (i in 0 until count) {
                val r = i / 3
                val c = i % 3
                val mx = pad + c * (cell + gap)
                val my = pad + r * (cell + gap)
                miniRect.set(mx, my, mx + cell, my + cell)
                canvas.drawBitmap(folderIcons[i], null, miniRect, bitmapPaint)
            }
            canvas.restore()
            canvas.drawPath(path, folderBorderPaint)
        }
        canvas.restoreToCount(save)
    }

    private fun drawNotificationBadge(canvas: Canvas, rect: RectF, count: Int) {
        val text = if (count > 99) "99+" else count.toString()
        val r = IosDesign.dp(context, 10f)
        val textW = badgeTextPaint.measureText(text)
        val pillW = maxOf(r * 2f, textW + IosDesign.dp(context, 10f))
        val cx = rect.right - IosDesign.dp(context, 2f)
        val cy = rect.top + IosDesign.dp(context, 2f)
        val pillRect = RectF(cx - pillW / 2f, cy - r, cx + pillW / 2f, cy + r)
        canvas.drawRoundRect(pillRect, r, r, badgeBgPaint)
        val baseline = cy - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
        canvas.drawText(text, cx, baseline, badgeTextPaint)
    }

    private fun drawRemoveBadge(canvas: Canvas, rect: RectF) {
        val r = IosDesign.dp(context, 10.5f)
        val cx = rect.left + IosDesign.dp(context, 2f)
        val cy = rect.top + IosDesign.dp(context, 2f)
        canvas.drawCircle(cx, cy, r, removeBgPaint)
        val halfBar = r * 0.48f
        canvas.drawLine(cx - halfBar, cy, cx + halfBar, cy, removeMinusPaint)

        // Generous tap hitbox around the (-) circle
        val hitPad = IosDesign.dp(context, 8f)
        removeHitRect.set(cx - r - hitPad, cy - r - hitPad, cx + r + hitPad, cy + r + hitPad)
    }
}
