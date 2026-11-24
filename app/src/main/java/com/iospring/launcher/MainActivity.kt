package com.iospring.launcher

import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.iospring.launcher.core.BiometricAuthHelper
import com.iospring.launcher.core.ColorOsCompat
import com.iospring.launcher.core.GestureSafeInsets
import com.iospring.launcher.core.IconCacheManager
import com.iospring.launcher.data.GridOccupancy
import com.iospring.launcher.data.LauncherRepository
import com.iospring.launcher.model.AppEntry
import com.iospring.launcher.model.BuiltInWidgetType
import com.iospring.launcher.model.DockSlot
import com.iospring.launcher.model.HomeItem
import com.iospring.launcher.model.IconSizeMode
import com.iospring.launcher.model.IconThemeStyle
import com.iospring.launcher.model.LauncherPreferences
import com.iospring.launcher.model.LauncherWorkspaceState
import com.iospring.launcher.service.BadgeStore
import com.iospring.launcher.service.PackageChangeReceiver
import com.iospring.launcher.ui.IosAppIconView
import com.iospring.launcher.ui.IosAppLibraryPage
import com.iospring.launcher.ui.IosContextMenuOverlay
import com.iospring.launcher.ui.IosDesign
import com.iospring.launcher.ui.IosDockView
import com.iospring.launcher.ui.IosFolderOverlayView
import com.iospring.launcher.ui.IosHomeGridPage
import com.iospring.launcher.ui.IosSearchPillView
import com.iospring.launcher.ui.IosSpotlightOverlayView
import com.iospring.launcher.ui.IosTodayViewPage
import com.iospring.launcher.ui.SettingsActivity
import com.iospring.launcher.ui.SpringWorkspacePager
import com.iospring.launcher.widget.IosBuiltInWidgetFactory
import com.iospring.launcher.widget.RefreshableIosWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Main Home Launcher Activity for iOSpring.
 * Engineered specifically for silky 60/120Hz iOS 18 aesthetics and zero-conflict
 * OPPO Android 15 (ColorOS 15) full-screen gesture navigation.
 */
class MainActivity : ComponentActivity() {

    private lateinit var repository: LauncherRepository
    private lateinit var iconCache: IconCacheManager
    private var launcherApps: LauncherApps? = null

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var prefs: LauncherPreferences = LauncherPreferences()
    private var workspace: LauncherWorkspaceState = LauncherWorkspaceState()
    private var installedApps: List<AppEntry> = emptyList()
    private var usageScores: Map<String, Long> = emptyMap()
    private var safeInsets: GestureSafeInsets = GestureSafeInsets()

    private var isJiggleMode: Boolean = false
    private var currentHomePageOffset: Int = 0 // 1 if Today View is enabled, else 0

    // Root & Layered Views
    private lateinit var rootFrame: FrameLayout
    private lateinit var workspaceBlurContainer: FrameLayout
    private lateinit var pager: SpringWorkspacePager
    private lateinit var dockView: IosDockView
    private lateinit var searchPill: IosSearchPillView
    private lateinit var editTopBar: LinearLayout

    // Overlays
    private lateinit var folderOverlay: IosFolderOverlayView
    private lateinit var spotlightOverlay: IosSpotlightOverlayView
    private lateinit var contextMenuOverlay: IosContextMenuOverlay

    // Special Pages
    private var todayViewPage: IosTodayViewPage? = null
    private var appLibraryPage: IosAppLibraryPage? = null
    private val homeGridPages = mutableListOf<IosHomeGridPage>()

    // Jiggle Drag & Drop state
    private var dragItem: HomeItem? = null
    private var dragGhostView: ImageView? = null
    private var dragTouchSlop: Int = 16
    private var dragStartX: Float = 0f
    private var dragStartY: Float = 0f
    private var dragMoved: Boolean = false
    private var edgeFlipRunnable: Runnable? = null

    private var packageReceiver: PackageChangeReceiver? = null
    private var liveTickReceiverRegistered: Boolean = false

    private val badgeListener: () -> Unit = {
        runOnUiThread { refreshBadgesInPlace() }
    }

    private val liveTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshVisibleWidgets()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. OPPO Android 15 / ColorOS 15 edge-to-edge & transparent wallpaper window
        ColorOsCompat.configureEdgeToEdge(this)

        repository = LauncherRepository(applicationContext)
        iconCache = IconCacheManager(applicationContext)
        launcherApps = getSystemService(LauncherApps::class.java)
        dragTouchSlop = ViewConfiguration.get(this).scaledTouchSlop

        prefs = repository.loadPreferences()
        iconCache.updateTheme(prefs.iconThemeStyle, prefs.iconTintColor)

        buildViewHierarchy()
        setContentView(rootFrame)

        // 2. Observe system bars & ColorOS 15 gesture safe zones
        ColorOsCompat.observeInsets(rootFrame) { insets ->
            safeInsets = insets
            applySafeInsets(insets)
        }

        // 3. Android 15 Predictive Back handling (never kills launcher on home screen)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackOrHome(isHomeGesture = false)
            }
        })

        // 4. Register lightweight incremental package change receiver
        packageReceiver = PackageChangeReceiver(
            onPackageAdded = { pkg -> handleIncrementalPackageUpdate(pkg, removed = false) },
            onPackageRemoved = { pkg -> handleIncrementalPackageUpdate(pkg, removed = true) },
            onPackageChanged = { pkg -> handleIncrementalPackageUpdate(pkg, removed = false) }
        ).also { it.register(this) }

        BadgeStore.addListener(badgeListener)

        // 5. Async app & workspace loading on Dispatchers.IO for instant startup
        loadLauncherDataAsync()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Triggered smoothly when OPPO ColorOS 15 Home gesture is performed
        handleBackOrHome(isHomeGesture = true)
    }

    override fun onResume() {
        super.onResume()
        ColorOsCompat.syncStatusBarAppearanceWithWallpaper(this)

        // Reload preferences in case user changed theme/grid/hidden apps in SettingsActivity
        val latestPrefs = repository.loadPreferences()
        if (latestPrefs != prefs) {
            val themeChanged = latestPrefs.iconThemeStyle != prefs.iconThemeStyle ||
                latestPrefs.iconTintColor != prefs.iconTintColor
            prefs = latestPrefs
            if (themeChanged) {
                iconCache.updateTheme(prefs.iconThemeStyle, prefs.iconTintColor)
            }
            pager.colorOsSafeZoneEnabled = prefs.colorOsGestureSafeZone
            pager.swipeDownSpotlightEnabled = prefs.swipeDownSpotlight
            repository.reconcileWithInstalledApps(workspace, installedApps, prefs)
            rebuildWorkspacePages(preserveCurrentPage = true)
        }

        registerLiveTickReceiver()
        refreshVisibleWidgets()
        refreshBadgesInPlace()
    }

    override fun onPause() {
        unregisterLiveTickReceiver()
        if (isJiggleMode) {
            exitJiggleMode()
        }
        super.onPause()
    }

    override fun onDestroy() {
        BadgeStore.removeListener(badgeListener)
        packageReceiver?.unregister(this)
        unregisterLiveTickReceiver()
        mainScope.cancel()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            iconCache.clearMemory()
        }
    }

    // -------------------------------------------------------------------------
    // View Hierarchy Setup
    // -------------------------------------------------------------------------

    private fun buildViewHierarchy() {
        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }

        workspaceBlurContainer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }

        pager = SpringWorkspacePager(this).apply {
            colorOsSafeZoneEnabled = prefs.colorOsGestureSafeZone
            swipeDownSpotlightEnabled = prefs.swipeDownSpotlight
            onPageSettled = { pageIdx ->
                handlePageSettled(pageIdx)
            }
            onScrollProgress = { scrollPx, pageW, totalCount ->
                if (totalCount > 1 && pageW > 0) {
                    val norm = scrollPx / ((totalCount - 1) * pageW).toFloat()
                    ColorOsCompat.updateWallpaperParallax(this@MainActivity, rootFrame, norm, totalCount)
                }
                updateDockAndPillVisibility(scrollPx, pageW)
            }
            onSwipeDownSpotlight = { _, committed ->
                if (committed && !isJiggleMode && !spotlightOverlay.isOpen && !folderOverlay.isOpen) {
                    openSpotlight()
                }
            }
            onEmptyLongPress = {
                if (!isJiggleMode) {
                    IosDesign.hapticLongPress(this, prefs.hapticsEnabled)
                    enterJiggleMode()
                }
            }
            onEmptyTap = {
                if (isJiggleMode) {
                    exitJiggleMode()
                }
            }
        }
        workspaceBlurContainer.addView(
            pager,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        // Floating iOS Search Pill / Page Dots above Dock
        searchPill = IosSearchPillView(this).apply {
            showSearchLabel = prefs.showSearchPill
            setOnClickListener {
                if (!isJiggleMode) {
                    IosDesign.hapticTick(this, prefs.hapticsEnabled)
                    openSpotlight()
                }
            }
        }
        val pillLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            IosDesign.dpInt(this, 34f),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = IosDesign.dpInt(this@MainActivity, 118f)
        }
        workspaceBlurContainer.addView(searchPill, pillLp)

        // Floating Frosted Glass Dock
        dockView = IosDockView(this).apply {
            onDockAppClick = { slot, anchor ->
                launchAppByComponent(slot.packageName, slot.className, slot.label, anchor)
            }
            onDockAppLongClick = { slot, anchor ->
                IosDesign.hapticLongPress(anchor, prefs.hapticsEnabled)
                showAppContextMenu(
                    packageName = slot.packageName,
                    className = slot.className,
                    label = slot.label,
                    homeItem = null,
                    dockSlot = slot
                )
            }
            onDockAppRemove = { slot ->
                workspace.dock.removeAll { it.componentKey == slot.componentKey }
                repository.saveWorkspaceAsync(workspace)
                bindDock()
            }
        }
        val dockLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            IosDesign.dpInt(this, 88f),
            Gravity.BOTTOM
        ).apply {
            leftMargin = IosDesign.dpInt(this@MainActivity, 14f)
            rightMargin = IosDesign.dpInt(this@MainActivity, 14f)
            bottomMargin = IosDesign.dpInt(this@MainActivity, 22f)
        }
        workspaceBlurContainer.addView(dockView, dockLp)

        // Jiggle Mode Top Edit Bar ([ Edit ] ... [ Done ])
        editTopBar = buildJiggleEditBar()
        workspaceBlurContainer.addView(
            editTopBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        rootFrame.addView(
            workspaceBlurContainer,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        // Overlays (Folder, Spotlight, Context Menu)
        folderOverlay = IosFolderOverlayView(this).apply {
            onAppClick = { app, anchor ->
                launchAppByComponent(app.packageName, app.className, app.label, anchor)
            }
            onAppRemoveFromFolder = { folder, app ->
                folder.apps.removeAll { it.id == app.id }
                if (folder.apps.isEmpty()) {
                    workspace.items.removeAll { it.id == folder.id }
                } else if (folder.apps.size == 1) {
                    val last = folder.apps.first()
                    val idx = workspace.items.indexOfFirst { it.id == folder.id }
                    if (idx >= 0) {
                        workspace.items[idx] = last.copy(page = folder.page, col = folder.col, row = folder.row)
                    }
                }
                repository.saveWorkspaceAsync(workspace)
                dismiss()
                rebuildWorkspacePages(preserveCurrentPage = true)
            }
            onTitleChanged = { _, _ ->
                repository.saveWorkspaceAsync(workspace)
                rebuildHomeGridOnly()
            }
            onDismiss = { setOverlayBlur(false) }
        }
        rootFrame.addView(folderOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        spotlightOverlay = IosSpotlightOverlayView(this).apply {
            onAppSelected = { app, anchor ->
                launchAppByComponent(app.packageName, app.className, app.label, anchor)
            }
            onWebSearch = { query ->
                val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }
            onDismiss = { setOverlayBlur(false) }
        }
        rootFrame.addView(spotlightOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        contextMenuOverlay = IosContextMenuOverlay(this).apply {
            onDismiss = { setOverlayBlur(false) }
        }
        rootFrame.addView(contextMenuOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun buildJiggleEditBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            val hp = IosDesign.dpInt(this@MainActivity, 18f)
            setPadding(hp, IosDesign.dpInt(this@MainActivity, 44f), hp, IosDesign.dpInt(this@MainActivity, 8f))
        }

        val editPill = TextView(this).apply {
            text = "Edit"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = IosDesign.createPillDrawable(this@MainActivity, 0x58FFFFFF, 0x44FFFFFF)
            val hp = IosDesign.dpInt(this@MainActivity, 18f)
            val vp = IosDesign.dpInt(this@MainActivity, 7f)
            setPadding(hp, vp, hp, vp)
            setOnClickListener { showEditMenuDialog() }
        }

        val spacer = View(this)

        val donePill = TextView(this).apply {
            text = "Done"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = IosDesign.createPillDrawable(this@MainActivity, 0x58FFFFFF, 0x44FFFFFF)
            val hp = IosDesign.dpInt(this@MainActivity, 18f)
            val vp = IosDesign.dpInt(this@MainActivity, 7f)
            setPadding(hp, vp, hp, vp)
            setOnClickListener { exitJiggleMode() }
        }

        bar.addView(editPill)
        bar.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(donePill)
        return bar
    }

    private fun applySafeInsets(insets: GestureSafeInsets) {
        pager.safeInsets = insets

        // Position Floating Dock cleanly ABOVE OPPO ColorOS 15 gesture pill
        val dockBottomMargin = maxOf(insets.navBarBottom, IosDesign.dpInt(this, 10f)) + IosDesign.dpInt(this, 10f)
        val dockHeight = IosDesign.dpInt(this, 86f)
        (dockView.layoutParams as FrameLayout.LayoutParams).apply {
            bottomMargin = dockBottomMargin
            height = dockHeight
        }
        dockView.requestLayout()

        // Search pill sits right above Dock
        (searchPill.layoutParams as FrameLayout.LayoutParams).apply {
            bottomMargin = dockBottomMargin + dockHeight + IosDesign.dpInt(this@MainActivity, 10f)
        }
        searchPill.requestLayout()

        // Jiggle mode top bar clears status bar
        val hp = IosDesign.dpInt(this, 18f)
        editTopBar.setPadding(hp, insets.statusBarTop + IosDesign.dpInt(this, 6f), hp, IosDesign.dpInt(this, 6f))

        // Grid pages pad top below status bar and bottom above Search Pill + Dock
        val gridTop = insets.statusBarTop + IosDesign.dpInt(this, 14f)
        val gridBottom = dockBottomMargin + dockHeight + IosDesign.dpInt(this, 48f)
        val gridSide = IosDesign.dpInt(this, 14f)
        homeGridPages.forEach { page ->
            page.setPadding(gridSide, gridTop, gridSide, gridBottom)
        }

        todayViewPage?.updateInsets(insets.statusBarTop, insets.navBarBottom)
        appLibraryPage?.updateInsets(insets.statusBarTop, insets.navBarBottom)
        spotlightOverlay.updateTopInset(insets.statusBarTop)
    }

    // -------------------------------------------------------------------------
    // Async Data Loading & Incremental Package Updates (Dispatchers.IO)
    // -------------------------------------------------------------------------

    private fun loadLauncherDataAsync() {
        mainScope.launch {
            val (apps, scores, loadedWorkspace) = withContext(Dispatchers.IO) {
                val appsList = repository.loadInstalledApps()
                val usage = repository.loadUsageScores()
                val ws = repository.loadOrCreateWorkspace(appsList, prefs)
                // Warm icon cache for Dock & Page 0 on background IO thread for instant first frame
                ws.dock.forEach { iconCache.getIcon(it.packageName, it.className) }
                ws.items.filter { it.page == 0 }.forEach { item ->
                    when (item) {
                        is HomeItem.AppShortcut -> iconCache.getIcon(item.packageName, item.className)
                        is HomeItem.FolderItem -> item.apps.take(9).forEach {
                            iconCache.getIcon(it.packageName, it.className)
                        }
                        else -> Unit
                    }
                }
                Triple(appsList, usage, ws)
            }
            installedApps = apps
            usageScores = scores
            workspace = loadedWorkspace
            rebuildWorkspacePages(preserveCurrentPage = false)
        }
    }

    private fun handleIncrementalPackageUpdate(packageName: String, removed: Boolean) {
        mainScope.launch {
            val updatedList = withContext(Dispatchers.IO) {
                val mutable = installedApps.filterNot { it.packageName == packageName }.toMutableList()
                if (!removed) {
                    val freshForPkg = repository.loadAppForPackage(packageName)
                    mutable.addAll(freshForPkg)
                }
                mutable.sortBy { it.label.lowercase() }
                mutable
            }

            val previousKeys = installedApps.map { it.componentKey }.toSet()
            installedApps = updatedList

            if (!removed) {
                // Auto-place newly installed apps on last Home page (just like iOS!)
                val newApps = updatedList.filter { it.packageName == packageName && it.componentKey !in previousKeys }
                for (app in newApps) {
                    placeNewAppOnHome(app)
                }
            }
            repository.reconcileWithInstalledApps(workspace, installedApps, prefs)
            repository.saveWorkspaceAsync(workspace)
            rebuildWorkspacePages(preserveCurrentPage = true)
        }
    }

    private fun placeNewAppOnHome(app: AppEntry) {
        if (app.packageName in prefs.hiddenPackages) return
        val lastPage = (workspace.pagesCount - 1).coerceAtLeast(0)
        val occ = GridOccupancy.forPage(lastPage, prefs.gridCols, prefs.gridRows, workspace.items)
        val slot = occ.findFirstVacant(1, 1)
        val targetPage = if (slot != null) lastPage else lastPage + 1
        val (col, row) = slot ?: Pair(0, 0)
        workspace.items.add(
            HomeItem.AppShortcut(
                id = LauncherRepository.newId(),
                page = targetPage,
                col = col,
                row = row,
                packageName = app.packageName,
                className = app.className,
                label = app.label
            )
        )
        workspace.pagesCount = maxOf(workspace.pagesCount, targetPage + 1)
    }

    // -------------------------------------------------------------------------
    // Workspace Rendering
    // -------------------------------------------------------------------------

    private fun rebuildWorkspacePages(preserveCurrentPage: Boolean) {
        val prevPage = pager.activePageIndex
        val pages = mutableListOf<View>()
        homeGridPages.clear()

        currentHomePageOffset = if (prefs.todayViewEnabled) 1 else 0

        // 1. Optional Today View (leftmost page)
        if (prefs.todayViewEnabled) {
            val tv = IosTodayViewPage(this).apply {
                onOpenSettings = { openSettings() }
                onAddWidget = { showAddWidgetDialog() }
            }
            todayViewPage = tv
            pages.add(tv)
        } else {
            todayViewPage = null
        }

        // 2. Home Screen Grid Pages (0 .. pagesCount - 1)
        val count = workspace.pagesCount.coerceAtLeast(1)
        for (p in 0 until count) {
            val gridPage = createHomeGridPage(p)
            homeGridPages.add(gridPage)
            pages.add(gridPage)
        }

        // 3. Optional App Library (rightmost page)
        if (prefs.appLibraryEnabled) {
            val lib = IosAppLibraryPage(this).apply {
                onAppClick = { app, anchor ->
                    launchAppByComponent(app.packageName, app.className, app.label, anchor)
                }
                onAppLongClick = { app, anchor ->
                    IosDesign.hapticLongPress(anchor, prefs.hapticsEnabled)
                    showAppContextMenu(app.packageName, app.className, app.label, homeItem = null, dockSlot = null)
                }
                onOpenCategory = { title, apps ->
                    openCategoryFolderModal(title, apps)
                }
                onOpenHiddenFolder = {
                    openHiddenAppsWithBiometric()
                }
            }
            lib.bind(installedApps, prefs.hiddenPackages, usageScores, iconCache)
            appLibraryPage = lib
            pages.add(lib)
        } else {
            appLibraryPage = null
        }

        val targetPage = if (preserveCurrentPage) {
            prevPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        } else {
            currentHomePageOffset
        }
        pager.setPages(pages, initialPage = targetPage)
        searchPill.totalHomePages = count
        searchPill.showSearchLabel = prefs.showSearchPill

        bindDock()
        applySafeInsets(safeInsets)
    }

    private fun rebuildHomeGridOnly() {
        homeGridPages.forEach { gridPage ->
            populateGridPage(gridPage)
        }
    }

    private fun createHomeGridPage(pageIndex: Int): IosHomeGridPage {
        return IosHomeGridPage(this, pageIndex, prefs.gridCols, prefs.gridRows).also {
            populateGridPage(it)
        }
    }

    private fun populateGridPage(gridPage: IosHomeGridPage) {
        gridPage.removeAllViews()
        val pageItems = workspace.items.filter { it.page == gridPage.pageIndex }
        val isLarge = prefs.iconSizeMode == IconSizeMode.LARGE
        val isDarkFolder = prefs.iconThemeStyle != IconThemeStyle.LIGHT

        for (item in pageItems) {
            val view: View = when (item) {
                is HomeItem.AppShortcut -> IosAppIconView(this).apply {
                    itemId = item.id
                    packageNameRef = item.packageName
                    classNameRef = item.className
                    label = item.label
                    largeMode = isLarge
                    iconBitmap = iconCache.getIcon(item.packageName, item.className)
                    badgeCount = if (prefs.showBadges) BadgeStore.countForPackage(item.packageName) else 0
                    setJiggling(isJiggleMode)
                    setOnClickListener {
                        if (!isJiggleMode) {
                            launchAppByComponent(item.packageName, item.className, item.label, this)
                        }
                    }
                    setOnLongClickListener {
                        if (!isJiggleMode) {
                            IosDesign.hapticLongPress(this, prefs.hapticsEnabled)
                            showAppContextMenu(item.packageName, item.className, item.label, homeItem = item, dockSlot = null)
                            true
                        } else {
                            false
                        }
                    }
                    onRemoveBadgeClick = {
                        promptRemoveHomeItem(item)
                    }
                    attachJiggleDragTouchListener(this, item)
                }

                is HomeItem.FolderItem -> IosAppIconView(this).apply {
                    itemId = item.id
                    isFolder = true
                    label = item.title
                    largeMode = isLarge
                    darkGlassFolder = isDarkFolder
                    folderIcons = item.apps.take(9).map { iconCache.getIcon(it.packageName, it.className) }
                    badgeCount = if (prefs.showBadges) {
                        BadgeStore.countForPackages(item.apps.map { it.packageName })
                    } else 0
                    setJiggling(isJiggleMode)
                    setOnClickListener {
                        if (!isJiggleMode) {
                            setOverlayBlur(true)
                            folderOverlay.openFolder(item, iconCache, prefs.showBadges, isLarge, isJiggleMode)
                        }
                    }
                    setOnLongClickListener {
                        if (!isJiggleMode) {
                            IosDesign.hapticLongPress(this, prefs.hapticsEnabled)
                            enterJiggleMode()
                            true
                        } else {
                            false
                        }
                    }
                    onRemoveBadgeClick = { promptRemoveHomeItem(item) }
                    attachJiggleDragTouchListener(this, item)
                }

                is HomeItem.BuiltInWidgetItem -> IosBuiltInWidgetFactory.createView(
                    context = this,
                    item = item,
                    onEditNote = { noteItem -> promptEditQuickNote(noteItem) },
                    onOpenCalendar = { launchCategoryApp(listOf("calendar")) },
                    onOpenClock = { launchCategoryApp(listOf("deskclock", "clock", "alarm")) }
                ).apply {
                    setOnLongClickListener {
                        IosDesign.hapticLongPress(this, prefs.hapticsEnabled)
                        if (isJiggleMode) {
                            promptRemoveHomeItem(item)
                        } else {
                            showWidgetContextMenu(item)
                        }
                        true
                    }
                    attachJiggleDragTouchListener(this, item)
                }

                is HomeItem.AndroidWidgetItem -> TextView(this).apply {
                    text = "Widget"
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    background = IosDesign.createGlassDrawable(this@MainActivity, 22f)
                }
            }
            gridPage.addHomeItemView(view, item)
        }
    }

    private fun bindDock() {
        dockView.applyGlassStyle(prefs.iconThemeStyle, prefs.blurEnabled)
        dockView.bind(
            slots = workspace.dock,
            iconCache = iconCache,
            showBadges = prefs.showBadges,
            largeIcons = prefs.iconSizeMode == IconSizeMode.LARGE,
            jiggle = isJiggleMode
        )
    }

    // -------------------------------------------------------------------------
    // Jiggle Mode & Drag-and-Drop (Free Placement + Folder Creation + Page Flip)
    // -------------------------------------------------------------------------

    private fun enterJiggleMode() {
        if (isJiggleMode) return
        isJiggleMode = true
        editTopBar.visibility = View.VISIBLE
        homeGridPages.forEach { it.setJiggleMode(true) }
        dockView.setJiggleMode(true)
        ColorOsCompat.setWallpaperZoom(this, rootFrame, 0.25f)
    }

    private fun exitJiggleMode() {
        if (!isJiggleMode) return
        isJiggleMode = false
        editTopBar.visibility = View.GONE
        homeGridPages.forEach {
            it.setJiggleMode(false)
            it.setDropHighlight(null)
        }
        dockView.setJiggleMode(false)
        cancelActiveDrag()
        ColorOsCompat.setWallpaperZoom(this, rootFrame, 0f)
        repository.saveWorkspaceAsync(workspace)
    }

    private fun attachJiggleDragTouchListener(view: View, item: HomeItem) {
        view.setOnTouchListener { v, event ->
            if (!isJiggleMode) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    dragMoved = false
                    false // Allow (-) remove badge tap to still work if not dragged
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragStartX
                    val dy = event.rawY - dragStartY
                    if (!dragMoved && (abs(dx) > dragTouchSlop || abs(dy) > dragTouchSlop)) {
                        dragMoved = true
                        startDraggingItem(v, item, event.rawX, event.rawY)
                    }
                    if (dragMoved && dragItem == item) {
                        updateDraggingItem(event.rawX, event.rawY)
                        return@setOnTouchListener true
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragMoved && dragItem == item) {
                        finishDraggingItem(event.rawX, event.rawY)
                        dragMoved = false
                        return@setOnTouchListener true
                    }
                    false
                }
                else -> false
            }
        }
    }

    private fun startDraggingItem(sourceView: View, item: HomeItem, rawX: Float, rawY: Float) {
        dragItem = item
        pager.isDraggingItem = true
        sourceView.alpha = 0.25f
        IosDesign.hapticTick(sourceView, prefs.hapticsEnabled)

        val ghostSize = IosDesign.dpInt(this, if (item.spanX > 1) 110f else 72f)
        val ghost = ImageView(this).apply {
            scaleX = 1.12f
            scaleY = 1.12f
            alpha = 0.92f
            when (item) {
                is HomeItem.AppShortcut -> setImageBitmap(iconCache.getIcon(item.packageName, item.className))
                is HomeItem.FolderItem -> background = IosDesign.createGlassDrawable(this@MainActivity, 20f, 0x66FFFFFF)
                else -> background = IosDesign.createGlassDrawable(this@MainActivity, 22f, 0x66FFFFFF)
            }
        }
        val lp = FrameLayout.LayoutParams(ghostSize, ghostSize)
        ghost.x = rawX - ghostSize / 2f
        ghost.y = rawY - ghostSize / 2f
        dragGhostView = ghost
        rootFrame.addView(ghost, lp)
    }

    private fun updateDraggingItem(rawX: Float, rawY: Float) {
        val item = dragItem ?: return
        val ghost = dragGhostView ?: return
        ghost.x = rawX - ghost.width / 2f
        ghost.y = rawY - ghost.height / 2f

        val activeGrid = currentHomeGridPage()
        if (activeGrid != null) {
            val loc = IntArray(2)
            activeGrid.getLocationOnScreen(loc)
            val localX = rawX - loc[0]
            val localY = rawY - loc[1]
            val (col, row) = activeGrid.pixelToCell(localX, localY)
            val clampedCol = col.coerceIn(0, (prefs.gridCols - item.spanX).coerceAtLeast(0))
            val clampedRow = row.coerceIn(0, (prefs.gridRows - item.spanY).coerceAtLeast(0))

            val occupant = workspace.items.firstOrNull {
                it.page == activeGrid.pageIndex &&
                    it.id != item.id &&
                    clampedCol in it.col until (it.col + it.spanX) &&
                    clampedRow in it.row until (it.row + it.spanY)
            }
            val canFolderMerge = item is HomeItem.AppShortcut &&
                (occupant is HomeItem.AppShortcut || occupant is HomeItem.FolderItem)

            activeGrid.setDropHighlight(
                IosHomeGridPage.DropHighlight(
                    col = clampedCol,
                    row = clampedRow,
                    spanX = item.spanX,
                    spanY = item.spanY,
                    isFolderMerge = canFolderMerge
                )
            )
        }

        // Edge-hover automatic page flipping while dragging
        val edgeZone = IosDesign.dp(this, 36f)
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        if (rawX < edgeZone) {
            scheduleEdgePageFlip(-1)
        } else if (rawX > screenW - edgeZone) {
            scheduleEdgePageFlip(+1)
        } else {
            cancelEdgePageFlip()
        }
    }

    private fun finishDraggingItem(rawX: Float, rawY: Float) {
        val item = dragItem ?: return
        cancelEdgePageFlip()
        dragGhostView?.let { rootFrame.removeView(it) }
        dragGhostView = null
        dragItem = null
        pager.isDraggingItem = false
        homeGridPages.forEach { it.setDropHighlight(null) }

        // Check if dropped onto the Dock (for AppShortcut)
        val dockLoc = IntArray(2)
        dockView.getLocationOnScreen(dockLoc)
        val dockRect = RectF(
            dockLoc[0].toFloat(),
            dockLoc[1].toFloat(),
            (dockLoc[0] + dockView.width).toFloat(),
            (dockLoc[1] + dockView.height).toFloat()
        )
        if (item is HomeItem.AppShortcut && dockRect.contains(rawX, rawY)) {
            pinAppToDock(item.packageName, item.className, item.label)
            workspace.items.removeAll { it.id == item.id }
            repository.saveWorkspaceAsync(workspace)
            rebuildWorkspacePages(preserveCurrentPage = true)
            return
        }

        val activeGrid = currentHomeGridPage()
        if (activeGrid != null) {
            val loc = IntArray(2)
            activeGrid.getLocationOnScreen(loc)
            val (col, row) = activeGrid.pixelToCell(rawX - loc[0], rawY - loc[1])
            val targetCol = col.coerceIn(0, (prefs.gridCols - item.spanX).coerceAtLeast(0))
            val targetRow = row.coerceIn(0, (prefs.gridRows - item.spanY).coerceAtLeast(0))

            val occupant = workspace.items.firstOrNull {
                it.page == activeGrid.pageIndex &&
                    it.id != item.id &&
                    targetCol in it.col until (it.col + it.spanX) &&
                    targetRow in it.row until (it.row + it.spanY)
            }

            if (item is HomeItem.AppShortcut && occupant is HomeItem.AppShortcut) {
                // Stack two apps -> Create iOS Folder!
                val folder = HomeItem.FolderItem(
                    id = LauncherRepository.newId(),
                    page = activeGrid.pageIndex,
                    col = occupant.col,
                    row = occupant.row,
                    title = "Folder",
                    apps = mutableListOf(occupant, item)
                )
                workspace.items.removeAll { it.id == item.id || it.id == occupant.id }
                workspace.items.add(folder)
            } else if (item is HomeItem.AppShortcut && occupant is HomeItem.FolderItem) {
                // Drop app into existing folder!
                occupant.apps.add(item)
                workspace.items.removeAll { it.id == item.id }
            } else {
                // Free Placement if target cells are open!
                val occ = GridOccupancy.forPage(activeGrid.pageIndex, prefs.gridCols, prefs.gridRows, workspace.items)
                if (occ.canPlace(targetCol, targetRow, item.spanX, item.spanY, ignoreItemId = item.id)) {
                    item.page = activeGrid.pageIndex
                    item.col = targetCol
                    item.row = targetRow
                }
            }
            workspace.pagesCount = ((workspace.items.maxOfOrNull { it.page } ?: 0) + 1).coerceAtLeast(1)
            repository.saveWorkspaceAsync(workspace)
        }
        rebuildWorkspacePages(preserveCurrentPage = true)
    }

    private fun cancelActiveDrag() {
        cancelEdgePageFlip()
        dragGhostView?.let { rootFrame.removeView(it) }
        dragGhostView = null
        dragItem = null
        pager.isDraggingItem = false
    }

    private fun scheduleEdgePageFlip(direction: Int) {
        if (edgeFlipRunnable != null) return
        val r = Runnable {
            edgeFlipRunnable = null
            val currentIdx = pager.activePageIndex
            val firstHomeIdx = currentHomePageOffset
            val lastHomeIdx = currentHomePageOffset + homeGridPages.size - 1

            if (direction < 0 && currentIdx > firstHomeIdx) {
                pager.scrollToPage(currentIdx - 1, animate = true)
            } else if (direction > 0) {
                if (currentIdx < lastHomeIdx) {
                    pager.scrollToPage(currentIdx + 1, animate = true)
                } else if (homeGridPages.size < 8) {
                    // Add a new blank Home page when dragging past rightmost page!
                    workspace.pagesCount = homeGridPages.size + 1
                    rebuildWorkspacePages(preserveCurrentPage = true)
                    pager.scrollToPage(currentHomePageOffset + workspace.pagesCount - 1, animate = true)
                }
            }
        }
        edgeFlipRunnable = r
        rootFrame.postDelayed(r, 520L)
    }

    private fun cancelEdgePageFlip() {
        edgeFlipRunnable?.let { rootFrame.removeCallbacks(it) }
        edgeFlipRunnable = null
    }

    private fun currentHomeGridPage(): IosHomeGridPage? {
        val homeIdx = pager.activePageIndex - currentHomePageOffset
        return homeGridPages.getOrNull(homeIdx)
    }

    // -------------------------------------------------------------------------
    // App Launching, Biometric App Lock & Hidden Apps Vault
    // -------------------------------------------------------------------------

    private fun launchAppByComponent(
        packageName: String,
        className: String,
        label: String,
        anchorView: View
    ) {
        if (packageName in prefs.lockedPackages) {
            BiometricAuthHelper.authenticate(
                activity = this,
                title = "Unlock $label",
                subtitle = "Verify identity to open locked app",
                onSuccess = { actuallyLaunchApp(packageName, className, anchorView) },
                onFailure = { reason ->
                    Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            actuallyLaunchApp(packageName, className, anchorView)
        }
    }

    private fun actuallyLaunchApp(packageName: String, className: String, anchorView: View) {
        IosDesign.hapticTick(anchorView, prefs.hapticsEnabled)
        val key = "$packageName/$className"
        repository.recordAppLaunch(key)
        usageScores = usageScores.toMutableMap().apply { this[key] = (this[key] ?: 0L) + 1L }

        val opts = ActivityOptions.makeClipRevealAnimation(
            anchorView, 0, 0, anchorView.width.coerceAtLeast(1), anchorView.height.coerceAtLeast(1)
        ).toBundle()

        try {
            val la = launcherApps
            if (la != null) {
                la.startMainActivity(
                    ComponentName(packageName, className),
                    Process.myUserHandle(),
                    null,
                    opts
                )
            } else {
                val fallback = packageManager.getLaunchIntentForPackage(packageName)
                if (fallback != null) {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(fallback, opts)
                } else {
                    Toast.makeText(this, "Unable to launch app", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {
            val fallback = packageManager.getLaunchIntentForPackage(packageName)
            if (fallback != null) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(fallback, opts)
            } else {
                Toast.makeText(this, "Unable to launch app", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchCategoryApp(keywords: List<String>) {
        val match = installedApps.firstOrNull { app ->
            keywords.any { k -> app.packageName.lowercase().contains(k) || app.label.lowercase().contains(k) }
        } ?: return
        launchAppByComponent(match.packageName, match.className, match.label, rootFrame)
    }

    private fun openHiddenAppsWithBiometric() {
        BiometricAuthHelper.authenticate(
            activity = this,
            title = "Hidden Apps",
            subtitle = "Authenticate to view hidden apps",
            onSuccess = {
                val hiddenApps = installedApps.filter { it.packageName in prefs.hiddenPackages }
                if (hiddenApps.isEmpty()) {
                    Toast.makeText(this, "No hidden apps. Long-press any app -> Hide App.", Toast.LENGTH_SHORT).show()
                } else {
                    openCategoryFolderModal("Hidden Apps", hiddenApps)
                }
            },
            onFailure = { reason ->
                Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun openCategoryFolderModal(title: String, apps: List<AppEntry>) {
        val tempFolder = HomeItem.FolderItem(
            id = "cat_$title",
            page = 0,
            col = 0,
            row = 0,
            title = title,
            apps = apps.map {
                HomeItem.AppShortcut(
                    id = it.componentKey,
                    page = 0,
                    col = 0,
                    row = 0,
                    packageName = it.packageName,
                    className = it.className,
                    label = it.label
                )
            }.toMutableList()
        )
        setOverlayBlur(true)
        folderOverlay.openFolder(
            folder = tempFolder,
            iconCache = iconCache,
            showBadges = prefs.showBadges,
            largeIcons = prefs.iconSizeMode == IconSizeMode.LARGE,
            jiggleMode = false
        )
    }

    // -------------------------------------------------------------------------
    // Context Menus, Spotlight, Widgets & Dialogs
    // -------------------------------------------------------------------------

    private fun showAppContextMenu(
        packageName: String,
        className: String,
        label: String,
        homeItem: HomeItem.AppShortcut?,
        dockSlot: DockSlot?
    ) {
        val isLocked = packageName in prefs.lockedPackages
        val isHidden = packageName in prefs.hiddenPackages
        val inDock = workspace.dock.any { it.packageName == packageName }

        val actions = mutableListOf<IosContextMenuOverlay.MenuAction>()

        actions.add(IosContextMenuOverlay.MenuAction("Edit Home Screen", "⊞") {
            enterJiggleMode()
        })

        if (dockSlot == null && !inDock) {
            actions.add(IosContextMenuOverlay.MenuAction("Pin to Dock", "⬇") {
                pinAppToDock(packageName, className, label)
            })
        } else if (dockSlot != null) {
            actions.add(IosContextMenuOverlay.MenuAction("Remove from Dock", "✕") {
                workspace.dock.removeAll { it.componentKey == dockSlot.componentKey }
                repository.saveWorkspaceAsync(workspace)
                bindDock()
            })
        }

        actions.add(
            IosContextMenuOverlay.MenuAction(
                title = if (isLocked) "Disable App Lock" else "Require Biometric Lock",
                iconGlyph = "🔐"
            ) {
                toggleAppLock(packageName)
            }
        )

        actions.add(
            IosContextMenuOverlay.MenuAction(
                title = if (isHidden) "Unhide App" else "Hide App",
                iconGlyph = "👁"
            ) {
                toggleHideApp(packageName)
            }
        )

        actions.add(IosContextMenuOverlay.MenuAction("App Info", "ⓘ") {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            runCatching { startActivity(intent) }
        })

        if (homeItem != null) {
            actions.add(IosContextMenuOverlay.MenuAction("Remove from Home", "−", isDestructive = true) {
                workspace.items.removeAll { it.id == homeItem.id }
                repository.saveWorkspaceAsync(workspace)
                rebuildWorkspacePages(preserveCurrentPage = true)
            })
        }

        actions.add(IosContextMenuOverlay.MenuAction("Uninstall App", "🗑", isDestructive = true) {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            runCatching { startActivity(intent) }
        })

        setOverlayBlur(true)
        contextMenuOverlay.show(label, actions)
    }

    private fun showWidgetContextMenu(item: HomeItem.BuiltInWidgetItem) {
        val actions = mutableListOf(
            IosContextMenuOverlay.MenuAction("Edit Home Screen", "⊞") { enterJiggleMode() },
            IosContextMenuOverlay.MenuAction("Add Another Widget", "+") { showAddWidgetDialog() },
            IosContextMenuOverlay.MenuAction("Remove Widget", "−", isDestructive = true) {
                workspace.items.removeAll { it.id == item.id }
                repository.saveWorkspaceAsync(workspace)
                rebuildWorkspacePages(preserveCurrentPage = true)
            }
        )
        setOverlayBlur(true)
        contextMenuOverlay.show(item.widgetType.title, actions)
    }

    private fun showEditMenuDialog() {
        val items = arrayOf(
            "Add Built-in iOS Widget",
            "Customize Icons (Light / Dark / Tinted / Large)",
            "Open iOSpring Settings"
        )
        AlertDialog.Builder(this)
            .setTitle("Edit Home Screen")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showAddWidgetDialog()
                    1 -> showQuickIconCustomizeDialog()
                    2 -> openSettings()
                }
            }
            .show()
    }

    private fun showAddWidgetDialog() {
        val types = BuiltInWidgetType.entries
        val labels = types.map { "${it.title} (${it.defaultSpanX}×${it.defaultSpanY}) — ${it.subtitle}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Add iOS Widget")
            .setItems(labels) { _, which ->
                addBuiltInWidgetToWorkspace(types[which])
            }
            .show()
    }

    private fun addBuiltInWidgetToWorkspace(type: BuiltInWidgetType) {
        val startPage = (pager.activePageIndex - currentHomePageOffset).coerceAtLeast(0)
        var targetPage = startPage
        var placement: Pair<Int, Int>? = null

        for (p in startPage until (workspace.pagesCount + 1)) {
            val occ = GridOccupancy.forPage(p, prefs.gridCols, prefs.gridRows, workspace.items)
            val slot = occ.findFirstVacant(type.defaultSpanX, type.defaultSpanY)
            if (slot != null) {
                targetPage = p
                placement = slot
                break
            }
        }
        val (col, row) = placement ?: Pair(0, 0)
        workspace.items.add(
            HomeItem.BuiltInWidgetItem(
                id = LauncherRepository.newId(),
                page = targetPage,
                col = col,
                row = row,
                spanX = type.defaultSpanX,
                spanY = type.defaultSpanY,
                widgetType = type
            )
        )
        workspace.pagesCount = maxOf(workspace.pagesCount, targetPage + 1)
        repository.saveWorkspaceAsync(workspace)
        rebuildWorkspacePages(preserveCurrentPage = false)
        pager.scrollToPage(currentHomePageOffset + targetPage, animate = true)
    }

    private fun showQuickIconCustomizeDialog() {
        val options = arrayOf(
            "Light Icons (Default)",
            "Dark Mode Icons (iOS 18)",
            "Tinted Icons (iOS 18 Blue)",
            "Toggle Large Icons (Hide Labels)"
        )
        AlertDialog.Builder(this)
            .setTitle("iOS 18 Icon Style")
            .setItems(options) { _, which ->
                prefs = when (which) {
                    0 -> prefs.copy(iconThemeStyle = IconThemeStyle.LIGHT)
                    1 -> prefs.copy(iconThemeStyle = IconThemeStyle.DARK)
                    2 -> prefs.copy(iconThemeStyle = IconThemeStyle.TINTED, iconTintColor = IosDesign.IOS_BLUE)
                    else -> prefs.copy(
                        iconSizeMode = if (prefs.iconSizeMode == IconSizeMode.LARGE) IconSizeMode.STANDARD else IconSizeMode.LARGE
                    )
                }
                repository.savePreferences(prefs)
                iconCache.updateTheme(prefs.iconThemeStyle, prefs.iconTintColor)
                rebuildWorkspacePages(preserveCurrentPage = true)
            }
            .show()
    }

    private fun promptRemoveHomeItem(item: HomeItem) {
        val label = when (item) {
            is HomeItem.AppShortcut -> item.label
            is HomeItem.FolderItem -> item.title
            is HomeItem.BuiltInWidgetItem -> item.widgetType.title
            is HomeItem.AndroidWidgetItem -> "Widget"
        }
        AlertDialog.Builder(this)
            .setTitle("Remove \"$label\"?")
            .setMessage("Removing from the Home Screen keeps the app inside your App Library.")
            .setPositiveButton("Remove") { _, _ ->
                workspace.items.removeAll { it.id == item.id }
                repository.saveWorkspaceAsync(workspace)
                rebuildWorkspacePages(preserveCurrentPage = true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptEditQuickNote(item: HomeItem.BuiltInWidgetItem) {
        val edit = EditText(this).apply {
            setText(item.customNote)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Edit Quick Note")
            .setView(edit)
            .setPositiveButton("Save") { _, _ ->
                item.customNote = edit.text.toString().ifBlank { "Tap to add a quick note..." }
                repository.saveWorkspaceAsync(workspace)
                rebuildHomeGridOnly()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pinAppToDock(packageName: String, className: String, label: String) {
        if (workspace.dock.any { it.packageName == packageName }) return
        if (workspace.dock.size >= 4) {
            workspace.dock.removeAt(workspace.dock.lastIndex)
        }
        workspace.dock.add(DockSlot(workspace.dock.size, packageName, className, label))
        repository.saveWorkspaceAsync(workspace)
        bindDock()
    }

    private fun toggleHideApp(packageName: String) {
        val updated = prefs.hiddenPackages.toMutableSet()
        if (packageName in updated) updated.remove(packageName) else updated.add(packageName)
        prefs = prefs.copy(hiddenPackages = updated)
        repository.savePreferences(prefs)
        repository.reconcileWithInstalledApps(workspace, installedApps, prefs)
        repository.saveWorkspaceAsync(workspace)
        rebuildWorkspacePages(preserveCurrentPage = true)
    }

    private fun toggleAppLock(packageName: String) {
        val updated = prefs.lockedPackages.toMutableSet()
        if (packageName in updated) updated.remove(packageName) else updated.add(packageName)
        prefs = prefs.copy(lockedPackages = updated)
        repository.savePreferences(prefs)
        val msg = if (packageName in updated) "Biometric App Lock enabled" else "App Lock disabled"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun openSpotlight() {
        setOverlayBlur(true)
        spotlightOverlay.open(installedApps, prefs.hiddenPackages, usageScores, iconCache)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // -------------------------------------------------------------------------
    // Navigation, Overlay Blur, Badges & Live Widget Ticks
    // -------------------------------------------------------------------------

    private fun handleBackOrHome(isHomeGesture: Boolean) {
        when {
            contextMenuOverlay.isOpen -> contextMenuOverlay.dismiss()
            spotlightOverlay.isOpen -> spotlightOverlay.dismiss()
            folderOverlay.isOpen -> folderOverlay.dismiss()
            isJiggleMode -> exitJiggleMode()
            pager.activePageIndex != currentHomePageOffset -> {
                pager.scrollToPage(currentHomePageOffset, animate = true)
            }
            else -> {
                // Already on main Home Page 0 — no-op (never finish Home launcher!)
                if (isHomeGesture) {
                    searchPill.showPageDotsTemporarily(0)
                }
            }
        }
    }

    private fun handlePageSettled(pageIdx: Int) {
        val homeIdx = pageIdx - currentHomePageOffset
        if (homeIdx in 0 until homeGridPages.size) {
            searchPill.showPageDotsTemporarily(homeIdx)
        }
    }

    private fun updateDockAndPillVisibility(scrollPx: Float, pageW: Int) {
        if (pageW <= 0) return
        val firstHomePx = currentHomePageOffset * pageW.toFloat()
        val lastHomePx = (currentHomePageOffset + homeGridPages.size - 1) * pageW.toFloat()

        // Smoothly fade Dock & Search Pill out when sliding into Today View or App Library (just like iOS!)
        val alphaFactor = when {
            scrollPx < firstHomePx -> (1f - ((firstHomePx - scrollPx) / pageW)).coerceIn(0f, 1f)
            scrollPx > lastHomePx -> (1f - ((scrollPx - lastHomePx) / pageW)).coerceIn(0f, 1f)
            else -> 1f
        }
        dockView.alpha = alphaFactor
        searchPill.alpha = alphaFactor
        dockView.isClickable = alphaFactor > 0.5f
        searchPill.isClickable = alphaFactor > 0.5f
    }

    private fun setOverlayBlur(active: Boolean) {
        ColorOsCompat.applyContentBlur(workspaceBlurContainer, if (active) 28f else 0f, prefs.blurEnabled)
        ColorOsCompat.setWallpaperZoom(this, rootFrame, if (active) 0.45f else 0f)
    }

    private fun refreshBadgesInPlace() {
        dockView.refreshBadges(prefs.showBadges)
        for (page in homeGridPages) {
            for (i in 0 until page.childCount) {
                val icon = page.getChildAt(i) as? IosAppIconView ?: continue
                val tagItem = icon.tag
                icon.badgeCount = if (!prefs.showBadges) {
                    0
                } else when (tagItem) {
                    is HomeItem.AppShortcut -> BadgeStore.countForPackage(tagItem.packageName)
                    is HomeItem.FolderItem -> BadgeStore.countForPackages(tagItem.apps.map { it.packageName })
                    else -> 0
                }
            }
        }
    }

    private fun refreshVisibleWidgets() {
        todayViewPage?.refreshWidgets()
        for (page in homeGridPages) {
            for (i in 0 until page.childCount) {
                val wrapper = page.getChildAt(i) as? FrameLayout ?: continue
                val inner = wrapper.getChildAt(0)
                if (inner is RefreshableIosWidget) {
                    inner.refreshWidgetData()
                }
            }
        }
    }

    private fun registerLiveTickReceiver() {
        if (liveTickReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(liveTickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(liveTickReceiver, filter)
        }
        liveTickReceiverRegistered = true
    }

    private fun unregisterLiveTickReceiver() {
        if (!liveTickReceiverRegistered) return
        try {
            unregisterReceiver(liveTickReceiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered
        }
        liveTickReceiverRegistered = false
    }
}
