package com.iospring.launcher.ui

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import com.iospring.launcher.core.ChoreographerSpring
import com.iospring.launcher.core.ColorOsCompat
import com.iospring.launcher.core.GestureSafeInsets
import com.iospring.launcher.core.SpringConfig
import com.iospring.launcher.core.SpringMath
import kotlin.math.abs

/**
 * 120Hz iOS Spring Horizontal Workspace Pager with OPPO ColorOS 15 gesture protection.
 *
 * Key OPPO Android 15 safeguards:
 * 1. Never intercepts touches originating inside left/right Back gesture edges or
 *    bottom Home/Recents swipe-up safe zone.
 * 2. Detects downward swipe in mid-screen to smoothly open Spotlight Search.
 * 3. Reports normalized scroll progress every frame for native wallpaper parallax.
 */
class SpringWorkspacePager(context: Context) : ViewGroup(context) {

    var safeInsets: GestureSafeInsets = GestureSafeInsets()
    var colorOsSafeZoneEnabled: Boolean = true
    var swipeDownSpotlightEnabled: Boolean = true
    var isDraggingItem: Boolean = false

    var onPageSettled: ((pageIndex: Int) -> Unit)? = null
    var onScrollProgress: ((scrollPx: Float, pageWidth: Int, pageCount: Int) -> Unit)? = null
    var onSwipeDownSpotlight: ((pullDistancePx: Float, committed: Boolean) -> Unit)? = null
    var onEmptyLongPress: (() -> Unit)? = null
    var onEmptyTap: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var currentPage: Int = 0
    private var currentScrollX: Float = 0f
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var lastX: Float = 0f
    private var gestureIgnoredBySystemZone: Boolean = false
    private var mode: DragMode = DragMode.NONE
    private var velocityTracker: VelocityTracker? = null
    private var longPressPending: Boolean = false

    private enum class DragMode { NONE, HORIZONTAL, SPOTLIGHT_DOWN }

    private val longPressRunnable = Runnable {
        if (longPressPending && mode == DragMode.NONE && !isDraggingItem) {
            longPressPending = false
            onEmptyLongPress?.invoke()
        }
    }

    private val spring = ChoreographerSpring(
        config = SpringConfig.PAGE_SNAP,
        positionEpsilon = 0.75f,
        velocityEpsilon = 2.0f,
        onUpdate = { value ->
            currentScrollX = value
            applyScrollTranslation()
        },
        onEnd = {
            onPageSettled?.invoke(currentPage)
        }
    )

    val pageCount: Int get() = childCount
    val activePageIndex: Int get() = currentPage

    init {
        clipChildren = false
        clipToPadding = false
    }

    fun setPages(pages: List<View>, initialPage: Int = 0) {
        spring.cancel()
        removeAllViews()
        pages.forEach { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
        currentPage = initialPage.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        requestLayout()
        post {
            if (width > 0) {
                spring.snapTo(currentPage * width.toFloat())
                onPageSettled?.invoke(currentPage)
            }
        }
    }

    fun scrollToPage(index: Int, animate: Boolean = true, initialVelocity: Float? = null) {
        if (childCount == 0 || width == 0) return
        val clamped = index.coerceIn(0, childCount - 1)
        currentPage = clamped
        val targetX = clamped * width.toFloat()
        if (animate) {
            spring.animateTo(targetX, initialVelocity)
        } else {
            spring.snapTo(targetX)
            onPageSettled?.invoke(currentPage)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val cw = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val ch = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            getChildAt(i).measure(cw, ch)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        for (i in 0 until childCount) {
            getChildAt(i).layout(i * w, 0, (i + 1) * w, h)
        }
        if (w > 0 && !spring.isRunning && mode == DragMode.NONE) {
            currentScrollX = currentPage * w.toFloat()
            applyScrollTranslation()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isDraggingItem) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                mode = DragMode.NONE

                // Respect OPPO ColorOS 15 side Back & bottom Home/Recents gesture zones
                gestureIgnoredBySystemZone = ColorOsCompat.isInSystemGestureZone(
                    x = ev.rawX,
                    y = ev.rawY,
                    viewWidth = resources.displayMetrics.widthPixels,
                    viewHeight = resources.displayMetrics.heightPixels,
                    insets = safeInsets,
                    enabled = colorOsSafeZoneEnabled
                )
                if (gestureIgnoredBySystemZone) return false

                if (spring.isRunning) {
                    spring.cancel()
                }
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(ev) }
            }

            MotionEvent.ACTION_MOVE -> {
                if (gestureIgnoredBySystemZone) return false
                val dx = ev.x - downX
                val dy = ev.y - downY
                val absDx = abs(dx)
                val absDy = abs(dy)

                if (absDx > touchSlop && absDx > absDy * 1.15f) {
                    mode = DragMode.HORIZONTAL
                    lastX = ev.x
                    cancelLongPressCheck()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                // Swipe down in middle of screen -> Spotlight Search
                // Never trigger swipe-up (let ColorOS handle bottom bar / don't hijack vertical scroll inside App Library/Today View)
                if (swipeDownSpotlightEnabled &&
                    dy > touchSlop * 1.4f &&
                    absDy > absDx * 1.35f &&
                    !canActivePageScrollUp()
                ) {
                    mode = DragMode.SPOTLIGHT_DOWN
                    cancelLongPressCheck()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = DragMode.NONE
                releaseVelocityTracker()
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isDraggingItem || gestureIgnoredBySystemZone) return false

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                longPressPending = true
                postDelayed(longPressRunnable, longPressTimeout)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dxTotal = event.x - downX
                val dyTotal = event.y - downY
                if (abs(dxTotal) > touchSlop || abs(dyTotal) > touchSlop) {
                    cancelLongPressCheck()
                }

                if (mode == DragMode.NONE) {
                    if (abs(dxTotal) > touchSlop && abs(dxTotal) > abs(dyTotal)) {
                        mode = DragMode.HORIZONTAL
                        lastX = event.x
                    } else if (swipeDownSpotlightEnabled && dyTotal > touchSlop * 1.4f && abs(dyTotal) > abs(dxTotal) * 1.3f) {
                        mode = DragMode.SPOTLIGHT_DOWN
                    }
                }

                when (mode) {
                    DragMode.HORIZONTAL -> {
                        val delta = lastX - event.x
                        lastX = event.x
                        val maxScroll = ((childCount - 1).coerceAtLeast(0) * width).toFloat()
                        var next = currentScrollX + delta
                        // UIScrollView rubber-band resistance past first/last page
                        if (next < 0f) {
                            next = SpringMath.rubberBand(next, width.toFloat(), 0.50f)
                        } else if (next > maxScroll) {
                            next = maxScroll + SpringMath.rubberBand(next - maxScroll, width.toFloat(), 0.50f)
                        }
                        currentScrollX = next
                        applyScrollTranslation()
                    }
                    DragMode.SPOTLIGHT_DOWN -> {
                        val pull = (event.y - downY).coerceAtLeast(0f)
                        onSwipeDownSpotlight?.invoke(pull, false)
                    }
                    DragMode.NONE -> Unit
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasLongPressPending = longPressPending
                cancelLongPressCheck()

                when (mode) {
                    DragMode.HORIZONTAL -> {
                        velocityTracker?.computeCurrentVelocity(1000)
                        val vx = velocityTracker?.xVelocity ?: 0f
                        val target = computeTargetPage(vx)
                        scrollToPage(target, animate = true, initialVelocity = -vx)
                    }
                    DragMode.SPOTLIGHT_DOWN -> {
                        val pull = (event.y - downY).coerceAtLeast(0f)
                        val threshold = IosDesign.dp(context, 72f)
                        onSwipeDownSpotlight?.invoke(pull, pull >= threshold)
                    }
                    DragMode.NONE -> {
                        if (event.actionMasked == MotionEvent.ACTION_UP && wasLongPressPending) {
                            onEmptyTap?.invoke()
                        }
                    }
                }
                mode = DragMode.NONE
                releaseVelocityTracker()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun computeTargetPage(velocityX: Float): Int {
        if (width <= 0 || childCount <= 1) return 0
        val flingThreshold = IosDesign.dp(context, 380f)
        val rawPage = currentScrollX / width.toFloat()
        return when {
            velocityX < -flingThreshold -> (currentPage + 1).coerceAtMost(childCount - 1)
            velocityX > flingThreshold -> (currentPage - 1).coerceAtLeast(0)
            else -> kotlin.math.round(rawPage).toInt().coerceIn(0, childCount - 1)
        }
    }

    private fun canActivePageScrollUp(): Boolean {
        val child = getChildAt(currentPage) ?: return false
        return child.canScrollVertically(-1)
    }

    private fun applyScrollTranslation() {
        scrollTo(currentScrollX.toInt(), 0)
        if (width > 0) {
            onScrollProgress?.invoke(currentScrollX, width, childCount)
        }
    }

    private fun cancelLongPressCheck() {
        longPressPending = false
        removeCallbacks(longPressRunnable)
    }

    private fun releaseVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun onDetachedFromWindow() {
        spring.cancel()
        cancelLongPressCheck()
        releaseVelocityTracker()
        super.onDetachedFromWindow()
    }
}
