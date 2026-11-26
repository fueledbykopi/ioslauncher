package com.iospring.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.iospring.launcher.core.IconCacheManager
import com.iospring.launcher.model.AppCategory
import com.iospring.launcher.model.AppEntry
import com.iospring.launcher.model.GlassThemeStyle
import com.iospring.launcher.model.IconThemeStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rightmost iOS 18+ App Library Page (also serves as the sole screen in Clean Mode).
 * - Auto-categorized 2x2 iOS category folders (3 big icons + 1 mini 2x2 quad cluster)
 * - Crisp non-clipped Search Bar switches smoothly to Alphabetical A-Z List with Fast-Scroll Index
 * - Async off-thread icon loading for instant startup & zero main-thread stutter
 * - Bottom iOS 18 "Hidden" Folder unlocked via BiometricPrompt
 */
class IosAppLibraryPage(context: Context) : FrameLayout(context) {

    var onAppClick: ((AppEntry, View) -> Unit)? = null
    var onAppLongClick: ((AppEntry, View) -> Unit)? = null
    var onOpenCategory: ((title: String, apps: List<AppEntry>) -> Unit)? = null
    var onOpenHiddenFolder: (() -> Unit)? = null
    var onOpenSettings: (() -> Unit)? = null

    private var allApps: List<AppEntry> = emptyList()
    private var hiddenPackages: Set<String> = emptySet()
    private var usageScores: Map<String, Long> = emptyMap()
    private var iconCache: IconCacheManager? = null
    private var glassStyle: GlassThemeStyle = GlassThemeStyle.LIQUID_GLASS
    private var iconStyle: IconThemeStyle = IconThemeStyle.LIGHT
    private var cleanMode: Boolean = false

    private var isListMode: Boolean = false
    private var foldersDirty: Boolean = true
    private var alphaListDirty: Boolean = true
    private var searchDebounceRunnable: Runnable? = null
    private lateinit var searchBox: LinearLayout
    private lateinit var settingsBtn: SettingsGearButton

    private val mainColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private val searchEdit = EditText(context).apply {
        hint = "Search App Library"
        setTextColor(Color.WHITE)
        setHintTextColor(0xB3FFFFFF.toInt())
        textSize = 15.5f
        gravity = Gravity.CENTER_VERTICAL
        background = null
        setPadding(0, 0, 0, 0)
        includeFontPadding = false
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }

    private val toggleModeBtn = TextView(context).apply {
        text = "A-Z"
        setTextColor(Color.WHITE)
        textSize = 13f
        typeface = IosDesign.TYPEFACE_MEDIUM_BOLD
        gravity = Gravity.CENTER
        background = IosDesign.createPillDrawable(context, 0x33FFFFFF, 0x33FFFFFF)
        val hp = IosDesign.dpInt(context, 12f)
        setPadding(hp, 0, hp, 0)
    }

    private val folderScroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
    }
    private val folderGrid = GridLayout(context).apply {
        columnCount = 2
    }

    private val listContainerFrame = FrameLayout(context).apply {
        visibility = GONE
    }
    private val alphaScroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
    }
    private val alphaListColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    private val sectionHeaderViews = mutableMapOf<Char, View>()
    private val fastScrubber = AlphabetFastScrubber(context)

    init {
        val padH = IosDesign.dpInt(context, 16f)
        mainColumn.setPadding(padH, IosDesign.dpInt(context, 48f), padH, IosDesign.dpInt(context, 90f))

        // Top App Library Search Bar + A-Z Toggle + Clean Mode Settings button
        val barRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = IosDesign.createThemeSurface(context, 18f, glassStyle, iconStyle)
            val hp = IosDesign.dpInt(context, 13f)
            setPadding(hp, 0, hp, 0)
            val glyphSize = IosDesign.dpInt(context, 17f)
            addView(SearchMagnifierView(context), LinearLayout.LayoutParams(glyphSize, glyphSize).apply {
                marginEnd = IosDesign.dpInt(context, 8f)
            })
            addView(searchEdit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        settingsBtn = SettingsGearButton(context).apply {
            visibility = GONE
            background = IosDesign.createThemeSurface(context, 999f, glassStyle, iconStyle)
            contentDescription = "iOSpring Settings"
            setOnClickListener { onOpenSettings?.invoke() }
        }

        val barH = IosDesign.dpInt(context, 44f)
        barRow.addView(searchBox, LinearLayout.LayoutParams(0, barH, 1f))
        barRow.addView(toggleModeBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, IosDesign.dpInt(context, 38f)
        ).apply {
            marginStart = IosDesign.dpInt(context, 8f)
        })
        barRow.addView(settingsBtn, LinearLayout.LayoutParams(
            IosDesign.dpInt(context, 38f), IosDesign.dpInt(context, 38f)
        ).apply {
            marginStart = IosDesign.dpInt(context, 8f)
        })
        mainColumn.addView(barRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = IosDesign.dpInt(context, 14f)
        })

        // Folder Grid view
        folderScroll.addView(folderGrid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        mainColumn.addView(folderScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // Alphabetical A-Z list + Fast-scroll right scrubber
        alphaScroll.addView(alphaListColumn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        listContainerFrame.addView(alphaScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            marginEnd = IosDesign.dpInt(context, 24f)
        })
        listContainerFrame.addView(fastScrubber, LayoutParams(
            IosDesign.dpInt(context, 24f), LayoutParams.MATCH_PARENT, Gravity.END
        ))
        mainColumn.addView(listContainerFrame, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        addView(mainColumn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        toggleModeBtn.setOnClickListener {
            setListMode(!isListMode)
        }

        searchEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !isListMode) {
                setListMode(true)
            }
        }

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                if (q.isEmpty() && !isListMode) {
                    return
                }
                if (q.isNotEmpty() && !isListMode) {
                    setListMode(true)
                }
                searchDebounceRunnable?.let { removeCallbacks(it) }
                val task = Runnable {
                    searchDebounceRunnable = null
                    alphaListDirty = false
                    renderAlphaList(q)
                }
                searchDebounceRunnable = task
                postDelayed(task, 50L)
            }
        })

        fastScrubber.onLetterSelected = { letter ->
            val target = sectionHeaderViews[letter]
            if (target != null) {
                alphaScroll.smoothScrollTo(0, target.top)
            }
        }
    }

    fun updateInsets(topInset: Int, bottomInset: Int) {
        val padH = IosDesign.dpInt(context, 16f)
        mainColumn.setPadding(padH, topInset + IosDesign.dpInt(context, 12f), padH, bottomInset + IosDesign.dpInt(context, 28f))
    }

    fun bind(
        apps: List<AppEntry>,
        hidden: Set<String>,
        usage: Map<String, Long>,
        cache: IconCacheManager,
        glassTheme: GlassThemeStyle = GlassThemeStyle.LIQUID_GLASS,
        iconTheme: IconThemeStyle = IconThemeStyle.LIGHT,
        isCleanMode: Boolean = false,
        renderNow: Boolean = isCleanMode
    ) {
        allApps = apps
        hiddenPackages = hidden
        usageScores = usage
        iconCache = cache
        glassStyle = glassTheme
        iconStyle = iconTheme
        cleanMode = isCleanMode

        searchBox.background = IosDesign.createThemeSurface(context, 18f, glassStyle, iconStyle)
        toggleModeBtn.background = IosDesign.createThemeSurface(context, 999f, glassStyle, iconStyle)
        settingsBtn.background = IosDesign.createThemeSurface(context, 999f, glassStyle, iconStyle)
        settingsBtn.visibility = if (cleanMode) VISIBLE else GONE

        foldersDirty = true
        alphaListDirty = true
        if (renderNow) {
            ensureRendered()
        }
    }

    /**
     * Lazy-materializes App Library views on demand so Home Screen cold start
     * never wastes CPU/RAM building off-screen App Library cards until needed.
     */
    fun ensureRendered() {
        if (foldersDirty) {
            foldersDirty = false
            renderCategoryFolders()
        }
        if (isListMode && alphaListDirty) {
            alphaListDirty = false
            renderAlphaList(searchEdit.text?.toString().orEmpty())
        }
    }

    fun clearSearchFocus() {
        if (searchEdit.hasFocus()) {
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm?.hideSoftInputFromWindow(searchEdit.windowToken, 0)
            searchEdit.clearFocus()
        }
    }

    fun resetToCategories(): Boolean {
        if (isListMode || searchEdit.hasFocus()) {
            setListMode(false)
            return true
        }
        return false
    }

    override fun onDetachedFromWindow() {
        searchDebounceRunnable?.let { removeCallbacks(it) }
        searchDebounceRunnable = null
        super.onDetachedFromWindow()
    }

    private fun setListMode(listMode: Boolean) {
        isListMode = listMode
        toggleModeBtn.text = if (listMode) "Grid" else "A-Z"
        folderScroll.visibility = if (listMode) GONE else VISIBLE
        listContainerFrame.visibility = if (listMode) VISIBLE else GONE
        if (listMode) {
            if (alphaListDirty) {
                alphaListDirty = false
                renderAlphaList(searchEdit.text?.toString().orEmpty())
            }
        } else {
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm?.hideSoftInputFromWindow(searchEdit.windowToken, 0)
            if (searchEdit.text?.isNotEmpty() == true) {
                searchEdit.setText("")
            }
            searchEdit.clearFocus()
            if (foldersDirty) {
                foldersDirty = false
                renderCategoryFolders()
            }
        }
    }

    private fun renderCategoryFolders() {
        folderGrid.removeAllViews()
        val cache = iconCache ?: return
        val visibleApps = allApps.filterNot { it.packageName in hiddenPackages }

        val buckets = linkedMapOf<String, List<AppEntry>>()

        // 1. Suggestions (top used)
        val suggestions = visibleApps
            .sortedByDescending { usageScores[it.componentKey] ?: 0L }
            .take(8)
        if (suggestions.isNotEmpty()) buckets["Suggestions"] = suggestions

        // 2. Recently Added
        val recent = visibleApps
            .sortedByDescending { it.firstInstallTime }
            .take(8)
        if (recent.isNotEmpty()) buckets["Recently Added"] = recent

        // 3. Auto-grouped by AppCategory
        val grouped = visibleApps.groupBy { it.category }
        AppCategory.entries
            .sortedBy { it.sortOrder }
            .forEach { cat ->
                val list = grouped[cat].orEmpty()
                if (list.isNotEmpty()) {
                    buckets[cat.displayName] = list
                }
            }

        val screenW = resources.displayMetrics.widthPixels
        val cardW = ((screenW - IosDesign.dpInt(context, 48f)) / 2).coerceAtLeast(130)

        for ((title, apps) in buckets) {
            val card = buildCategoryCard(title, apps, cache, cardW)
            folderGrid.addView(card, GridLayout.LayoutParams().apply {
                width = cardW
                setMargins(
                    IosDesign.dpInt(context, 6f),
                    IosDesign.dpInt(context, 6f),
                    IosDesign.dpInt(context, 6f),
                    IosDesign.dpInt(context, 12f)
                )
            })
        }

        // 4. iOS 18 Biometric Hidden Apps Folder at the bottom
        val hiddenCard = buildHiddenFolderCard(cardW, hiddenPackages.size)
        folderGrid.addView(hiddenCard, GridLayout.LayoutParams().apply {
            width = cardW
            setMargins(
                IosDesign.dpInt(context, 6f),
                IosDesign.dpInt(context, 6f),
                IosDesign.dpInt(context, 6f),
                IosDesign.dpInt(context, 16f)
            )
        })
    }

    private fun buildCategoryCard(
        title: String,
        apps: List<AppEntry>,
        cache: IconCacheManager,
        cardW: Int
    ): View {
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val box = GridLayout(context).apply {
            columnCount = 2
            rowCount = 2
            background = IosDesign.createThemeSurface(context, 28f, glassStyle, iconStyle)
            val p = IosDesign.dpInt(context, 12f)
            setPadding(p, p, p, p)
        }

        val cellSize = ((cardW - IosDesign.dpInt(context, 36f)) / 2).coerceAtLeast(48)

        // First 3 slots: direct-launch big icons (async non-blocking icon load)
        for (i in 0 until 3) {
            val slotView: View = if (i < apps.size) {
                val app = apps[i]
                IosAppIconView(context).apply {
                    showLabel = false
                    largeMode = true
                    bindIconAsync(cache, app.packageName, app.className, app.label)
                    setOnClickListener { onAppClick?.invoke(app, this) }
                    setOnLongClickListener {
                        onAppLongClick?.invoke(app, this)
                        true
                    }
                }
            } else {
                View(context)
            }
            box.addView(slotView, GridLayout.LayoutParams().apply {
                width = cellSize
                height = cellSize
            })
        }

        // 4th slot: either 4th app directly (if <= 4 apps) or mini 2x2 quad cluster!
        val fourthView: View = when {
            apps.size == 4 -> {
                val app = apps[3]
                IosAppIconView(context).apply {
                    showLabel = false
                    largeMode = true
                    bindIconAsync(cache, app.packageName, app.className, app.label)
                    setOnClickListener { onAppClick?.invoke(app, this) }
                    setOnLongClickListener {
                        onAppLongClick?.invoke(app, this)
                        true
                    }
                }
            }
            apps.size > 4 -> {
                val quadApps = apps.drop(3).take(4)
                MiniQuadClusterView(context, quadApps.size).apply {
                    bindIconsAsync(quadApps, cache)
                    setOnClickListener { onOpenCategory?.invoke(title, apps) }
                }
            }
            else -> View(context)
        }
        box.addView(fourthView, GridLayout.LayoutParams().apply {
            width = cellSize
            height = cellSize
        })

        wrapper.addView(box, LinearLayout.LayoutParams(cardW, cardW))
        wrapper.addView(TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = IosDesign.TYPEFACE_MEDIUM
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(4f, 0f, 1.5f, 0x99000000.toInt())
            setPadding(0, IosDesign.dpInt(context, 6f), 0, 0)
        })
        return wrapper
    }

    private fun buildHiddenFolderCard(cardW: Int, hiddenCount: Int): View {
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnClickListener { onOpenHiddenFolder?.invoke() }
        }
        val box = FrameLayout(context).apply {
            background = IosDesign.createThemeSurface(context, 28f, glassStyle, IconThemeStyle.DARK)
        }
        val lockInfo = TextView(context).apply {
            text = "🔒\nBiometric\n($hiddenCount)"
            setTextColor(0xDDFFFFFF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = IosDesign.TYPEFACE_MEDIUM
        }
        box.addView(lockInfo, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        wrapper.addView(box, LinearLayout.LayoutParams(cardW, cardW))
        wrapper.addView(TextView(context).apply {
            text = "Hidden"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = IosDesign.TYPEFACE_MEDIUM
            gravity = Gravity.CENTER
            setPadding(0, IosDesign.dpInt(context, 6f), 0, 0)
        })
        return wrapper
    }

    private fun renderAlphaList(query: String) {
        alphaListColumn.removeAllViews()
        sectionHeaderViews.clear()
        val cache = iconCache ?: return

        val q = query.trim().lowercase()
        val filtered = ArrayList<AppEntry>(allApps.size)
        for (i in allApps.indices) {
            val app = allApps[i]
            if (app.packageName in hiddenPackages) continue
            if (q.isEmpty() || app.lowerLabel.contains(q) || app.lowerPackage.contains(q)) {
                filtered.add(app)
            }
        }
        filtered.sortBy { it.lowerLabel }

        val rowPad = IosDesign.dpInt(context, 8f)
        val iconSz = IosDesign.dpInt(context, 44f)
        val textPadStart = IosDesign.dpInt(context, 14f)
        val hdrPadStart = IosDesign.dpInt(context, 8f)
        val hdrPadTop = IosDesign.dpInt(context, 12f)
        val hdrPadBot = IosDesign.dpInt(context, 4f)

        var lastLetter: Char? = null
        for (app in filtered) {
            val firstChar = app.label.firstOrNull()?.uppercaseChar()?.let {
                if (it in 'A'..'Z') it else '#'
            } ?: '#'

            if (firstChar != lastLetter) {
                lastLetter = firstChar
                val header = TextView(context).apply {
                    text = firstChar.toString()
                    setTextColor(0xCCFFFFFF.toInt())
                    textSize = 14f
                    typeface = IosDesign.TYPEFACE_MEDIUM_BOLD
                    setPadding(hdrPadStart, hdrPadTop, 0, hdrPadBot)
                }
                sectionHeaderViews[firstChar] = header
                alphaListColumn.addView(header)
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(rowPad, rowPad, rowPad, rowPad)
                setOnClickListener { onAppClick?.invoke(app, this) }
                setOnLongClickListener {
                    onAppLongClick?.invoke(app, this)
                    true
                }
            }
            val iv = ImageView(context).apply {
                tag = app.componentKey
                val cached = cache.getCachedIcon(app.packageName, app.className)
                if (cached != null) {
                    setImageBitmap(cached)
                } else {
                    cache.loadIconAsync(app.packageName, app.className, app.label) { bmp ->
                        if (tag == app.componentKey) {
                            setImageBitmap(bmp)
                        }
                    }
                }
            }
            row.addView(iv, LinearLayout.LayoutParams(iconSz, iconSz))
            row.addView(TextView(context).apply {
                text = app.label
                setTextColor(Color.WHITE)
                textSize = 15.5f
                typeface = IosDesign.TYPEFACE_REGULAR
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(textPadStart, 0, 0, 0)
            })
            alphaListColumn.addView(row)
        }
    }

    override fun canScrollVertically(direction: Int): Boolean {
        return if (isListMode) alphaScroll.canScrollVertically(direction) else folderScroll.canScrollVertically(direction)
    }

    private class MiniQuadClusterView(
        context: Context,
        slotCount: Int
    ) : View(context) {
        private val icons = arrayOfNulls<Bitmap>(slotCount.coerceIn(0, 4))
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val rect = RectF()

        fun bindIconsAsync(apps: List<AppEntry>, cache: IconCacheManager) {
            val count = minOf(apps.size, icons.size)
            for (i in 0 until count) {
                val app = apps[i]
                val cached = cache.getCachedIcon(app.packageName, app.className)
                if (cached != null) {
                    icons[i] = cached
                } else {
                    cache.loadIconAsync(app.packageName, app.className, app.label) { bmp ->
                        icons[i] = bmp
                        invalidate()
                    }
                }
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val pad = IosDesign.dp(context, 5f)
            val gap = IosDesign.dp(context, 4f)
            val cell = (minOf(width, height) - pad * 2f - gap) / 2f
            if (cell <= 0f) return
            val startX = (width - (cell * 2f + gap)) / 2f
            val startY = (height - (cell * 2f + gap)) / 2f
            for (i in icons.indices) {
                val bmp = icons[i] ?: continue
                val r = i / 2
                val c = i % 2
                val l = startX + c * (cell + gap)
                val t = startY + r * (cell + gap)
                rect.set(l, t, l + cell, t + cell)
                canvas.drawBitmap(bmp, null, rect, paint)
            }
        }
    }

    private class SearchMagnifierView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = IosDesign.dp(context, 1.75f)
            strokeCap = Paint.Cap.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val s = minOf(width, height).toFloat()
            if (s <= 0f) return
            val r = s * 0.31f
            val cx = s * 0.42f
            val cy = s * 0.42f
            canvas.drawCircle(cx, cy, r, paint)
            canvas.drawLine(cx + r * 0.72f, cy + r * 0.72f, s * 0.86f, s * 0.86f, paint)
        }
    }

    private class SettingsGearButton(context: Context) : View(context) {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xEEFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = IosDesign.dp(context, 1.8f)
            strokeCap = Paint.Cap.ROUND
        }

        init {
            isClickable = true
            isFocusable = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) * 0.21f
            if (r <= 0f) return
            canvas.drawCircle(cx, cy, r, strokePaint)
            val innerR = r * 1.15f
            val outerR = r * 1.52f
            for (i in 0 until 8) {
                val a = (i * 45.0) * PI / 180.0
                val cosA = cos(a).toFloat()
                val sinA = sin(a).toFloat()
                canvas.drawLine(
                    cx + cosA * innerR,
                    cy + sinA * innerR,
                    cx + cosA * outerR,
                    cy + sinA * outerR,
                    strokePaint
                )
            }
        }
    }

    private class AlphabetFastScrubber(context: Context) : View(context) {
        private val letters = ('A'..'Z').toList() + '#'
        var onLetterSelected: ((Char) -> Unit)? = null

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = IosDesign.sp(context, 10f)
            typeface = IosDesign.TYPEFACE_MEDIUM_BOLD
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (height <= 0) return
            val step = height.toFloat() / letters.size
            val cx = width / 2f
            letters.forEachIndexed { idx, ch ->
                val y = step * (idx + 0.72f)
                canvas.drawText(ch.toString(), cx, y, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val idx = ((event.y / height.coerceAtLeast(1)) * letters.size)
                        .toInt().coerceIn(0, letters.lastIndex)
                    onLetterSelected?.invoke(letters[idx])
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
