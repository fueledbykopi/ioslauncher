package com.iospring.launcher

import android.content.pm.ApplicationInfo
import com.iospring.launcher.core.AppCategorizer
import com.iospring.launcher.core.ColorOsCompat
import com.iospring.launcher.core.GestureSafeInsets
import com.iospring.launcher.core.IosIconPackRenderer
import com.iospring.launcher.core.SpotlightEngine
import com.iospring.launcher.core.SpringConfig
import com.iospring.launcher.core.SpringMath
import com.iospring.launcher.core.SpringState
import com.iospring.launcher.data.GridOccupancy
import com.iospring.launcher.model.AppCategory
import com.iospring.launcher.model.AppEntry
import com.iospring.launcher.model.BuiltInWidgetType
import com.iospring.launcher.model.GlassThemeStyle
import com.iospring.launcher.model.HomeItem
import com.iospring.launcher.model.IconSizeMode
import com.iospring.launcher.model.IconThemeStyle
import com.iospring.launcher.model.LauncherPreferences
import com.iospring.launcher.model.LauncherWorkspaceState
import com.iospring.launcher.model.LockClockStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class IospringCoreTest {

    @Test
    fun springPhysicsConvergesSmoothlyToTarget() {
        val state = SpringState(value = 0f, velocity = 0f)
        var settled = false
        for (frame in 0 until 240) {
            settled = state.step(target = 1080f, dtSeconds = 1f / 120f, config = SpringConfig.PAGE_SNAP)
            if (settled) break
        }
        assertTrue("Spring should settle within 2 seconds", settled)
        assertEquals(1080f, state.value, 0.01f)
    }

    @Test
    fun rubberBandResistsOverscrollAndPreservesSign() {
        val pos = SpringMath.rubberBand(200f, 1080f)
        val neg = SpringMath.rubberBand(-200f, 1080f)
        assertTrue(pos > 0f && pos < 200f)
        assertTrue(neg < 0f && neg > -200f)
        assertEquals(abs(pos), abs(neg), 0.001f)
    }

    @Test
    fun spotlightT9AndPrefixMatchingWork() {
        val apps = listOf(
            AppEntry("com.whatsapp", ".Main", "WhatsApp", AppCategory.SOCIAL),
            AppEntry("com.instagram.android", ".Main", "Instagram", AppCategory.SOCIAL),
            AppEntry("com.android.camera", ".Main", "Camera", AppCategory.CREATIVITY),
            AppEntry("com.secret.vault", ".Main", "Secret Vault", AppCategory.UTILITIES)
        )
        val hidden = setOf("com.secret.vault")

        // Prefix match excludes hidden apps
        val secretRes = SpotlightEngine.search("secret", apps, hidden, emptyMap())
        assertTrue(secretRes.matchedApps.isEmpty())

        // T9 numeric match: "942" -> "wha" (WhatsApp)
        val t9Res = SpotlightEngine.search("942", apps, hidden, emptyMap())
        assertEquals("WhatsApp", t9Res.matchedApps.firstOrNull()?.label)

        // T9 numeric match: "226" -> "cam" (Camera)
        val camRes = SpotlightEngine.search("226", apps, hidden, emptyMap())
        assertEquals("Camera", camRes.matchedApps.firstOrNull()?.label)
    }

    @Test
    fun spotlightInlineCalculatorEvaluatesExpressions() {
        assertEquals("168", SpotlightEngine.evaluateMathExpression("24 * 7"))
        assertEquals("30", SpotlightEngine.evaluateMathExpression("(45 + 15) / 2"))
        assertEquals("12.5", SpotlightEngine.evaluateMathExpression("25 / 2"))
        assertNull(SpotlightEngine.evaluateMathExpression("whatsapp"))
        assertNull(SpotlightEngine.evaluateMathExpression("10 / 0"))
    }

    @Test
    fun appCategorizerClassifiesPopularAndColorOsApps() {
        assertEquals(
            AppCategory.SOCIAL,
            AppCategorizer.classify("com.whatsapp", "WhatsApp", ApplicationInfo.CATEGORY_UNDEFINED)
        )
        assertEquals(
            AppCategory.SHOPPING,
            AppCategorizer.classify("com.shopee.id", "Shopee", ApplicationInfo.CATEGORY_UNDEFINED)
        )
        assertEquals(
            AppCategory.GAMES,
            AppCategorizer.classify("com.mobile.legends", "Mobile Legends", ApplicationInfo.CATEGORY_GAME)
        )
        assertEquals(
            AppCategory.UTILITIES,
            AppCategorizer.classify("com.coloros.phonemanager", "Phone Manager", ApplicationInfo.CATEGORY_UNDEFINED)
        )
    }

    @Test
    fun gridOccupancySupportsIos18FreePlacementAndWidgets() {
        val widget = HomeItem.BuiltInWidgetItem(
            id = "w1", page = 0, col = 0, row = 0, spanX = 2, spanY = 2,
            widgetType = BuiltInWidgetType.CLOCK_2X2
        )
        val app = HomeItem.AppShortcut(
            id = "a1", page = 0, col = 3, row = 5,
            packageName = "com.whatsapp", className = ".Main", label = "WhatsApp"
        )
        val occ = GridOccupancy.forPage(0, cols = 4, rows = 6, items = listOf(widget, app))

        // Occupied cells by 2x2 widget
        assertFalse(occ.canPlace(0, 0, 1, 1))
        assertFalse(occ.canPlace(1, 1, 1, 1))
        // Open cells next to widget
        assertTrue(occ.canPlace(2, 0, 2, 2))
        // Occupied bottom-right cell by free-placed app
        assertFalse(occ.canPlace(3, 5, 1, 1))
        // Moving same item to its own cell is allowed
        assertTrue(occ.canPlace(3, 5, 1, 1, ignoreItemId = "a1"))
        // Vacant 2x2 slot finds (2, 0)
        assertEquals(Pair(2, 0), occ.findFirstVacant(2, 2))
    }

    @Test
    fun colorOsGestureSafeZoneProtectsEdgesAndBottomBar() {
        val insets = GestureSafeInsets(
            statusBarTop = 96,
            navBarBottom = 64,
            gestureLeft = 72,
            gestureRight = 72,
            gestureBottom = 88
        )
        val w = 1080
        val h = 2400

        // Left & right Back gesture edges protected
        assertTrue(ColorOsCompat.isInSystemGestureZone(20f, 1200f, w, h, insets, enabled = true))
        assertTrue(ColorOsCompat.isInSystemGestureZone(1050f, 1200f, w, h, insets, enabled = true))
        // Bottom ColorOS Home/Recents swipe-up bar protected
        assertTrue(ColorOsCompat.isInSystemGestureZone(540f, 2350f, w, h, insets, enabled = true))
        // Mid-screen touch allowed for workspace swipes & Spotlight pull-down
        assertFalse(ColorOsCompat.isInSystemGestureZone(540f, 1200f, w, h, insets, enabled = true))
        // Disabled safe zone lets all touches through
        assertFalse(ColorOsCompat.isInSystemGestureZone(10f, 2380f, w, h, insets, enabled = false))
    }

    @Test
    fun springStateRecoversCleanlyFromNonFiniteInputs() {
        val state = SpringState(value = Float.NaN, velocity = Float.POSITIVE_INFINITY)
        val settled = state.step(target = 1080f, dtSeconds = Float.NaN, config = SpringConfig.PAGE_SNAP)
        assertTrue("Spring value must stay finite", state.value.isFinite())
        assertTrue("Spring velocity must stay finite", state.velocity.isFinite())
        assertTrue("Non-finite initial state should snap cleanly to target", settled)
        assertEquals(1080f, state.value, 0.01f)
    }

    @Test
    fun colorOsGestureSafeZoneClampsOversizedInsets() {
        val rogueInsets = GestureSafeInsets(
            statusBarTop = 96,
            navBarBottom = 64,
            gestureLeft = 600,
            gestureRight = 600,
            gestureBottom = 1200
        )
        val w = 1080
        val h = 2400

        // Center of screen must never be blocked even if OEM reports oversized insets (e.g. IME)
        assertFalse(ColorOsCompat.isInSystemGestureZone(540f, 1200f, w, h, rogueInsets, enabled = true))
        // True edges remain protected
        assertTrue(ColorOsCompat.isInSystemGestureZone(20f, 1200f, w, h, rogueInsets, enabled = true))
        assertTrue(ColorOsCompat.isInSystemGestureZone(540f, 2350f, w, h, rogueInsets, enabled = true))
    }

    @Test
    fun builtInIosIconPackMatchesPopularAppsAndPreservesPlayStoreBrowsersAndGames() {
        assertEquals(IosIconPackRenderer.IconGlyph.WHATSAPP, IosIconPackRenderer.matchGlyph("com.whatsapp", "WhatsApp"))
        assertEquals(IosIconPackRenderer.IconGlyph.INSTAGRAM, IosIconPackRenderer.matchGlyph("com.instagram.android", "Instagram"))
        assertEquals(IosIconPackRenderer.IconGlyph.YOUTUBE, IosIconPackRenderer.matchGlyph("com.google.android.youtube", "YouTube"))
        assertEquals(IosIconPackRenderer.IconGlyph.TIKTOK, IosIconPackRenderer.matchGlyph("com.ss.android.ugc.trill", "TikTok"))
        assertEquals(IosIconPackRenderer.IconGlyph.SPOTIFY, IosIconPackRenderer.matchGlyph("com.spotify.music", "Spotify"))
        assertEquals(IosIconPackRenderer.IconGlyph.CAMERA, IosIconPackRenderer.matchGlyph("com.oplus.camera", "Camera"))
        assertEquals(IosIconPackRenderer.IconGlyph.SETTINGS, IosIconPackRenderer.matchGlyph("com.android.settings", "Settings"))
        assertEquals(IosIconPackRenderer.IconGlyph.SHOPPING, IosIconPackRenderer.matchGlyph("com.shopee.id", "Shopee"))
        assertEquals(IosIconPackRenderer.IconGlyph.RIDE_HAILING, IosIconPackRenderer.matchGlyph("com.gojek.app", "Gojek"))
        assertEquals(IosIconPackRenderer.IconGlyph.WALLET, IosIconPackRenderer.matchGlyph("id.dana", "DANA"))

        // Play Store & Browsers keep their own authentic icons styled with iOS squircle & glass rim
        assertNull(IosIconPackRenderer.matchGlyph("com.android.vending", "Play Store"))
        assertNull(IosIconPackRenderer.matchGlyph("com.android.chrome", "Chrome"))
        assertNull(IosIconPackRenderer.matchGlyph("org.mozilla.firefox", "Firefox"))
        assertNull(IosIconPackRenderer.matchGlyph("com.brave.browser", "Brave"))
        assertNull(IosIconPackRenderer.matchGlyph("com.opera.browser", "Opera"))
        assertNull(IosIconPackRenderer.matchGlyph("com.microsoft.emmx", "Edge"))
        assertNull(IosIconPackRenderer.matchGlyph("com.heytap.browser", "Browser"))
        assertNull(IosIconPackRenderer.matchGlyph("com.mobile.legends", "Mobile Legends"))
    }

    @Test
    fun autoArrangeCompactsEmptySpacesAfterUninstallWhilePreservingWidgets() {
        val widget = HomeItem.BuiltInWidgetItem(
            id = "w1", page = 0, col = 0, row = 0, spanX = 2, spanY = 2,
            widgetType = BuiltInWidgetType.CLOCK_2X2
        )
        // Simulate empty holes at (2,0), (0,2), (1,2) after uninstalls + an app stranded on page 1
        val appA = HomeItem.AppShortcut("a1", page = 0, col = 3, row = 0, "com.a", ".A", "App A")
        val appB = HomeItem.AppShortcut("a2", page = 0, col = 2, row = 2, "com.b", ".B", "App B")
        val appC = HomeItem.AppShortcut("a3", page = 1, col = 2, row = 3, "com.c", ".C", "App C")
        val state = LauncherWorkspaceState(
            pagesCount = 2,
            items = mutableListOf(widget, appA, appB, appC)
        )

        GridOccupancy.compactWorkspace(state, cols = 4, rows = 6)

        // Widget stays cleanly at (0,0)
        assertEquals(0, widget.page)
        assertEquals(0, widget.col)
        assertEquals(0, widget.row)

        // Apps flow sequentially into the earliest vacant cells around the 2x2 widget with zero gaps
        assertEquals(0, appA.page)
        assertEquals(2, appA.col)
        assertEquals(0, appA.row)

        assertEquals(0, appB.page)
        assertEquals(3, appB.col)
        assertEquals(0, appB.row)

        assertEquals(0, appC.page)
        assertEquals(2, appC.col)
        assertEquals(1, appC.row)

        // Empty page 1 collapsed automatically
        assertEquals(1, state.pagesCount)
    }

    @Test
    fun launcherPreferencesDefaultsAndGlassThemesAreValid() {
        val defaults = LauncherPreferences()
        assertTrue(defaults.showAppLabels)
        assertTrue(defaults.iosIconPackEnabled)
        assertTrue(defaults.keepInRamEnabled)
        assertTrue(defaults.autoArrangeIcons)
        assertFalse(defaults.cleanModeEnabled)
        assertTrue(defaults.doubleTapToLockEnabled)
        assertFalse(defaults.iosLockScreenEnabled)
        assertEquals(IconThemeStyle.DARK, defaults.iconThemeStyle)
        assertEquals(GlassThemeStyle.LIQUID_GLASS, defaults.glassThemeStyle)
        assertEquals(IconSizeMode.LARGE, defaults.iconSizeMode)
        assertEquals(LockClockStyle.LIQUID_GLASS, defaults.lockScreenClockStyle)
        assertTrue(GlassThemeStyle.entries.contains(GlassThemeStyle.FROSTED_GLASS))
        assertTrue(GlassThemeStyle.entries.contains(GlassThemeStyle.FROSTED_BLUR))
    }

    @Test
    fun autoArrangePreservesAndroidWidgetsWhileCompactingApps() {
        val androidWidget = HomeItem.AndroidWidgetItem(
            id = "aw1", page = 0, col = 0, row = 0, spanX = 4, spanY = 2,
            appWidgetId = 42, providerPackage = "com.google.android.deskclock", providerClass = ".ClockWidget"
        )
        val appA = HomeItem.AppShortcut("a1", page = 0, col = 3, row = 3, "com.a", ".A", "App A")
        val state = LauncherWorkspaceState(pagesCount = 1, items = mutableListOf(androidWidget, appA))

        GridOccupancy.compactWorkspace(state, cols = 4, rows = 6)

        assertEquals(0, androidWidget.col)
        assertEquals(0, androidWidget.row)
        assertEquals(0, appA.col)
        assertEquals(2, appA.row)
    }
}
