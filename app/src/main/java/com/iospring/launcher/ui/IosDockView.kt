package com.iospring.launcher.ui

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.iospring.launcher.core.IconCacheManager
import com.iospring.launcher.model.DockSlot
import com.iospring.launcher.model.GlassThemeStyle
import com.iospring.launcher.model.IconThemeStyle
import com.iospring.launcher.service.BadgeStore

/**
 * Floating iOS 18 Frosted Glass Dock (up to 4 pinned apps).
 * Designed to sit cleanly above OPPO Android 15 (ColorOS 15) bottom gesture bar.
 */
class IosDockView(context: Context) : FrameLayout(context) {

    private val backdropLayer = View(context)
    private val iconsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    var onDockAppClick: ((DockSlot, View) -> Unit)? = null
    var onDockAppLongClick: ((DockSlot, View) -> Unit)? = null
    var onDockAppRemove: ((DockSlot) -> Unit)? = null

    private var currentSlots: List<DockSlot> = emptyList()
    private var jiggling: Boolean = false
    private var showBadges: Boolean = true

    init {
        backdropLayer.background = IosDesign.createThemeSurface(
            context = context,
            cornerRadiusDp = 32f,
            glassStyle = GlassThemeStyle.LIQUID_GLASS,
            iconStyle = IconThemeStyle.LIGHT
        )
        addView(backdropLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val padH = IosDesign.dpInt(context, 10f)
        val padV = IosDesign.dpInt(context, 8f)
        iconsRow.setPadding(padH, padV, padH, padV)
        addView(iconsRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun applyGlassStyle(
        style: IconThemeStyle,
        blurEnabled: Boolean,
        glassStyle: GlassThemeStyle = GlassThemeStyle.LIQUID_GLASS
    ) {
        backdropLayer.background = IosDesign.createThemeSurface(
            context = context,
            cornerRadiusDp = 32f,
            glassStyle = glassStyle,
            iconStyle = style
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = IosDesign.blurRadiusFor(glassStyle, strong = false)
            backdropLayer.setRenderEffect(
                if (blurEnabled) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null
            )
        }
    }

    fun bind(
        slots: List<DockSlot>,
        iconCache: IconCacheManager,
        showBadges: Boolean,
        largeIcons: Boolean,
        jiggle: Boolean
    ) {
        this.currentSlots = slots.take(4)
        this.jiggling = jiggle
        this.showBadges = showBadges
        iconsRow.removeAllViews()

        for (slot in currentSlots) {
            val iconView = IosAppIconView(context).apply {
                itemId = "dock_${slot.index}"
                packageNameRef = slot.packageName
                classNameRef = slot.className
                label = slot.label
                showLabel = false // iOS Dock icons never show labels
                largeMode = largeIcons
                iconBitmap = iconCache.getIcon(slot.packageName, slot.className, slot.label)
                badgeCount = if (showBadges) BadgeStore.countForPackage(slot.packageName) else 0
                setJiggling(jiggle)
                setOnClickListener {
                    if (!jiggling) onDockAppClick?.invoke(slot, this)
                }
                setOnLongClickListener {
                    onDockAppLongClick?.invoke(slot, this)
                    true
                }
                onRemoveBadgeClick = {
                    onDockAppRemove?.invoke(slot)
                }
            }
            val lp = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            iconsRow.addView(iconView, lp)
        }
    }

    fun refreshBadges(showBadges: Boolean) {
        this.showBadges = showBadges
        for (i in 0 until iconsRow.childCount) {
            val child = iconsRow.getChildAt(i) as? IosAppIconView ?: continue
            child.badgeCount = if (showBadges) BadgeStore.countForPackage(child.packageNameRef) else 0
        }
    }

    fun setJiggleMode(jiggle: Boolean) {
        jiggling = jiggle
        for (i in 0 until iconsRow.childCount) {
            (iconsRow.getChildAt(i) as? IosAppIconView)?.setJiggling(jiggle)
        }
    }
}
