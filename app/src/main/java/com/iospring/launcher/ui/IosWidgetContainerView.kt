package com.iospring.launcher.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.random.Random

/**
 * Unified iOS 18/26 Widget Container wrapping BOTH Built-in iOS Widgets and native Android AppWidgets.
 * - Clips inner widget content cleanly to Apple 22dp rounded corners.
 * - Reliably detects long-press even over clickable widget children / RemoteViews to open Remove Widget menu.
 * - Displays an interactive iOS top-left (-) Remove badge in Jiggle Mode and intercepts widget clicks while editing.
 * - Updates AppWidgetHostView size options automatically when resized/laid out.
 */
class IosWidgetContainerView(
    context: Context,
    val contentView: View
) : FrameLayout(context) {

    var onWidgetLongPress: (() -> Unit)? = null
    var onRemoveClick: (() -> Unit)? = null
    var onJiggleTouch: ((View, MotionEvent) -> Boolean)? = null

    private var isJiggling: Boolean = false
    private var jiggleAnimator: ObjectAnimator? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var downX: Float = 0f
    private var downY: Float = 0f
    private var longPressTriggered: Boolean = false
    private var longPressPending: Boolean = false

    private val longPressRunnable = Runnable {
        if (longPressPending && !isJiggling) {
            longPressPending = false
            longPressTriggered = true
            onWidgetLongPress?.invoke()
        }
    }

    private val cornerRadiusPx = IosDesign.dp(context, 22f)
    private val contentClipper = FrameLayout(context).apply {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
        clipToOutline = true
    }

    private val removeBadge = RemoveBadgeView(context).apply {
        visibility = GONE
        contentDescription = "Remove Widget"
        setOnClickListener {
            onRemoveClick?.invoke()
        }
    }

    init {
        clipChildren = false
        clipToPadding = false
        val pad = IosDesign.dpInt(context, 6f)
        setPadding(pad, pad, pad, pad)

        contentClipper.addView(
            contentView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(contentClipper, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val badgeSize = IosDesign.dpInt(context, 28f)
        val badgeLp = LayoutParams(badgeSize, badgeSize, Gravity.TOP or Gravity.START).apply {
            leftMargin = -IosDesign.dpInt(context, 4f)
            topMargin = -IosDesign.dpInt(context, 4f)
        }
        addView(removeBadge, badgeLp)
    }

    fun setJiggling(jiggle: Boolean) {
        if (isJiggling == jiggle) return
        isJiggling = jiggle
        removeBadge.visibility = if (jiggle) VISIBLE else GONE
        jiggleAnimator?.cancel()
        jiggleAnimator = null
        if (jiggle) {
            val amp = 1.05f + Random.nextFloat() * 0.35f
            val dir = if (Random.nextBoolean()) 1f else -1f
            jiggleAnimator = ObjectAnimator.ofFloat(this, ROTATION, -amp * dir, amp * dir).apply {
                duration = 145L + Random.nextLong(25L)
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                startDelay = Random.nextLong(80L)
                start()
            }
        } else {
            animate().rotation(0f).scaleX(1f).scaleY(1f).setDuration(120L).start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (contentView is AppWidgetHostView && w > 0 && h > 0) {
            val density = resources.displayMetrics.density.coerceAtLeast(1f)
            val pad = paddingLeft + paddingRight
            val dpW = ((w - pad) / density).toInt().coerceAtLeast(40)
            val dpH = ((h - pad) / density).toInt().coerceAtLeast(40)
            runCatching {
                val opts = Bundle().apply {
                    putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, dpW)
                    putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, dpW)
                    putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, dpH)
                    putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, dpH)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    contentView.updateAppWidgetSize(opts, listOf(android.util.SizeF(dpW.toFloat(), dpH.toFloat())))
                } else {
                    @Suppress("DEPRECATION")
                    contentView.updateAppWidgetSize(opts, dpW, dpH, dpW, dpH)
                }
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // In Jiggle Mode: allow tapping the (-) remove badge directly, intercept everything else for dragging
        if (isJiggling) {
            if (isTouchOnRemoveBadge(ev)) {
                return false
            }
            return true
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                longPressTriggered = false
                longPressPending = true
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, longPressTimeout)
            }
            MotionEvent.ACTION_MOVE -> {
                if (longPressPending && (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop)) {
                    cancelLongPressCheck()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLongPressCheck()
                if (longPressTriggered) {
                    longPressTriggered = false
                    return true
                }
            }
        }
        return longPressTriggered
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isJiggling) {
            return onJiggleTouch?.invoke(this, event) == true || true
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            cancelLongPressCheck()
            longPressTriggered = false
        }
        return true
    }

    private fun isTouchOnRemoveBadge(ev: MotionEvent): Boolean {
        if (removeBadge.visibility != VISIBLE) return false
        val hitPad = IosDesign.dp(context, 10f)
        return ev.x >= removeBadge.left - hitPad &&
            ev.x <= removeBadge.right + hitPad &&
            ev.y >= removeBadge.top - hitPad &&
            ev.y <= removeBadge.bottom + hitPad
    }

    private fun cancelLongPressCheck() {
        longPressPending = false
        removeCallbacks(longPressRunnable)
    }

    override fun onDetachedFromWindow() {
        cancelLongPressCheck()
        jiggleAnimator?.cancel()
        jiggleAnimator = null
        super.onDetachedFromWindow()
    }

    private class RemoveBadgeView(context: Context) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE6E5E5EA.toInt()
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44000000
            style = Paint.Style.STROKE
            strokeWidth = IosDesign.dp(context, 0.8f)
        }
        private val minusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1C1C1E.toInt()
            strokeWidth = IosDesign.dp(context, 2.2f)
            strokeCap = Paint.Cap.ROUND
        }

        init {
            isClickable = true
            isFocusable = true
            elevation = IosDesign.dp(context, 4f)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) * 0.42f
            if (r <= 0f) return
            canvas.drawCircle(cx, cy, r, bgPaint)
            canvas.drawCircle(cx, cy, r, strokePaint)
            val halfBar = r * 0.48f
            canvas.drawLine(cx - halfBar, cy, cx + halfBar, cy, minusPaint)
        }
    }
}
