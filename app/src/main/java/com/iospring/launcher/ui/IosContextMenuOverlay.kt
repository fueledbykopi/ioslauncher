package com.iospring.launcher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.iospring.launcher.model.GlassThemeStyle
import com.iospring.launcher.model.IconThemeStyle

/**
 * Authentic iOS 18 Haptic Touch Context Menu overlay.
 */
class IosContextMenuOverlay(context: Context) : FrameLayout(context) {

    data class MenuAction(
        val title: String,
        val iconGlyph: String,
        val isDestructive: Boolean = false,
        val onSelect: () -> Unit
    )

    var onDismiss: (() -> Unit)? = null

    private var isDismissing: Boolean = false
    private var animToken: Int = 0

    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = IosDesign.createThemeSurface(
            context = context,
            cornerRadiusDp = 22f,
            glassStyle = GlassThemeStyle.LIQUID_GLASS,
            iconStyle = IconThemeStyle.DARK,
            strongSurface = true
        )
    }

    fun applyGlassTheme(glassStyle: GlassThemeStyle, iconStyle: IconThemeStyle) {
        val effectiveIcon = if (glassStyle == GlassThemeStyle.LIQUID_GLASS) IconThemeStyle.DARK else iconStyle
        card.background = IosDesign.createThemeSurface(
            context = context,
            cornerRadiusDp = 22f,
            glassStyle = glassStyle,
            iconStyle = effectiveIcon,
            strongSurface = true
        )
    }

    init {
        visibility = GONE
        setBackgroundColor(0x5A000000)
        isClickable = true
        isFocusable = true
        setOnClickListener { dismiss() }
        addView(card, LayoutParams(IosDesign.dpInt(context, 248f), LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    fun show(headerTitle: String, actions: List<MenuAction>) {
        card.removeAllViews()

        val header = TextView(context).apply {
            text = headerTitle
            setTextColor(0x99FFFFFF.toInt())
            textSize = 12.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            val hp = IosDesign.dpInt(context, 16f)
            setPadding(hp, IosDesign.dpInt(context, 12f), hp, IosDesign.dpInt(context, 10f))
        }
        card.addView(header)

        actions.forEachIndexed { idx, action ->
            if (idx >= 0) {
                card.addView(View(context).apply {
                    setBackgroundColor(0x22FFFFFF)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val hp = IosDesign.dpInt(context, 16f)
                val vp = IosDesign.dpInt(context, 13f)
                setPadding(hp, vp, hp, vp)
                setOnClickListener {
                    dismiss(immediate = true)
                    action.onSelect()
                }
            }
            row.addView(TextView(context).apply {
                text = action.title
                setTextColor(if (action.isDestructive) IosDesign.IOS_RED else Color.WHITE)
                textSize = 15.5f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            row.addView(TextView(context).apply {
                text = action.iconGlyph
                setTextColor(if (action.isDestructive) IosDesign.IOS_RED else 0xDDFFFFFF.toInt())
                textSize = 15f
            })
            card.addView(row)
        }

        animToken++
        isDismissing = false
        animate().setListener(null).cancel()
        card.animate().cancel()

        visibility = VISIBLE
        alpha = 0f
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        animate().alpha(1f).setDuration(150L).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(190L).start()
    }

    fun dismiss(immediate: Boolean = false) {
        if (visibility != VISIBLE) return
        val token = ++animToken
        isDismissing = true
        animate().setListener(null).cancel()
        card.animate().cancel()

        if (immediate) {
            finalizeDismiss()
            return
        }

        animate()
            .alpha(0f)
            .setDuration(130L)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animToken == token && isDismissing) {
                        finalizeDismiss()
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    if (animToken == token && isDismissing) {
                        finalizeDismiss()
                    }
                }
            })
            .start()
        card.animate().scaleX(0.88f).scaleY(0.88f).setDuration(130L).start()
    }

    fun finalizePendingDismiss() {
        if (isDismissing || (visibility == VISIBLE && alpha < 0.05f)) {
            dismiss(immediate = true)
        }
    }

    private fun finalizeDismiss() {
        animate().setListener(null)
        isDismissing = false
        visibility = GONE
        alpha = 1f
        card.scaleX = 1f
        card.scaleY = 1f
        onDismiss?.invoke()
    }

    override fun onDetachedFromWindow() {
        animate().setListener(null).cancel()
        card.animate().cancel()
        super.onDetachedFromWindow()
    }

    val isOpen: Boolean get() = visibility == VISIBLE && !isDismissing
}
