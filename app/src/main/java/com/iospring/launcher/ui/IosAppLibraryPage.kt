package com.iospring.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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

/**
 * Rightmost iOS 18 App Library Page.
 * - Auto-categorized 2x2 iOS category folders (3 big icons + 1 mini 2x2 quad cluster)
 * - Top Search Bar switches cleanly to Alphabetical A-Z List with right-side Fast-Scroll Index
 * - Bottom iOS 18 "Hidden" Folder unlocked via BiometricPrompt
 */
class IosAppLibraryPage(context: Context) : FrameLayout(context) {

    var onAppClick: ((AppEntry, View) -> Unit)? = null
    var onAppLongClick: ((AppEntry, View) -> Unit)? = null
    var onOpenCategory: ((title: String, apps: List<AppEntry>) -> Unit)? = null
    var onOpenHiddenFolder: (() -> Unit)? = null

    private var allApps: List<AppEntry> = emptyList()
    private var hiddenPackages: Set<String> = emptySet()
    private var usageScores: Map<String, Long> = emptyMap()
    private var iconCache: IconCacheManager? = null

    private var isListMode: Boolean = false

    private val mainColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private val searchEdit = EditText(context).apply {
        hint = "🔍  App Library"
        setTextColor(Color.WHITE)
        setHintTextColor(0xB3FFFFFF.toInt())
        textSize = 16f
        gravity = Gravity.CENTER
        background = null
        isSingleLine = true
    }

    private val toggleModeBtn = TextView(context).apply {
        text = "A-Z"
        setTextColor(Color.WHITE)
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        gravity = Gravity.CENTER
        background = IosDesign.createPillDrawable(context, 0x33FFFFFF, 0x33FFFFFF)
        val hp = IosDesign.dpInt(context, 12f)
        val vp = IosDesign.dpInt(context, 6f)
        setPadding(hp, vp, hp, vp)
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

        // Top App Library Search Bar + A-Z Toggle
        val barRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val searchBox = FrameLayout(context).apply {
            background = IosDesign.createGlassDrawable(context, 16f, 0x36FFFFFF, 0x32FFFFFF)
            val hp = IosDesign.dpInt(context, 14f)
            setPadding(hp, 0, hp, 0)
            addView(searchEdit, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        barRow.addView(searchBox, LinearLayout.LayoutParams(0, IosDesign.dpInt(context, 44f), 1f))
        barRow.addView(toggleModeBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, IosDesign.dpInt(context, 36f)
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
                if (q.isNotEmpty() && !isListMode) {
                    setListMode(true)
                }
                renderAlphaList(q)
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
        cache: IconCacheManager
    ) {
        allApps = apps
        hiddenPackages = hidden
        usageScores = usage
        iconCache = cache
        renderCategoryFolders()
        renderAlphaList(searchEdit.text?.toString().orEmpty())
    }

    private fun setListMode(listMode: Boolean) {
        isListMode = listMode
        toggleModeBtn.text = if (listMode) "Grid" else "A-Z"
        folderScroll.visibility = if (listMode) GONE else VISIBLE
        listContainerFrame.visibility = if (listMode) VISIBLE else GONE
        if (!listMode) {
            searchEdit.setText("")
            searchEdit.clearFocus()
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
            background = IosDesign.createGlassDrawable(context, 26f, 0x38F2F2F7, 0x38FFFFFF)
            val p = IosDesign.dpInt(context, 12f)
            setPadding(p, p, p, p)
        }

        val cellSize = ((cardW - IosDesign.dpInt(context, 36f)) / 2).coerceAtLeast(48)

        // First 3 slots: direct-launch big icons
        for (i in 0 until 3) {
            val slotView: View = if (i < apps.size) {
                val app = apps[i]
                IosAppIconView(context).apply {
                    packageNameRef = app.packageName
                    classNameRef = app.className
                    showLabel = false
                    largeMode = true
                    iconBitmap = cache.getIcon(app.packageName, app.className)
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
                    packageNameRef = app.packageName
                    classNameRef = app.className
                    showLabel = false
                    largeMode = true
                    iconBitmap = cache.getIcon(app.packageName, app.className)
                    setOnClickListener { onAppClick?.invoke(app, this) }
                }
            }
            apps.size > 4 -> {
                val miniBitmaps = apps.drop(3).take(4).map { cache.getIcon(it.packageName, it.className) }
                MiniQuadClusterView(context, miniBitmaps).apply {
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
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            maxLines = 1
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
            background = IosDesign.createGlassDrawable(context, 26f, 0x481C1C1E, 0x38FFFFFF)
        }
        val lockInfo = TextView(context).apply {
            text = "🔒\nBiometric\n($hiddenCount)"
            setTextColor(0xDDFFFFFF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        box.addView(lockInfo, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        wrapper.addView(box, LinearLayout.LayoutParams(cardW, cardW))
        wrapper.addView(TextView(context).apply {
            text = "Hidden"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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
        val filtered = allApps
            .filterNot { it.packageName in hiddenPackages }
            .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
            .sortedBy { it.label.lowercase() }

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
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setPadding(IosDesign.dpInt(context, 8f), IosDesign.dpInt(context, 12f), 0, IosDesign.dpInt(context, 4f))
                }
                sectionHeaderViews[firstChar] = header
                alphaListColumn.addView(header)
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = IosDesign.dpInt(context, 8f)
                setPadding(p, p, p, p)
                setOnClickListener { onAppClick?.invoke(app, this) }
                setOnLongClickListener {
                    onAppLongClick?.invoke(app, this)
                    true
                }
            }
            val iv = ImageView(context).apply {
                setImageBitmap(cache.getIcon(app.packageName, app.className))
            }
            val iconSz = IosDesign.dpInt(context, 44f)
            row.addView(iv, LinearLayout.LayoutParams(iconSz, iconSz))
            row.addView(TextView(context).apply {
                text = app.label
                setTextColor(Color.WHITE)
                textSize = 15.5f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                setPadding(IosDesign.dpInt(context, 14f), 0, 0, 0)
            })
            alphaListColumn.addView(row)
        }
    }

    override fun canScrollVertically(direction: Int): Boolean {
        return if (isListMode) alphaScroll.canScrollVertically(direction) else folderScroll.canScrollVertically(direction)
    }

    private class MiniQuadClusterView(
        context: Context,
        private val icons: List<Bitmap>
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val pad = IosDesign.dp(context, 5f)
            val gap = IosDesign.dp(context, 4f)
            val cell = (minOf(width, height) - pad * 2f - gap) / 2f
            if (cell <= 0f) return
            val startX = (width - (cell * 2f + gap)) / 2f
            val startY = (height - (cell * 2f + gap)) / 2f
            for (i in 0 until minOf(icons.size, 4)) {
                val r = i / 2
                val c = i % 2
                val l = startX + c * (cell + gap)
                val t = startY + r * (cell + gap)
                rect.set(l, t, l + cell, t + cell)
                canvas.drawBitmap(icons[i], null, rect, paint)
            }
        }
    }

    private class AlphabetFastScrubber(context: Context) : View(context) {
        private val letters = ('A'..'Z').toList() + '#'
        var onLetterSelected: ((Char) -> Unit)? = null

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = IosDesign.sp(context, 10f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
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
