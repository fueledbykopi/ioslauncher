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
import android.widget.TextView
import com.iospring.launcher.core.IconCacheManager
import com.iospring.launcher.core.SpotlightEngine
import com.iospring.launcher.model.AppEntry

/**
 * Full-screen iOS 18 Spotlight Search Overlay.
 * Supports real-time prefix, initials, fuzzy & T9 numeric matching,
 * inline calculator, local Siri Suggestions, and quick Search-on-Web action.
 */
class IosSpotlightOverlayView(context: Context) : FrameLayout(context) {

    var onAppSelected: ((AppEntry, View) -> Unit)? = null
    var onWebSearch: ((query: String) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private var installedApps: List<AppEntry> = emptyList()
    private var hiddenPackages: Set<String> = emptySet()
    private var usageScores: Map<String, Long> = emptyMap()
    private var iconCache: IconCacheManager? = null
    private var isDismissing: Boolean = false
    private var animToken: Int = 0

    private val showKeyboardRunnable = Runnable {
        if (visibility == VISIBLE && !isDismissing) {
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private val contentColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private val searchInput = EditText(context).apply {
        hint = "Search apps, T9, or math..."
        setTextColor(Color.WHITE)
        setHintTextColor(0x99FFFFFF.toInt())
        textSize = 16f
        background = null
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_SEARCH
    }

    private val clearBtn = TextView(context).apply {
        text = "✕"
        setTextColor(0xCCFFFFFF.toInt())
        textSize = 14f
        gravity = Gravity.CENTER
        visibility = GONE
    }

    private val resultsContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    init {
        visibility = GONE
        setBackgroundColor(0xB80E0E12.toInt())
        isClickable = true
        isFocusable = true

        val padH = IosDesign.dpInt(context, 18f)
        contentColumn.setPadding(padH, IosDesign.dpInt(context, 54f), padH, IosDesign.dpInt(context, 24f))

        // Top search bar row: [ Frosted Pill Input ] [ Cancel ]
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val inputPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = IosDesign.createGlassDrawable(
                context = context,
                cornerRadiusDp = 16f,
                fillColor = 0x38FFFFFF,
                strokeColor = 0x34FFFFFF
            )
            setPadding(IosDesign.dpInt(context, 14f), IosDesign.dpInt(context, 8f), IosDesign.dpInt(context, 10f), IosDesign.dpInt(context, 8f))
        }

        val searchGlyph = TextView(context).apply {
            text = "🔍"
            textSize = 15f
            setPadding(0, 0, IosDesign.dpInt(context, 8f), 0)
        }
        inputPill.addView(searchGlyph)
        inputPill.addView(searchInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        inputPill.addView(clearBtn, LinearLayout.LayoutParams(IosDesign.dpInt(context, 28f), IosDesign.dpInt(context, 28f)))

        val cancelBtn = TextView(context).apply {
            text = "Cancel"
            setTextColor(IosDesign.IOS_BLUE)
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(IosDesign.dpInt(context, 14f), IosDesign.dpInt(context, 8f), 0, IosDesign.dpInt(context, 8f))
            setOnClickListener { dismiss() }
        }

        topRow.addView(inputPill, LinearLayout.LayoutParams(0, IosDesign.dpInt(context, 46f), 1f))
        topRow.addView(cancelBtn)
        contentColumn.addView(topRow)

        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(resultsContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        contentColumn.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply {
            topMargin = IosDesign.dpInt(context, 16f)
        })

        addView(contentColumn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        clearBtn.setOnClickListener { searchInput.setText("") }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                clearBtn.visibility = if (q.isEmpty()) GONE else VISIBLE
                renderQuery(q)
            }
        })

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = searchInput.text.toString().trim()
                if (q.isNotEmpty()) {
                    val res = SpotlightEngine.search(q, installedApps, hiddenPackages, usageScores)
                    val first = res.matchedApps.firstOrNull()
                    if (first != null) {
                        dismiss(immediate = true)
                        onAppSelected?.invoke(first, searchInput)
                    } else {
                        dismiss(immediate = true)
                        onWebSearch?.invoke(q)
                    }
                }
                true
            } else {
                false
            }
        }
    }

    fun updateTopInset(statusBarTop: Int) {
        val padH = IosDesign.dpInt(context, 18f)
        contentColumn.setPadding(padH, statusBarTop + IosDesign.dpInt(context, 14f), padH, IosDesign.dpInt(context, 24f))
    }

    fun open(
        apps: List<AppEntry>,
        hidden: Set<String>,
        usage: Map<String, Long>,
        cache: IconCacheManager
    ) {
        installedApps = apps
        hiddenPackages = hidden
        usageScores = usage
        iconCache = cache

        animToken++
        isDismissing = false
        removeCallbacks(showKeyboardRunnable)
        animate().setListener(null).cancel()

        searchInput.setText("")
        renderQuery("")

        visibility = VISIBLE
        alpha = 0f
        translationY = -IosDesign.dp(context, 28f)
        animate().alpha(1f).translationY(0f).setDuration(220L).start()

        searchInput.requestFocus()
        postDelayed(showKeyboardRunnable, 90L)
    }

    fun dismiss(immediate: Boolean = false) {
        if (visibility != VISIBLE) return
        val token = ++animToken
        isDismissing = true
        removeCallbacks(showKeyboardRunnable)
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchInput.clearFocus()

        animate().setListener(null).cancel()
        if (immediate) {
            finalizeDismiss()
            return
        }

        animate()
            .alpha(0f)
            .translationY(-IosDesign.dp(context, 20f))
            .setDuration(160L)
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
    }

    fun finalizePendingDismiss() {
        if (isDismissing || (visibility == VISIBLE && alpha < 0.05f)) {
            dismiss(immediate = true)
        }
    }

    private fun finalizeDismiss() {
        removeCallbacks(showKeyboardRunnable)
        animate().setListener(null)
        isDismissing = false
        visibility = GONE
        alpha = 1f
        translationY = 0f
        onDismiss?.invoke()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(showKeyboardRunnable)
        animate().setListener(null).cancel()
        super.onDetachedFromWindow()
    }

    val isOpen: Boolean get() = visibility == VISIBLE && !isDismissing

    private fun renderQuery(query: String) {
        resultsContainer.removeAllViews()
        val cache = iconCache ?: return
        val res = SpotlightEngine.search(query, installedApps, hiddenPackages, usageScores)

        // 1. Inline Calculator Card (if math expression)
        if (res.mathResult != null && res.mathExpression != null) {
            val mathCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = IosDesign.createGlassDrawable(context, 18f, 0x38FFFFFF, 0x30FFFFFF)
                val p = IosDesign.dpInt(context, 16f)
                setPadding(p, p, p, p)
            }
            mathCard.addView(TextView(context).apply {
                text = "CALCULATOR"
                setTextColor(0x99FFFFFF.toInt())
                textSize = 11f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            })
            mathCard.addView(TextView(context).apply {
                text = "${res.mathExpression} = ${res.mathResult}"
                setTextColor(Color.WHITE)
                textSize = 24f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setPadding(0, IosDesign.dpInt(context, 4f), 0, 0)
            })
            resultsContainer.addView(mathCard, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = IosDesign.dpInt(context, 14f)
            })
        }

        // 2. Siri Suggestions when query is empty, or Top App Matches when typing
        val appsToShow = if (query.isBlank()) res.suggestedApps else res.matchedApps
        val sectionTitle = if (query.isBlank()) "SIRI SUGGESTIONS" else "APPLICATIONS"

        if (appsToShow.isNotEmpty()) {
            resultsContainer.addView(createSectionHeader(sectionTitle))
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = IosDesign.createGlassDrawable(context, 20f, 0x2CFFFFFF, 0x28FFFFFF)
                val p = IosDesign.dpInt(context, 10f)
                setPadding(p, p, p, p)
            }
            val grid = GridLayout(context).apply { columnCount = 4 }
            val cellW = ((resources.displayMetrics.widthPixels - IosDesign.dpInt(context, 56f)) / 4).coerceAtLeast(64)

            for (app in appsToShow.take(8)) {
                val icon = IosAppIconView(context).apply {
                    packageNameRef = app.packageName
                    classNameRef = app.className
                    label = app.label
                    iconBitmap = cache.getIcon(app.packageName, app.className, app.label)
                    setOnClickListener {
                        dismiss(immediate = true)
                        onAppSelected?.invoke(app, this)
                    }
                }
                grid.addView(icon, GridLayout.LayoutParams().apply {
                    width = cellW
                    height = IosDesign.dpInt(context, 86f)
                })
            }
            card.addView(grid)
            resultsContainer.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = IosDesign.dpInt(context, 14f)
            })
        }

        // 3. Quick Search Web Action Button
        if (query.isNotBlank()) {
            val webBtn = TextView(context).apply {
                text = "🌐   Search the Web for \"$query\""
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = IosDesign.createGlassDrawable(context, 16f, 0x36FFFFFF, 0x30FFFFFF)
                val p = IosDesign.dpInt(context, 16f)
                setPadding(p, IosDesign.dpInt(context, 14f), p, IosDesign.dpInt(context, 14f))
                setOnClickListener {
                    dismiss(immediate = true)
                    onWebSearch?.invoke(query.trim())
                }
            }
            resultsContainer.addView(webBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun createSectionHeader(title: String): TextView {
        return TextView(context).apply {
            text = title
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(IosDesign.dpInt(context, 6f), 0, 0, IosDesign.dpInt(context, 8f))
        }
    }
}
