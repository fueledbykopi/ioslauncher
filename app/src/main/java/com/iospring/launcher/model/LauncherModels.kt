package com.iospring.launcher.model

enum class AppCategory(val displayName: String, val sortOrder: Int) {
    SUGGESTIONS("Suggestions", 0),
    RECENTLY_ADDED("Recently Added", 1),
    SOCIAL("Social", 2),
    UTILITIES("Utilities", 3),
    PRODUCTIVITY("Productivity & Finance", 4),
    ENTERTAINMENT("Entertainment", 5),
    CREATIVITY("Creativity", 6),
    GAMES("Games", 7),
    SHOPPING("Shopping & Food", 8),
    INFORMATION("Information & Reading", 9),
    OTHER("Other", 10)
}

enum class IconThemeStyle(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    TINTED("Tinted")
}

enum class GlassThemeStyle(val label: String, val subtitle: String) {
    LIQUID_GLASS("Liquid Glass (Latest iOS)", "Crystal-clear refractive optical glass & specular rim"),
    FROSTED_BLUR("Frosted Blur (iOS 18)", "Rich milky frosted glass with smooth backdrop blur"),
    OBSIDIAN_GLASS("Obsidian Glass (Dark)", "Deep smoked dark glass with titanium sheen")
}

enum class IconSizeMode(val label: String) {
    STANDARD("Small (Labels)"),
    LARGE("Large (No Labels)")
}

enum class BuiltInWidgetType(
    val title: String,
    val subtitle: String,
    val defaultSpanX: Int,
    val defaultSpanY: Int
) {
    CLOCK_2X2("Clock", "iOS Analog World Clock", 2, 2),
    CALENDAR_2X2("Calendar", "Today's Date & Schedule", 2, 2),
    BATTERY_2X2("Batteries", "Device Power Ring", 2, 2),
    QUICK_STATUS_4X2("Device Summary", "Date, Battery & RAM Status", 4, 2),
    NOTES_4X2("Quick Note", "Pinned Glass Note Card", 4, 2)
}

data class AppEntry(
    val packageName: String,
    val className: String,
    val label: String,
    val category: AppCategory = AppCategory.OTHER,
    val isSystemApp: Boolean = false,
    val firstInstallTime: Long = 0L
) {
    val componentKey: String get() = "$packageName/$className"
}

sealed class HomeItem {
    abstract val id: String
    abstract var page: Int
    abstract var col: Int
    abstract var row: Int
    abstract val spanX: Int
    abstract val spanY: Int

    data class AppShortcut(
        override val id: String,
        override var page: Int,
        override var col: Int,
        override var row: Int,
        val packageName: String,
        val className: String,
        var label: String
    ) : HomeItem() {
        override val spanX: Int = 1
        override val spanY: Int = 1
        val componentKey: String get() = "$packageName/$className"
    }

    data class FolderItem(
        override val id: String,
        override var page: Int,
        override var col: Int,
        override var row: Int,
        var title: String,
        val apps: MutableList<AppShortcut> = mutableListOf()
    ) : HomeItem() {
        override val spanX: Int = 1
        override val spanY: Int = 1
    }

    data class BuiltInWidgetItem(
        override val id: String,
        override var page: Int,
        override var col: Int,
        override var row: Int,
        override val spanX: Int,
        override val spanY: Int,
        val widgetType: BuiltInWidgetType,
        var customNote: String = "Tap to edit note in iOSpring"
    ) : HomeItem()

    data class AndroidWidgetItem(
        override val id: String,
        override var page: Int,
        override var col: Int,
        override var row: Int,
        override val spanX: Int,
        override val spanY: Int,
        val appWidgetId: Int,
        val providerPackage: String,
        val providerClass: String
    ) : HomeItem()
}

data class DockSlot(
    val index: Int,
    val packageName: String,
    val className: String,
    val label: String
) {
    val componentKey: String get() = "$packageName/$className"
}

data class LauncherPreferences(
    val gridCols: Int = 4,
    val gridRows: Int = 6,
    val iconThemeStyle: IconThemeStyle = IconThemeStyle.LIGHT,
    val glassThemeStyle: GlassThemeStyle = GlassThemeStyle.LIQUID_GLASS,
    val iconTintColor: Int = 0xFF0A84FF.toInt(),
    val iconSizeMode: IconSizeMode = IconSizeMode.STANDARD,
    val showAppLabels: Boolean = true,
    val iosIconPackEnabled: Boolean = true,
    val keepInRamEnabled: Boolean = true,
    val showSearchPill: Boolean = true,
    val showBadges: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val blurEnabled: Boolean = true,
    val swipeDownSpotlight: Boolean = true,
    val todayViewEnabled: Boolean = true,
    val appLibraryEnabled: Boolean = true,
    val colorOsGestureSafeZone: Boolean = true,
    val hiddenPackages: Set<String> = emptySet(),
    val lockedPackages: Set<String> = emptySet()
)

data class LauncherWorkspaceState(
    var pagesCount: Int = 1,
    val items: MutableList<HomeItem> = mutableListOf(),
    val dock: MutableList<DockSlot> = mutableListOf()
)

data class SpotlightResult(
    val mathExpression: String? = null,
    val mathResult: String? = null,
    val suggestedApps: List<AppEntry> = emptyList(),
    val matchedApps: List<AppEntry> = emptyList()
)
