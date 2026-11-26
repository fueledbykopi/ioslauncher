package com.iospring.launcher.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.iospring.launcher.model.GlassThemeStyle
import com.iospring.launcher.model.IconThemeStyle
import com.iospring.launcher.widget.IosBatteryWidgetView
import com.iospring.launcher.widget.IosCalendarWidgetView
import com.iospring.launcher.widget.IosClockWidgetView
import com.iospring.launcher.widget.IosQuickStatusWidgetView
import com.iospring.launcher.widget.RefreshableIosWidget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Leftmost iOS 18+ Today View (Widget Page).
 * Swipe right from Home Page 0 to view glanceable iOS widgets and quick settings.
 */
class IosTodayViewPage(
    context: Context,
    glassStyle: GlassThemeStyle = GlassThemeStyle.LIQUID_GLASS,
    iconStyle: IconThemeStyle = IconThemeStyle.DARK
) : ScrollView(context) {

    var onOpenSettings: (() -> Unit)? = null
    var onAddWidget: (() -> Unit)? = null

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private val headerDate = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 26f
        typeface = IosDesign.TYPEFACE_MEDIUM_BOLD
        setShadowLayer(6f, 0f, 2f, 0x88000000.toInt())
    }

    private val headerDateFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
    private val headerDateBuf = Date()
    private val refreshables = mutableListOf<RefreshableIosWidget>()
    private lateinit var editBtn: TextView
    private lateinit var settingsBtn: TextView

    init {
        isVerticalScrollBarEnabled = false
        val padH = IosDesign.dpInt(context, 18f)
        column.setPadding(padH, IosDesign.dpInt(context, 56f), padH, IosDesign.dpInt(context, 100f))

        column.addView(headerDate, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = IosDesign.dpInt(context, 16f)
        })

        // Top 2-column row: Clock + Calendar
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val widgetSize = IosDesign.dpInt(context, 162f)
        val isDark = iconStyle != IconThemeStyle.LIGHT || glassStyle == GlassThemeStyle.OBSIDIAN_GLASS
        val clockWidget = IosClockWidgetView(context, preferDark = isDark, glassStyle = glassStyle).also { refreshables.add(it) }
        val calWidget = IosCalendarWidgetView(context, isDark = isDark, glassStyle = glassStyle, iconStyle = iconStyle).also { refreshables.add(it) }

        topRow.addView(clockWidget, LinearLayout.LayoutParams(0, widgetSize, 1f).apply {
            marginEnd = IosDesign.dpInt(context, 7f)
        })
        topRow.addView(calWidget, LinearLayout.LayoutParams(0, widgetSize, 1f).apply {
            marginStart = IosDesign.dpInt(context, 7f)
        })
        column.addView(topRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = IosDesign.dpInt(context, 14f)
        })

        // Device Summary 4x2
        val statusWidget = IosQuickStatusWidgetView(context, glassStyle = glassStyle, iconStyle = iconStyle).also { refreshables.add(it) }
        column.addView(statusWidget, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, IosDesign.dpInt(context, 118f)
        ).apply {
            bottomMargin = IosDesign.dpInt(context, 14f)
        })

        // Batteries (Phone + Bluetooth)
        val batteryWidget = IosBatteryWidgetView(context, glassStyle = glassStyle, iconStyle = iconStyle).also { refreshables.add(it) }
        column.addView(batteryWidget, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, IosDesign.dpInt(context, 160f)
        ).apply {
            bottomMargin = IosDesign.dpInt(context, 20f)
        })

        // Bottom frosted pill action buttons
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        editBtn = createPillButton("Add Widget") { onAddWidget?.invoke() }
        settingsBtn = createPillButton("iOSpring Settings") { onOpenSettings?.invoke() }
        btnRow.addView(editBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = IosDesign.dpInt(context, 10f) })
        btnRow.addView(settingsBtn)
        column.addView(btnRow)

        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        refreshWidgets()
    }

    fun applyGlassTheme(glassStyle: GlassThemeStyle, iconStyle: IconThemeStyle) {
        editBtn.background = IosDesign.createThemeSurface(context, 999f, glassStyle, iconStyle)
        settingsBtn.background = IosDesign.createThemeSurface(context, 999f, glassStyle, iconStyle)
    }

    fun updateInsets(topInset: Int, bottomInset: Int) {
        val padH = IosDesign.dpInt(context, 18f)
        column.setPadding(padH, topInset + IosDesign.dpInt(context, 18f), padH, bottomInset + IosDesign.dpInt(context, 36f))
    }

    fun refreshWidgets() {
        headerDateBuf.time = System.currentTimeMillis()
        headerDate.text = headerDateFormat.format(headerDateBuf)
        refreshables.forEach { it.refreshWidgetData() }
    }

    private fun createPillButton(title: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = IosDesign.TYPEFACE_MEDIUM
            background = IosDesign.createPillDrawable(context, 0x3DFFFFFF, 0x3AFFFFFF)
            val hp = IosDesign.dpInt(context, 18f)
            val vp = IosDesign.dpInt(context, 10f)
            setPadding(hp, vp, hp, vp)
            setOnClickListener { onClick() }
        }
    }
}
