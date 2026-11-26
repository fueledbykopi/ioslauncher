package com.iospring.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import com.iospring.launcher.model.HomeItem

/**
 * Authentic iOS 18 Free-Placement Grid Page (default 4x6, supports 5x6).
 * Positions apps, folders, and widgets at exact (col, row, spanX, spanY) cells
 * while preserving intentional empty gaps.
 */
class IosHomeGridPage(
    context: Context,
    val pageIndex: Int,
    var gridCols: Int = 4,
    var gridRows: Int = 6
) : ViewGroup(context) {

    data class DropHighlight(
        val col: Int,
        val row: Int,
        val spanX: Int,
        val spanY: Int,
        val isFolderMerge: Boolean
    )

    private var highlight: DropHighlight? = null
    private val highlightRect = RectF()

    private val dropFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dropStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = IosDesign.dp(context, 2f)
    }

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
    }

    fun setDropHighlight(target: DropHighlight?) {
        if (highlight != target) {
            highlight = target
            invalidate()
        }
    }

    fun addHomeItemView(view: View, item: HomeItem) {
        view.tag = item
        addView(view, CellLayoutParams(item.col, item.row, item.spanX, item.spanY))
    }

    fun pixelToCell(x: Float, y: Float): Pair<Int, Int> {
        val availW = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        val availH = (height - paddingTop - paddingBottom).coerceAtLeast(1)
        val cellW = availW.toFloat() / gridCols
        val cellH = availH.toFloat() / gridRows
        val col = ((x - paddingLeft) / cellW).toInt().coerceIn(0, gridCols - 1)
        val row = ((y - paddingTop) / cellH).toInt().coerceIn(0, gridRows - 1)
        return Pair(col, row)
    }

    fun cellBounds(col: Int, row: Int, spanX: Int, spanY: Int, out: RectF) {
        val availW = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        val availH = (height - paddingTop - paddingBottom).coerceAtLeast(1)
        val cellW = availW.toFloat() / gridCols
        val cellH = availH.toFloat() / gridRows
        val l = paddingLeft + col * cellW
        val t = paddingTop + row * cellH
        out.set(l, t, l + spanX * cellW, t + spanY * cellH)
    }

    fun setJiggleMode(jiggle: Boolean) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            when (child) {
                is IosAppIconView -> child.setJiggling(jiggle)
                is IosWidgetContainerView -> child.setJiggling(jiggle)
                else -> {
                    child.animate()
                        .scaleX(if (jiggle) 0.96f else 1f)
                        .scaleY(if (jiggle) 0.96f else 1f)
                        .setDuration(160L)
                        .start()
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)

        val availW = (w - paddingLeft - paddingRight).coerceAtLeast(1)
        val availH = (h - paddingTop - paddingBottom).coerceAtLeast(1)
        val cellW = availW.toFloat() / gridCols
        val cellH = availH.toFloat() / gridRows

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as? CellLayoutParams ?: continue
            val cw = (lp.spanX * cellW).toInt().coerceAtLeast(1)
            val ch = (lp.spanY * cellH).toInt().coerceAtLeast(1)
            child.measure(
                MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val availW = (r - l - paddingLeft - paddingRight).coerceAtLeast(1)
        val availH = (b - t - paddingTop - paddingBottom).coerceAtLeast(1)
        val cellW = availW.toFloat() / gridCols
        val cellH = availH.toFloat() / gridRows

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as? CellLayoutParams ?: continue
            val left = (paddingLeft + lp.col * cellW).toInt()
            val top = (paddingTop + lp.row * cellH).toInt()
            val right = (left + lp.spanX * cellW).toInt()
            val bottom = (top + lp.spanY * cellH).toInt()
            child.layout(left, top, right, bottom)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val hl = highlight ?: return
        cellBounds(hl.col, hl.row, hl.spanX, hl.spanY, highlightRect)
        val inset = IosDesign.dp(context, 6f)
        highlightRect.inset(inset, inset)
        val radius = IosDesign.dp(context, if (hl.spanX > 1) 22f else 18f)

        if (hl.isFolderMerge) {
            dropFillPaint.color = 0x55FFFFFF
            dropStrokePaint.color = 0xDDFFFFFF.toInt()
        } else {
            dropFillPaint.color = 0x26FFFFFF
            dropStrokePaint.color = 0x80FFFFFF.toInt()
        }
        canvas.drawRoundRect(highlightRect, radius, radius, dropFillPaint)
        canvas.drawRoundRect(highlightRect, radius, radius, dropStrokePaint)
    }

    class CellLayoutParams(
        val col: Int,
        val row: Int,
        val spanX: Int = 1,
        val spanY: Int = 1
    ) : LayoutParams(MATCH_PARENT, MATCH_PARENT)
}
