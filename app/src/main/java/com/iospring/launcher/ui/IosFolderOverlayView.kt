package com.iospring.launcher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.iospring.launcher.core.IconCacheManager
import com.iospring.launcher.model.GlassThemeStyle
import com.iospring.launcher.model.HomeItem
import com.iospring.launcher.model.IconThemeStyle
import com.iospring.launcher.service.BadgeStore

/**
 * Full-screen iOS 18 expanded Folder Modal Overlay.
 * Features centered editable folder title, frosted rounded glass card, and 3x3 app grid.
 */
class IosFolderOverlayView(context: Context) : FrameLayout(context) {

    private var currentFolder: HomeItem.FolderItem? = null
    private var isDismissing: Boolean = false
    private var animToken: Int = 0

    var onAppClick: ((HomeItem.AppShortcut, View) -> Unit)? = null
    var onAppRemoveFromFolder: ((HomeItem.FolderItem, HomeItem.AppShortcut) -> Unit)? = null
    var onTitleChanged: ((HomeItem.FolderItem, String) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private val titleEdit = EditText(context).apply {
        setTextColor(Color.WHITE)
        setHintTextColor(0x99FFFFFF.toInt())
        hint = "Folder"
        textSize = 26f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        background = null
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_DONE
    }

    private val cardContainer = FrameLayout(context)
    private val grid = GridLayout(context).apply {
        columnCount = 3
    }

    private val contentColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }

    init {
        visibility = GONE
        setBackgroundColor(0x66000000)
        isClickable = true
        isFocusable = true

        setOnClickListener { dismiss() }

        val titleLp = LinearLayout.LayoutParams(
            IosDesign.dpInt(context, 280f),
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = IosDesign.dpInt(context, 18f)
        }
        contentColumn.addView(titleEdit, titleLp)

        val cardSize = IosDesign.dpInt(context, 312f)
        cardContainer.background = IosDesign.createThemeSurface(
            context = context,
            cornerRadiusDp = 36f,
            glassStyle = GlassThemeStyle.LIQUID_GLASS,
            iconStyle = IconThemeStyle.LIGHT,
            strongSurface = true
        )
        val pad = IosDesign.dpInt(context, 16f)
        cardContainer.setPadding(pad, pad, pad, pad)
        cardContainer.setOnClickListener { /* swallow tap inside card */ }

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        cardContainer.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        contentColumn.addView(cardContainer, LinearLayout.LayoutParams(cardSize, cardSize))

        val colLp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        addView(contentColumn, colLp)

        titleEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val folder = currentFolder ?: return
                val updated = s?.toString()?.trim().orEmpty().ifEmpty { "Folder" }
                if (folder.title != updated) {
                    folder.title = updated
                    onTitleChanged?.invoke(folder, updated)
                }
            }
        })
    }

    fun applyGlassTheme(glassStyle: GlassThemeStyle, iconStyle: IconThemeStyle) {
        cardContainer.background = IosDesign.createThemeSurface(
            context = context,
            cornerRadiusDp = 36f,
            glassStyle = glassStyle,
            iconStyle = iconStyle,
            strongSurface = true
        )
    }

    fun openFolder(
        folder: HomeItem.FolderItem,
        iconCache: IconCacheManager,
        showBadges: Boolean,
        largeIcons: Boolean,
        jiggleMode: Boolean,
        showLabels: Boolean = true
    ) {
        animToken++
        isDismissing = false
        animate().setListener(null).cancel()
        contentColumn.animate().cancel()

        currentFolder = folder
        titleEdit.setText(folder.title)
        populateGrid(folder, iconCache, showBadges, largeIcons, jiggleMode, showLabels)

        visibility = VISIBLE
        alpha = 0f
        contentColumn.scaleX = 0.84f
        contentColumn.scaleY = 0.84f
        animate().alpha(1f).setDuration(200L).start()
        contentColumn.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240L)
            .start()
    }

    private fun populateGrid(
        folder: HomeItem.FolderItem,
        iconCache: IconCacheManager,
        showBadges: Boolean,
        largeIcons: Boolean,
        jiggleMode: Boolean,
        showLabels: Boolean
    ) {
        grid.removeAllViews()
        val cellSize = IosDesign.dpInt(context, 90f)
        for (app in folder.apps) {
            val icon = IosAppIconView(context).apply {
                itemId = app.id
                packageNameRef = app.packageName
                classNameRef = app.className
                label = app.label
                showLabel = showLabels
                largeMode = largeIcons
                bindIconAsync(iconCache, app.packageName, app.className, app.label)
                badgeCount = if (showBadges) BadgeStore.countForPackage(app.packageName) else 0
                setJiggling(jiggleMode)
                setOnClickListener {
                    dismiss(immediate = true)
                    onAppClick?.invoke(app, this)
                }
                setOnLongClickListener {
                    onAppRemoveFromFolder?.invoke(folder, app)
                    true
                }
                onRemoveBadgeClick = {
                    onAppRemoveFromFolder?.invoke(folder, app)
                }
            }
            val lp = GridLayout.LayoutParams().apply {
                width = cellSize
                height = IosDesign.dpInt(context, 94f)
            }
            grid.addView(icon, lp)
        }
    }

    fun dismiss(immediate: Boolean = false) {
        if (visibility != VISIBLE) return
        val token = ++animToken
        isDismissing = true
        currentFolder = null
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(titleEdit.windowToken, 0)
        titleEdit.clearFocus()

        animate().setListener(null).cancel()
        contentColumn.animate().cancel()
        if (immediate) {
            finalizeDismiss()
            return
        }

        animate()
            .alpha(0f)
            .setDuration(150L)
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
        contentColumn.animate().scaleX(0.86f).scaleY(0.86f).setDuration(150L).start()
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
        contentColumn.scaleX = 1f
        contentColumn.scaleY = 1f
        onDismiss?.invoke()
    }

    override fun onDetachedFromWindow() {
        animate().setListener(null).cancel()
        contentColumn.animate().cancel()
        super.onDetachedFromWindow()
    }

    val isOpen: Boolean get() = visibility == VISIBLE && !isDismissing
}
