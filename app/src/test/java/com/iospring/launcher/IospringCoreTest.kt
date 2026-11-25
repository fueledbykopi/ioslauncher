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
import com.iospring.launcher.model.LauncherPreferences
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
    fun builtInIosIconPackMatchesPopularAppsAndPreservesUnknownGames() {
        assertEquals(IosIconPackRenderer.IconGlyph.WHATSAPP, IosIconPackRenderer.matchGlyph("com.whatsapp", "WhatsApp"))
        assertEquals(IosIconPackRenderer.IconGlyph.INSTAGRAM, IosIconPackRenderer.matchGlyph("com.instagram.android", "Instagram"))
        assertEquals(IosIconPackRenderer.IconGlyph.YOUTUBE, IosIconPackRenderer.matchGlyph("com.google.android.youtube", "YouTube"))
        assertEquals(IosIconPackRenderer.IconGlyph.TIKTOK, IosIconPackRenderer.matchGlyph("com.ss.android.ugc.trill", "TikTok"))
        assertEquals(IosIconPackRenderer.IconGlyph.SPOTIFY, IosIconPackRenderer.matchGlyph("com.spotify.music", "Spotify"))
        assertEquals(IosIconPackRenderer.IconGlyph.BROWSER, IosIconPackRenderer.matchGlyph("com.android.chrome", "Chrome"))
        assertEquals(IosIconPackRenderer.IconGlyph.CAMERA, IosIconPackRenderer.matchGlyph("com.oplus.camera", "Camera"))
        assertEquals(IosIconPackRenderer.IconGlyph.SETTINGS, IosIconPackRenderer.matchGlyph("com.android.settings", "Settings"))
        assertEquals(IosIconPackRenderer.IconGlyph.SHOPPING, IosIconPackRenderer.matchGlyph("com.shopee.id", "Shopee"))
        assertEquals(IosIconPackRenderer.IconGlyph.RIDE_HAILING, IosIconPackRenderer.matchGlyph("com.gojek.app", "Gojek"))
        assertEquals(IosIconPackRenderer.IconGlyph.WALLET, IosIconPackRenderer.matchGlyph("id.dana", "DANA"))
        assertNull(IosIconPackRenderer.matchGlyph("com.mobile.legends", "Mobile Legends"))
    }

    @Test
    fun launcherPreferencesEnableLabelsIconPackAndKeepInRamByDefault() {
        val defaults = LauncherPreferences()
        assertTrue(defaults.showAppLabels)
        assertTrue(defaults.iosIconPackEnabled)
        assertTrue(defaults.keepInRamEnabled)
        assertEquals(GlassThemeStyle.LIQUID_GLASS, defaults.glassThemeStyle)
    }
}
