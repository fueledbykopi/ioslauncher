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

    private val viewConfig = ViewConfiguration.get(context)
    private val touchSlop = viewConfig.scaledTouchSlop
    private val maxFlingVelocity = viewConfig.scaledMaximumFlingVelocity.toFloat()
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var currentPage: Int = 0
    private var currentScrollX: Float = 0f
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var lastX: Float = 0f
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var gestureIgnoredBySystemZone: Boolean = false
    private var dragInterruptedSpring: Boolean = false
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
    val hasStaleTouchState: Boolean
        get() = mode != DragMode.NONE || isDraggingItem || gestureIgnoredBySystemZone || activePointerId != MotionEvent.INVALID_POINTER_ID

    init {
        clipChildren = false
        clipToPadding = false
    }

    fun setPages(pages: List<View>, initialPage: Int = 0) {
        resetTouchState(snapToCurrentPage = false)
        removeAllViews()
        pages.forEach { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
        currentPage = initialPage.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        requestLayout()
        post {
            if (width > 0 && childCount > 0) {
                currentPage = currentPage.coerceIn(0, childCount - 1)
                spring.snapTo(currentPage * width.toFloat())
                onPageSettled?.invoke(currentPage)
            }
        }
    }

    /**
     * Inserts a page without removing/detaching existing pages (safe during active item drag).
     */
    fun insertPage(index: Int, page: View) {
        val clampedIndex = index.coerceIn(0, childCount)
        addView(page, clampedIndex, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (clampedIndex <= currentPage) {
            currentPage = (currentPage + 1).coerceIn(0, childCount - 1)
        }
        requestLayout()
    }

    /**
     * Clears any in-flight gesture, velocity tracker, long-press callback, or stale drag lock.
     * Safe to invoke on activity pause/resume or before rebuilding workspace pages.
     */
    fun resetTouchState(snapToCurrentPage: Boolean = true) {
        spring.cancel()
        cancelLongPressCheck()
        releaseVelocityTracker()
        mode = DragMode.NONE
        activePointerId = MotionEvent.INVALID_POINTER_ID
        gestureIgnoredBySystemZone = false
        dragInterruptedSpring = false
        isDraggingItem = false
        if (snapToCurrentPage && width > 0 && childCount > 0) {
            currentPage = currentPage.coerceIn(0, childCount - 1)
            val targetX = currentPage * width.toFloat()
            if (!currentScrollX.isFinite() || abs(currentScrollX - targetX) > 0.5f) {
                spring.snapTo(targetX)
            }
        }
    }

    fun scrollToPage(index: Int, animate: Boolean = true, initialVelocity: Float? = null) {
        if (childCount == 0 || width == 0) return
        val clamped = index.coerceIn(0, childCount - 1)
        currentPage = clamped
        val targetX = clamped * width.toFloat()
        if (!currentScrollX.isFinite()) {
            currentScrollX = targetX
        }
        if (animate) {
            if (!spring.isRunning) {
                spring.setCurrentValue(currentScrollX)
            }
            val safeVelocity = initialVelocity?.takeIf { it.isFinite() }
                ?.coerceIn(-maxFlingVelocity, maxFlingVelocity)
            spring.animateTo(targetX, safeVelocity)
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
                activePointerId = ev.getPointerId(0)
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                mode = DragMode.NONE
                dragInterruptedSpring = false

                // Respect OPPO ColorOS 15 side Back & bottom Home/Recents gesture zones
                val effectiveW = if (width > 0) width else resources.displayMetrics.widthPixels
                val effectiveH = if (height > 0) height else resources.displayMetrics.heightPixels
                gestureIgnoredBySystemZone = ColorOsCompat.isInSystemGestureZone(
                    x = ev.x,
                    y = ev.y,
                    viewWidth = effectiveW,
                    viewHeight = effectiveH,
                    insets = safeInsets,
                    enabled = colorOsSafeZoneEnabled
                )
                if (gestureIgnoredBySystemZone) return false

                if (spring.isRunning) {
                    spring.cancel()
                    dragInterruptedSpring = true
                }
                releaseVelocityTracker()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(ev) }
            }

            MotionEvent.ACTION_MOVE -> {
                if (gestureIgnoredBySystemZone) return false
                val pointerIdx = ev.findPointerIndex(activePointerId)
                if (pointerIdx < 0) return false
                velocityTracker?.addMovement(ev)

                val x = ev.getX(pointerIdx)
                val y = ev.getY(pointerIdx)
                val dx = x - downX
                val dy = y - downY
                val absDx = abs(dx)
                val absDy = abs(dy)

                if (childCount > 1 && absDx > touchSlop && absDx > absDy * 1.15f) {
                    mode = DragMode.HORIZONTAL
                    lastX = x
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

            MotionEvent.ACTION_POINTER_UP -> {
                onSecondaryPointerUp(ev)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = DragMode.NONE
                activePointerId = MotionEvent.INVALID_POINTER_ID
                releaseVelocityTracker()
                if (dragInterruptedSpring) {
                    dragInterruptedSpring = false
                    scrollToPage(currentPage, animate = true)
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isDraggingItem || gestureIgnoredBySystemZone) return false

        val action = event.actionMasked
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
            velocityTracker?.addMovement(event)
        } else if (action != MotionEvent.ACTION_DOWN) {
            // Avoid double-recording ACTION_DOWN already added in onInterceptTouchEvent
            velocityTracker?.addMovement(event)
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                lastX = event.x
                longPressPending = true
                postDelayed(longPressRunnable, longPressTimeout)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                var pointerIdx = event.findPointerIndex(activePointerId)
                if (pointerIdx < 0) {
                    activePointerId = event.getPointerId(0)
                    pointerIdx = 0
                    lastX = event.getX(0)
                }
                val x = event.getX(pointerIdx)
                val y = event.getY(pointerIdx)
                val dxTotal = x - downX
                val dyTotal = y - downY
                if (abs(dxTotal) > touchSlop || abs(dyTotal) > touchSlop) {
                    cancelLongPressCheck()
                }

                if (mode == DragMode.NONE) {
                    if (childCount > 1 && abs(dxTotal) > touchSlop && abs(dxTotal) > abs(dyTotal)) {
                        mode = DragMode.HORIZONTAL
                        lastX = x
                    } else if (swipeDownSpotlightEnabled &&
                        dyTotal > touchSlop * 1.4f &&
                        abs(dyTotal) > abs(dxTotal) * 1.3f &&
                        !canActivePageScrollUp()
                    ) {
                        mode = DragMode.SPOTLIGHT_DOWN
                    }
                }

                when (mode) {
                    DragMode.HORIZONTAL -> {
                        if (!currentScrollX.isFinite()) {
                            currentScrollX = currentPage * width.toFloat()
                        }
                        val delta = lastX - x
                        lastX = x
                        val maxScroll = ((childCount - 1).coerceAtLeast(0) * width).toFloat()
                        var next = currentScrollX + delta
                        // UIScrollView rubber-band resistance past first/last page
                        if (next < 0f) {
                            next = SpringMath.rubberBand(next, width.toFloat(), 0.50f)
                        } else if (next > maxScroll) {
                            next = maxScroll + SpringMath.rubberBand(next - maxScroll, width.toFloat(), 0.50f)
                        }
                        currentScrollX = if (next.isFinite()) next else currentPage * width.toFloat()
                        applyScrollTranslation()
                    }
                    DragMode.SPOTLIGHT_DOWN -> {
                        val pull = (y - downY).coerceAtLeast(0f)
                        onSwipeDownSpotlight?.invoke(pull, false)
                    }
                    DragMode.NONE -> Unit
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                onSecondaryPointerUp(event)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasLongPressPending = longPressPending
                cancelLongPressCheck()

                when (mode) {
                    DragMode.HORIZONTAL -> {
                        velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity)
                        val rawVx = if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                            velocityTracker?.getXVelocity(activePointerId) ?: 0f
                        } else {
                            velocityTracker?.xVelocity ?: 0f
                        }
                        val vx = if (rawVx.isFinite()) rawVx.coerceIn(-maxFlingVelocity, maxFlingVelocity) else 0f
                        val target = computeTargetPage(vx)
                        spring.setCurrentValue(currentScrollX)
                        scrollToPage(target, animate = true, initialVelocity = -vx)
                    }
                    DragMode.SPOTLIGHT_DOWN -> {
                        val pointerIdx = event.findPointerIndex(activePointerId).let { if (it >= 0) it else 0 }
                        val pull = (event.getY(pointerIdx) - downY).coerceAtLeast(0f)
                        val threshold = IosDesign.dp(context, 72f)
                        onSwipeDownSpotlight?.invoke(pull, action == MotionEvent.ACTION_UP && pull >= threshold)
                    }
                    DragMode.NONE -> {
                        if (dragInterruptedSpring) {
                            scrollToPage(currentPage, animate = true)
                        } else if (action == MotionEvent.ACTION_UP && wasLongPressPending) {
                            onEmptyTap?.invoke()
                        }
                    }
                }
                mode = DragMode.NONE
                activePointerId = MotionEvent.INVALID_POINTER_ID
                dragInterruptedSpring = false
                releaseVelocityTracker()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIdx = ev.actionIndex
        val pointerId = ev.getPointerId(pointerIdx)
        if (pointerId == activePointerId) {
            val newIdx = if (pointerIdx == 0) 1 else 0
            if (newIdx < ev.pointerCount) {
                activePointerId = ev.getPointerId(newIdx)
                lastX = ev.getX(newIdx)
                downX = ev.getX(newIdx)
                downY = ev.getY(newIdx)
            }
        }
    }

    private fun computeTargetPage(velocityX: Float): Int {
        if (width <= 0 || childCount <= 1) return 0
        val flingThreshold = IosDesign.dp(context, 380f)
        val safeScroll = if (currentScrollX.isFinite()) currentScrollX else currentPage * width.toFloat()
        val rawPage = safeScroll / width.toFloat()
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
        if (!currentScrollX.isFinite()) {
            currentScrollX = currentPage * width.toFloat()
        }
        scrollTo(currentScrollX.toInt(), 0)
        if (width > 0) {
            val w = width.toFloat()
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val dist = abs(currentScrollX - i * w) / w
                if (dist > 1.25f) {
                    // Hide far-offscreen pages to cut GPU overdraw & view traversal overhead
                    if (child.visibility != INVISIBLE) {
                        child.visibility = INVISIBLE
                    }
                    continue
                }
                if (child.visibility != VISIBLE) {
                    child.visibility = VISIBLE
                }
                val clamped = dist.coerceIn(0f, 1f)
                // Subtle iOS SpringBoard depth scale on gliding pages (zero allocations)
                val scale = 1f - clamped * 0.025f
                val targetAlpha = 1f - clamped * 0.14f
                if (child.scaleX != scale) {
                    child.scaleX = scale
                    child.scaleY = scale
                }
                if (child.alpha != targetAlpha) {
                    child.alpha = targetAlpha
                }
            }
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
