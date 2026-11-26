package com.iospring.launcher.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.Color
import android.os.Process
import com.iospring.launcher.core.AppCategorizer
import com.iospring.launcher.model.AppEntry
import com.iospring.launcher.model.BuiltInWidgetType
import com.iospring.launcher.model.DockSlot
import com.iospring.launcher.model.GlassThemeStyle
import com.iospring.launcher.model.HomeItem
import com.iospring.launcher.model.IconSizeMode
import com.iospring.launcher.model.IconThemeStyle
import com.iospring.launcher.model.LauncherPreferences
import com.iospring.launcher.model.LauncherWorkspaceState
import com.iospring.launcher.model.LockClockStyle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Ultra-lightweight JSON & SharedPreferences repository.
 * Zero external serialization libraries; atomic file writes on a single background thread.
 */
class LauncherRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val usagePrefs = context.getSharedPreferences(USAGE_PREFS_NAME, Context.MODE_PRIVATE)
    private val layoutFile = File(context.filesDir, LAYOUT_FILENAME)
    private val ioExecutor = Executors.newSingleThreadExecutor()

    fun loadInstalledApps(): List<AppEntry> {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val selfPkg = context.packageName

        val list = try {
            launcherApps?.getActivityList(null, Process.myUserHandle()).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        val entries = ArrayList<AppEntry>(list.size)
        for (info in list) {
            val pkg = info.componentName.packageName
            if (pkg == selfPkg) continue
            val appInfo = info.applicationInfo
            val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val label = info.label?.toString()?.trim().orEmpty().ifEmpty { pkg }
            // Direct LauncherActivityInfo.firstInstallTime avoids 100+ extra PackageManager IPC calls!
            val installTime = info.firstInstallTime
            val category = AppCategorizer.classify(pkg, label, appInfo.category)
            entries.add(
                AppEntry(
                    packageName = pkg,
                    className = info.componentName.className,
                    label = label,
                    category = category,
                    isSystemApp = isSys,
                    firstInstallTime = installTime
                )
            )
        }
        entries.sortBy { it.lowerLabel }
        return entries
    }

    fun loadAppForPackage(packageName: String): List<AppEntry> {
        if (packageName == context.packageName) return emptyList()
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val list = try {
            launcherApps?.getActivityList(packageName, Process.myUserHandle()).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        return list.map { info ->
            val appInfo = info.applicationInfo
            val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val label = info.label?.toString()?.trim().orEmpty().ifEmpty { packageName }
            AppEntry(
                packageName = packageName,
                className = info.componentName.className,
                label = label,
                category = AppCategorizer.classify(packageName, label, appInfo.category),
                isSystemApp = isSys,
                firstInstallTime = info.firstInstallTime
            )
        }
    }

    fun loadPreferences(): LauncherPreferences {
        val styleOrd = prefs.getInt(KEY_ICON_STYLE, IconThemeStyle.DARK.ordinal)
        val glassStyle = prefs.getString(KEY_GLASS_STYLE_NAME, null)?.let { savedName ->
            runCatching { GlassThemeStyle.valueOf(savedName) }.getOrNull()
        } ?: when (prefs.getInt(KEY_GLASS_STYLE, 0)) {
            1 -> GlassThemeStyle.FROSTED_BLUR
            2 -> GlassThemeStyle.OBSIDIAN_GLASS
            else -> GlassThemeStyle.LIQUID_GLASS
        }
        val sizeOrd = prefs.getInt(KEY_ICON_SIZE, IconSizeMode.LARGE.ordinal)
        val lockClockOrd = prefs.getInt(KEY_LOCK_CLOCK_STYLE, LockClockStyle.LIQUID_GLASS.ordinal)
        val hiddenSet = prefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet())?.toSet() ?: emptySet()
        val lockedSet = prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet())?.toSet() ?: emptySet()

        return LauncherPreferences(
            gridCols = prefs.getInt(KEY_GRID_COLS, 4).coerceIn(4, 5),
            gridRows = prefs.getInt(KEY_GRID_ROWS, 6).coerceIn(5, 7),
            iconThemeStyle = IconThemeStyle.entries.getOrElse(styleOrd) { IconThemeStyle.DARK },
            glassThemeStyle = glassStyle,
            iconTintColor = prefs.getInt(KEY_ICON_TINT, Color.parseColor("#0A84FF")),
            iconSizeMode = IconSizeMode.entries.getOrElse(sizeOrd) { IconSizeMode.LARGE },
            showAppLabels = prefs.getBoolean(KEY_SHOW_APP_LABELS, true),
            iosIconPackEnabled = prefs.getBoolean(KEY_IOS_ICON_PACK, true),
            keepInRamEnabled = prefs.getBoolean(KEY_KEEP_IN_RAM, true),
            showSearchPill = prefs.getBoolean(KEY_SEARCH_PILL, true),
            showBadges = prefs.getBoolean(KEY_SHOW_BADGES, true),
            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true),
            blurEnabled = prefs.getBoolean(KEY_BLUR, true),
            swipeDownSpotlight = prefs.getBoolean(KEY_SWIPE_SPOTLIGHT, true),
            todayViewEnabled = prefs.getBoolean(KEY_TODAY_VIEW, true),
            appLibraryEnabled = prefs.getBoolean(KEY_APP_LIBRARY, true),
            autoArrangeIcons = prefs.getBoolean(KEY_AUTO_ARRANGE, true),
            cleanModeEnabled = prefs.getBoolean(KEY_CLEAN_MODE, false),
            doubleTapToLockEnabled = prefs.getBoolean(KEY_DOUBLE_TAP_LOCK, true),
            iosLockScreenEnabled = prefs.getBoolean(KEY_IOS_LOCK_SCREEN, false),
            lockScreenClockStyle = LockClockStyle.entries.getOrElse(lockClockOrd) { LockClockStyle.LIQUID_GLASS },
            lockScreenShowNotifications = prefs.getBoolean(KEY_LOCK_NOTIFICATIONS, true),
            colorOsGestureSafeZone = prefs.getBoolean(KEY_COLOROS_SAFE, true),
            hiddenPackages = hiddenSet,
            lockedPackages = lockedSet
        )
    }

    fun savePreferences(p: LauncherPreferences) {
        prefs.edit()
            .putInt(KEY_GRID_COLS, p.gridCols)
            .putInt(KEY_GRID_ROWS, p.gridRows)
            .putInt(KEY_ICON_STYLE, p.iconThemeStyle.ordinal)
            .putInt(KEY_GLASS_STYLE, p.glassThemeStyle.ordinal)
            .putString(KEY_GLASS_STYLE_NAME, p.glassThemeStyle.name)
            .putInt(KEY_ICON_TINT, p.iconTintColor)
            .putInt(KEY_ICON_SIZE, p.iconSizeMode.ordinal)
            .putBoolean(KEY_SHOW_APP_LABELS, p.showAppLabels)
            .putBoolean(KEY_IOS_ICON_PACK, p.iosIconPackEnabled)
            .putBoolean(KEY_KEEP_IN_RAM, p.keepInRamEnabled)
            .putBoolean(KEY_SEARCH_PILL, p.showSearchPill)
            .putBoolean(KEY_SHOW_BADGES, p.showBadges)
            .putBoolean(KEY_HAPTICS, p.hapticsEnabled)
            .putBoolean(KEY_BLUR, p.blurEnabled)
            .putBoolean(KEY_SWIPE_SPOTLIGHT, p.swipeDownSpotlight)
            .putBoolean(KEY_TODAY_VIEW, p.todayViewEnabled)
            .putBoolean(KEY_APP_LIBRARY, p.appLibraryEnabled)
            .putBoolean(KEY_AUTO_ARRANGE, p.autoArrangeIcons)
            .putBoolean(KEY_CLEAN_MODE, p.cleanModeEnabled)
            .putBoolean(KEY_DOUBLE_TAP_LOCK, p.doubleTapToLockEnabled)
            .putBoolean(KEY_IOS_LOCK_SCREEN, p.iosLockScreenEnabled)
            .putInt(KEY_LOCK_CLOCK_STYLE, p.lockScreenClockStyle.ordinal)
            .putBoolean(KEY_LOCK_NOTIFICATIONS, p.lockScreenShowNotifications)
            .putBoolean(KEY_COLOROS_SAFE, p.colorOsGestureSafeZone)
            .putStringSet(KEY_HIDDEN_PACKAGES, p.hiddenPackages)
            .putStringSet(KEY_LOCKED_PACKAGES, p.lockedPackages)
            .apply()
    }

    fun close() {
        ioExecutor.shutdown()
    }

    fun recordAppLaunch(componentKey: String) {
        val current = usagePrefs.getLong(componentKey, 0L)
        usagePrefs.edit().putLong(componentKey, current + 1L).apply()
    }

    fun loadUsageScores(): Map<String, Long> {
        return usagePrefs.all.mapNotNull { (k, v) ->
            (v as? Long)?.let { k to it }
        }.toMap()
    }

    fun loadOrCreateWorkspace(
        installedApps: List<AppEntry>,
        prefs: LauncherPreferences
    ): LauncherWorkspaceState {
        val loaded = readWorkspaceJson()
        if (loaded != null && (loaded.items.isNotEmpty() || loaded.dock.isNotEmpty())) {
            reconcileWithInstalledApps(loaded, installedApps, prefs)
            return loaded
        }
        val fresh = buildDefaultIosWorkspace(installedApps, prefs)
        saveWorkspaceAsync(fresh)
        return fresh
    }

    fun resetWorkspace(installedApps: List<AppEntry>, prefs: LauncherPreferences): LauncherWorkspaceState {
        val fresh = buildDefaultIosWorkspace(installedApps, prefs)
        saveWorkspaceAsync(fresh)
        return fresh
    }

    fun buildDefaultIosWorkspace(
        installedApps: List<AppEntry>,
        prefs: LauncherPreferences
    ): LauncherWorkspaceState {
        val state = LauncherWorkspaceState(pagesCount = 1)
        val visible = installedApps.filterNot { it.packageName in prefs.hiddenPackages }

        // 1. Auto-select 4 iconic iOS Dock apps: Phone, Messages/WhatsApp, Browser, Camera
        val dockKeywords = listOf(
            listOf("dialer", "phone", "contacts"),
            listOf("whatsapp", "messaging", "mms", "telegram", "messages"),
            listOf("chrome", "browser", "brave", "firefox", "opera"),
            listOf("camera")
        )
        val pickedKeys = mutableSetOf<String>()
        dockKeywords.forEachIndexed { idx, keys ->
            val found = visible.firstOrNull { app ->
                app.componentKey !in pickedKeys &&
                    keys.any { k -> app.packageName.lowercase().contains(k) || app.label.lowercase().contains(k) }
            }
            if (found != null) {
                pickedKeys.add(found.componentKey)
                state.dock.add(DockSlot(idx, found.packageName, found.className, found.label))
            }
        }
        // Fill remaining dock slots up to 4 if needed
        for (app in visible) {
            if (state.dock.size >= 4) break
            if (app.componentKey !in pickedKeys) {
                pickedKeys.add(app.componentKey)
                state.dock.add(DockSlot(state.dock.size, app.packageName, app.className, app.label))
            }
        }

        // 2. Signature iOS 18 top widgets on Page 0: Clock (2x2) + Calendar (2x2)
        state.items.add(
            HomeItem.BuiltInWidgetItem(
                id = newId(),
                page = 0,
                col = 0,
                row = 0,
                spanX = 2,
                spanY = 2,
                widgetType = BuiltInWidgetType.CLOCK_2X2
            )
        )
        if (prefs.gridCols >= 4) {
            state.items.add(
                HomeItem.BuiltInWidgetItem(
                    id = newId(),
                    page = 0,
                    col = 2,
                    row = 0,
                    spanX = 2,
                    spanY = 2,
                    widgetType = BuiltInWidgetType.CALENDAR_2X2
                )
            )
        }

        // 3. Place remaining apps cleanly across pages (cap initial pages to keep Home clean like iOS)
        val remaining = visible.filterNot { it.componentKey in pickedKeys }
        var page = 0
        for (app in remaining) {
            var placed = false
            while (!placed) {
                val occ = GridOccupancy.forPage(page, prefs.gridCols, prefs.gridRows, state.items)
                val slot = occ.findFirstVacant(1, 1)
                if (slot != null) {
                    state.items.add(
                        HomeItem.AppShortcut(
                            id = newId(),
                            page = page,
                            col = slot.first,
                            row = slot.second,
                            packageName = app.packageName,
                            className = app.className,
                            label = app.label
                        )
                    )
                    placed = true
                } else {
                    page++
                }
            }
            if (page >= 5) break // Additional apps live cleanly in App Library
        }
        state.pagesCount = (state.items.maxOfOrNull { it.page } ?: 0) + 1
        return state
    }

    fun reconcileWithInstalledApps(
        state: LauncherWorkspaceState,
        installedApps: List<AppEntry>,
        prefs: LauncherPreferences
    ) {
        val byKey = installedApps.associateBy { it.componentKey }
        val hidden = prefs.hiddenPackages

        // Clean uninstalled or hidden dock items
        state.dock.removeAll { it.componentKey !in byKey || it.packageName in hidden }

        // Clean uninstalled or hidden items from Home & Folders
        val toRemove = mutableListOf<HomeItem>()
        for (item in state.items) {
            when (item) {
                is HomeItem.AppShortcut -> {
                    val live = byKey[item.componentKey]
                    if (live == null || item.packageName in hidden) {
                        toRemove.add(item)
                    } else {
                        item.label = live.label
                    }
                }
                is HomeItem.FolderItem -> {
                    item.apps.removeAll { it.componentKey !in byKey || it.packageName in hidden }
                    item.apps.forEach { child -> byKey[child.componentKey]?.let { child.label = it.label } }
                    if (item.apps.isEmpty()) toRemove.add(item)
                }
                else -> Unit
            }
        }
        state.items.removeAll(toRemove.toSet())
        // Re-index dock slots cleanly after removals
        if (state.dock.isNotEmpty()) {
            val reindexed = state.dock.mapIndexed { idx, slot ->
                if (slot.index == idx) slot else slot.copy(index = idx)
            }
            state.dock.clear()
            state.dock.addAll(reindexed)
        }
        if (prefs.autoArrangeIcons) {
            GridOccupancy.compactWorkspace(state, prefs.gridCols, prefs.gridRows)
        } else {
            state.pagesCount = ((state.items.maxOfOrNull { it.page } ?: 0) + 1).coerceAtLeast(1)
        }
    }

    fun saveWorkspaceAsync(state: LauncherWorkspaceState) {
        val jsonStr = serializeWorkspace(state)
        ioExecutor.execute {
            try {
                val tmp = File(context.filesDir, "$LAYOUT_FILENAME.tmp")
                tmp.writeText(jsonStr, Charsets.UTF_8)
                if (!tmp.renameTo(layoutFile)) {
                    layoutFile.writeText(jsonStr, Charsets.UTF_8)
                    tmp.delete()
                }
            } catch (_: Exception) {
                // Ignore transient I/O errors
            }
        }
    }

    private fun readWorkspaceJson(): LauncherWorkspaceState? {
        if (!layoutFile.exists()) return null
        return try {
            deserializeWorkspace(layoutFile.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "iospring_prefs"
        private const val USAGE_PREFS_NAME = "iospring_usage"
        private const val LAYOUT_FILENAME = "iospring_layout_v1.json"

        private const val KEY_GRID_COLS = "grid_cols"
        private const val KEY_GRID_ROWS = "grid_rows"
        private const val KEY_ICON_STYLE = "icon_style"
        private const val KEY_GLASS_STYLE = "glass_style"
        private const val KEY_GLASS_STYLE_NAME = "glass_style_name"
        private const val KEY_ICON_TINT = "icon_tint"
        private const val KEY_ICON_SIZE = "icon_size"
        private const val KEY_SHOW_APP_LABELS = "show_app_labels"
        private const val KEY_IOS_ICON_PACK = "ios_icon_pack"
        private const val KEY_KEEP_IN_RAM = "keep_in_ram"
        private const val KEY_SEARCH_PILL = "search_pill"
        private const val KEY_SHOW_BADGES = "show_badges"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_BLUR = "blur"
        private const val KEY_SWIPE_SPOTLIGHT = "swipe_spotlight"
        private const val KEY_TODAY_VIEW = "today_view"
        private const val KEY_APP_LIBRARY = "app_library"
        private const val KEY_AUTO_ARRANGE = "auto_arrange_icons"
        private const val KEY_CLEAN_MODE = "clean_mode"
        private const val KEY_DOUBLE_TAP_LOCK = "double_tap_lock"
        private const val KEY_IOS_LOCK_SCREEN = "ios_lock_screen"
        private const val KEY_LOCK_CLOCK_STYLE = "lock_clock_style"
        private const val KEY_LOCK_NOTIFICATIONS = "lock_notifications"
        private const val KEY_COLOROS_SAFE = "coloros_safe"
        private const val KEY_HIDDEN_PACKAGES = "hidden_packages"
        private const val KEY_LOCKED_PACKAGES = "locked_packages"

        fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

        fun serializeWorkspace(state: LauncherWorkspaceState): String {
            val root = JSONObject()
            root.put("pagesCount", state.pagesCount)

            val dockArr = JSONArray()
            state.dock.forEach { d ->
                dockArr.put(
                    JSONObject().apply {
                        put("index", d.index)
                        put("pkg", d.packageName)
                        put("cls", d.className)
                        put("label", d.label)
                    }
                )
            }
            root.put("dock", dockArr)

            val itemsArr = JSONArray()
            state.items.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("page", item.page)
                    put("col", item.col)
                    put("row", item.row)
                    put("spanX", item.spanX)
                    put("spanY", item.spanY)
                }
                when (item) {
                    is HomeItem.AppShortcut -> {
                        obj.put("type", "app")
                        obj.put("pkg", item.packageName)
                        obj.put("cls", item.className)
                        obj.put("label", item.label)
                    }
                    is HomeItem.FolderItem -> {
                        obj.put("type", "folder")
                        obj.put("title", item.title)
                        val children = JSONArray()
                        item.apps.forEach { child ->
                            children.put(
                                JSONObject().apply {
                                    put("id", child.id)
                                    put("pkg", child.packageName)
                                    put("cls", child.className)
                                    put("label", child.label)
                                }
                            )
                        }
                        obj.put("apps", children)
                    }
                    is HomeItem.BuiltInWidgetItem -> {
                        obj.put("type", "builtin_widget")
                        obj.put("widgetType", item.widgetType.name)
                        obj.put("note", item.customNote)
                    }
                    is HomeItem.AndroidWidgetItem -> {
                        obj.put("type", "android_widget")
                        obj.put("appWidgetId", item.appWidgetId)
                        obj.put("pkg", item.providerPackage)
                        obj.put("cls", item.providerClass)
                    }
                }
                itemsArr.put(obj)
            }
            root.put("items", itemsArr)
            return root.toString()
        }

        fun deserializeWorkspace(raw: String): LauncherWorkspaceState {
            val root = JSONObject(raw)
            val state = LauncherWorkspaceState(
                pagesCount = root.optInt("pagesCount", 1).coerceAtLeast(1)
            )

            val dockArr = root.optJSONArray("dock")
            if (dockArr != null) {
                for (i in 0 until dockArr.length()) {
                    val o = dockArr.getJSONObject(i)
                    state.dock.add(
                        DockSlot(
                            index = o.optInt("index", i),
                            packageName = o.getString("pkg"),
                            className = o.getString("cls"),
                            label = o.optString("label", "")
                        )
                    )
                }
            }

            val itemsArr = root.optJSONArray("items")
            if (itemsArr != null) {
                for (i in 0 until itemsArr.length()) {
                    val o = itemsArr.getJSONObject(i)
                    val id = o.optString("id", newId())
                    val page = o.optInt("page", 0)
                    val col = o.optInt("col", 0)
                    val row = o.optInt("row", 0)
                    val spanX = o.optInt("spanX", 1)
                    val spanY = o.optInt("spanY", 1)

                    when (o.optString("type")) {
                        "app" -> state.items.add(
                            HomeItem.AppShortcut(
                                id = id,
                                page = page,
                                col = col,
                                row = row,
                                packageName = o.getString("pkg"),
                                className = o.getString("cls"),
                                label = o.optString("label", "")
                            )
                        )
                        "folder" -> {
                            val children = mutableListOf<HomeItem.AppShortcut>()
                            val cArr = o.optJSONArray("apps")
                            if (cArr != null) {
                                for (j in 0 until cArr.length()) {
                                    val c = cArr.getJSONObject(j)
                                    children.add(
                                        HomeItem.AppShortcut(
                                            id = c.optString("id", newId()),
                                            page = page,
                                            col = 0,
                                            row = 0,
                                            packageName = c.getString("pkg"),
                                            className = c.getString("cls"),
                                            label = c.optString("label", "")
                                        )
                                    )
                                }
                            }
                            state.items.add(
                                HomeItem.FolderItem(
                                    id = id,
                                    page = page,
                                    col = col,
                                    row = row,
                                    title = o.optString("title", "Folder"),
                                    apps = children
                                )
                            )
                        }
                        "builtin_widget" -> {
                            val wtName = o.optString("widgetType", BuiltInWidgetType.CLOCK_2X2.name)
                            val wt = runCatching { BuiltInWidgetType.valueOf(wtName) }
                                .getOrDefault(BuiltInWidgetType.CLOCK_2X2)
                            state.items.add(
                                HomeItem.BuiltInWidgetItem(
                                    id = id,
                                    page = page,
                                    col = col,
                                    row = row,
                                    spanX = spanX,
                                    spanY = spanY,
                                    widgetType = wt,
                                    customNote = o.optString("note", "Tap to edit note")
                                )
                            )
                        }
                        "android_widget" -> state.items.add(
                            HomeItem.AndroidWidgetItem(
                                id = id,
                                page = page,
                                col = col,
                                row = row,
                                spanX = spanX,
                                spanY = spanY,
                                appWidgetId = o.getInt("appWidgetId"),
                                providerPackage = o.getString("pkg"),
                                providerClass = o.getString("cls")
                            )
                        )
                    }
                }
            }
            return state
        }
    }
}
