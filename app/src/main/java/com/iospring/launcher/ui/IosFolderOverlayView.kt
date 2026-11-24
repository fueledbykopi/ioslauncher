package com.iospring.launcher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.iospring.launcher.core.IconCacheManager
import com.iospring.launcher.model.HomeItem
import com.iospring.launcher.service.BadgeStore

/**
 * Full-screen iOS 18 expanded Folder Modal Overlay.
 * Features centered editable folder title, frosted rounded glass card, and 3x3 app grid.
 */
class IosFolderOverlayView(context: Context) : FrameLayout(context) {

    private var currentFolder: HomeItem.FolderItem? = null

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

        val cardSize = IosDesign.dpInt(context, 308f)
        cardContainer.background = IosDesign.createGlassDrawable(
            context = context,
            cornerRadiusDp = 34f,
            fillColor = 0x52F2F2F7,
            strokeColor = 0x4CFFFFFF,
            strokeWidthDp = 1f
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

    fun openFolder(
        folder: HomeItem.FolderItem,
        iconCache: IconCacheManager,
        showBadges: Boolean,
        largeIcons: Boolean,
        jiggleMode: Boolean
    ) {
        currentFolder = folder
        titleEdit.setText(folder.title)
        populateGrid(folder, iconCache, showBadges, largeIcons, jiggleMode)

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
        jiggleMode: Boolean
    ) {
        grid.removeAllViews()
        val cellSize = IosDesign.dpInt(context, 90f)
        for (app in folder.apps) {
            val icon = IosAppIconView(context).apply {
                itemId = app.id
                packageNameRef = app.packageName
                classNameRef = app.className
                label = app.label
                largeMode = largeIcons
                iconBitmap = iconCache.getIcon(app.packageName, app.className)
                badgeCount = if (showBadges) BadgeStore.countForPackage(app.packageName) else 0
                setJiggling(jiggleMode)
                setOnClickListener {
                    dismiss()
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

    fun dismiss() {
        if (visibility != VISIBLE) return
        currentFolder = null
        animate().alpha(0f).setDuration(150L).withEndAction {
            visibility = GONE
            onDismiss?.invoke()
        }.start()
        contentColumn.animate().scaleX(0.86f).scaleY(0.86f).setDuration(150L).start()
    }

    val isOpen: Boolean get() = visibility == VISIBLE
}
