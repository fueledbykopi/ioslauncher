package com.iospring.launcher.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.iospring.launcher.core.BiometricAuthHelper
import com.iospring.launcher.core.ColorOsCompat
import com.iospring.launcher.data.LauncherRepository
import com.iospring.launcher.model.IconSizeMode
import com.iospring.launcher.model.IconThemeStyle
import com.iospring.launcher.model.LauncherPreferences
import com.iospring.launcher.service.LauncherKeepAliveService

/**
 * Clean iOS 18 Grouped Settings interface for iOSpring.
 * Manages Icon Style (Default / Dark / Tinted), Large Icons, Grid Size,
 * Hidden Apps & Biometric App Lock, OPPO ColorOS 15 gesture optimization,
 * and one-tap Default Home App activation.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var repository: LauncherRepository
    private lateinit var prefs: LauncherPreferences
    private lateinit var rootColumn: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ColorOsCompat.configureEdgeToEdge(this)

        repository = LauncherRepository(applicationContext)
        prefs = repository.loadPreferences()

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            isVerticalScrollBarEnabled = false
        }
        rootColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val hp = IosDesign.dpInt(this@SettingsActivity, 16f)
            setPadding(hp, IosDesign.dpInt(this@SettingsActivity, 52f), hp, IosDesign.dpInt(this@SettingsActivity, 44f))
        }
        scroll.addView(rootColumn)
        setContentView(scroll)

        ColorOsCompat.observeInsets(scroll) { insets ->
            val hp = IosDesign.dpInt(this, 16f)
            rootColumn.setPadding(hp, insets.statusBarTop + IosDesign.dpInt(this, 16f), hp, insets.navBarBottom + IosDesign.dpInt(this, 28f))
        }

        renderSettings()
    }

    override fun onResume() {
        super.onResume()
        prefs = repository.loadPreferences()
        renderSettings()
    }

    override fun onDestroy() {
        repository.close()
        super.onDestroy()
    }

    private fun renderSettings() {
        rootColumn.removeAllViews()

        // Top Header
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "iOSpring"
            setTextColor(Color.WHITE)
            textSize = 32f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val closeBtn = TextView(this).apply {
            text = "Done"
            setTextColor(IosDesign.IOS_BLUE)
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setOnClickListener { finish() }
        }
        headerRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        headerRow.addView(closeBtn)
        rootColumn.addView(headerRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = IosDesign.dpInt(this@SettingsActivity, 18f) })

        // 1. Default Home App Banner
        val isDefault = isDefaultHomeLauncher()
        val defaultCard = createGroupCard()
        defaultCard.addView(createActionRow(
            title = if (isDefault) "iOSpring is Default Home App ✓" else "Set iOSpring as Default Home App",
            subtitle = if (isDefault) "Active on your device" else "Tap to select iOSpring in Android Default Home Settings",
            accentColor = if (isDefault) IosDesign.IOS_GREEN else IosDesign.IOS_BLUE
        ) {
            openDefaultHomeSettings()
        })
        rootColumn.addView(defaultCard)

        // 2. iOS 18 Icon Appearance
        rootColumn.addView(createSectionHeader("IOS 18 ICON APPEARANCE"))
        val iconCard = createGroupCard()
        iconCard.addView(createActionRow(
            title = "Icon Style",
            subtitle = prefs.iconThemeStyle.label,
            accentColor = IosDesign.IOS_BLUE
        ) {
            showIconStylePicker()
        })
        if (prefs.iconThemeStyle == IconThemeStyle.TINTED) {
            iconCard.addView(createDivider())
            iconCard.addView(buildTintSwatchRow())
        }
        iconCard.addView(createDivider())
        iconCard.addView(createSwitchRow(
            title = "Built-in iOS Icon Pack",
            subtitle = "Custom iOS 18 vector icons for popular apps",
            checked = prefs.iosIconPackEnabled
        ) { checked ->
            updatePrefs(prefs.copy(iosIconPackEnabled = checked))
        })
        iconCard.addView(createDivider())
        iconCard.addView(createSwitchRow(
            title = "Show App Labels",
            subtitle = "Show app name text below icons (default on)",
            checked = prefs.showAppLabels
        ) { checked ->
            updatePrefs(prefs.copy(showAppLabels = checked))
        })
        iconCard.addView(createDivider())
        iconCard.addView(createSwitchRow(
            title = "Large Icons",
            subtitle = "Enlarge squircle icons on Home Screen",
            checked = prefs.iconSizeMode == IconSizeMode.LARGE
        ) { checked ->
            updatePrefs(prefs.copy(iconSizeMode = if (checked) IconSizeMode.LARGE else IconSizeMode.STANDARD))
        })
        iconCard.addView(createDivider())
        iconCard.addView(createActionRow(
            title = "Home Screen Grid",
            subtitle = "${prefs.gridCols} × ${prefs.gridRows}",
            accentColor = IosDesign.IOS_BLUE
        ) {
            val nextCols = if (prefs.gridCols == 4) 5 else 4
            updatePrefs(prefs.copy(gridCols = nextCols))
            renderSettings()
        })
        rootColumn.addView(iconCard)

        // 3. Home Screen & Gestures
        rootColumn.addView(createSectionHeader("HOME SCREEN & FEATURES"))
        val homeCard = createGroupCard()
        homeCard.addView(createSwitchRow("Floating Search Pill", "Show Search pill above Dock", prefs.showSearchPill) {
            updatePrefs(prefs.copy(showSearchPill = it))
        })
        homeCard.addView(createDivider())
        homeCard.addView(createSwitchRow("Swipe Down for Spotlight", "Pull down anywhere on Home to search", prefs.swipeDownSpotlight) {
            updatePrefs(prefs.copy(swipeDownSpotlight = it))
        })
        homeCard.addView(createDivider())
        homeCard.addView(createSwitchRow("App Library", "Auto-categorized apps on rightmost page", prefs.appLibraryEnabled) {
            updatePrefs(prefs.copy(appLibraryEnabled = it))
        })
        homeCard.addView(createDivider())
        homeCard.addView(createSwitchRow("Today View", "Widget page to the left of Home", prefs.todayViewEnabled) {
            updatePrefs(prefs.copy(todayViewEnabled = it))
        })
        homeCard.addView(createDivider())
        homeCard.addView(createSwitchRow("Frosted Glass Blur (Android 12+)", "GPU RenderEffect blur on Dock & overlays", prefs.blurEnabled) {
            updatePrefs(prefs.copy(blurEnabled = it))
        })
        homeCard.addView(createDivider())
        homeCard.addView(createSwitchRow("Notification Badges", "Red iOS notification count dots", prefs.showBadges) {
            updatePrefs(prefs.copy(showBadges = it))
        })
        homeCard.addView(createDivider())
        homeCard.addView(createActionRow(
            title = "Notification Badge Permission",
            subtitle = "Grant Notification Access for live red badges",
            accentColor = IosDesign.IOS_BLUE
        ) {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        })
        homeCard.addView(createDivider())
        homeCard.addView(createSwitchRow("Haptic Feedback", "Subtle iOS tactile feedback on taps & drags", prefs.hapticsEnabled) {
            updatePrefs(prefs.copy(hapticsEnabled = it))
        })
        rootColumn.addView(homeCard)

        // 4. Privacy & Biometric App Lock
        rootColumn.addView(createSectionHeader("PRIVACY & BIOMETRIC LOCK"))
        val privacyCard = createGroupCard()
        privacyCard.addView(createActionRow(
            title = "Hidden Apps (${prefs.hiddenPackages.size})",
            subtitle = "Hide apps from Home Screen & App Library",
            accentColor = IosDesign.IOS_BLUE
        ) {
            openBiometricAppPicker(isHiddenPicker = true)
        })
        privacyCard.addView(createDivider())
        privacyCard.addView(createActionRow(
            title = "Locked Apps (${prefs.lockedPackages.size})",
            subtitle = "Require Fingerprint / Face Unlock before opening",
            accentColor = IosDesign.IOS_BLUE
        ) {
            openBiometricAppPicker(isHiddenPicker = false)
        })
        rootColumn.addView(privacyCard)

        // 5. Memory Residency & OPPO Android 15 (ColorOS 15) Optimization
        rootColumn.addView(createSectionHeader("MEMORY & OPPO COLOROS 15"))
        val oppoCard = createGroupCard()
        oppoCard.addView(createSwitchRow(
            title = "Keep Launcher in RAM",
            subtitle = "Low-memory persistent service prevents OS from killing launcher",
            checked = prefs.keepInRamEnabled
        ) { checked ->
            updatePrefs(prefs.copy(keepInRamEnabled = checked))
            LauncherKeepAliveService.syncState(this, checked)
        })
        oppoCard.addView(createDivider())
        oppoCard.addView(createSwitchRow(
            title = "ColorOS 15 Gesture Safe Zone",
            subtitle = "Protect bottom Home/Recents bar & side Back edges",
            checked = prefs.colorOsGestureSafeZone
        ) {
            updatePrefs(prefs.copy(colorOsGestureSafeZone = it))
        })
        oppoCard.addView(createDivider())
        val isUnrestricted = isIgnoringBatteryOptimizations()
        oppoCard.addView(createActionRow(
            title = if (isUnrestricted) "Battery Optimization Unrestricted ✓" else "Allow Unrestricted Background Battery",
            subtitle = if (isUnrestricted) "Protected from background memory kills" else "Tap to whitelist iOSpring so Android never kills it",
            accentColor = if (isUnrestricted) IosDesign.IOS_GREEN else IosDesign.IOS_BLUE
        ) {
            requestUnrestrictedBattery()
        })
        rootColumn.addView(oppoCard)

        // 6. Reset Layout
        rootColumn.addView(createSectionHeader("MAINTENANCE"))
        val maintCard = createGroupCard()
        maintCard.addView(createActionRow(
            title = "Reset Home Screen Layout",
            subtitle = "Restore default iOS 18 layout & widgets",
            accentColor = IosDesign.IOS_RED
        ) {
            confirmResetLayout()
        })
        rootColumn.addView(maintCard)
    }

    private fun updatePrefs(updated: LauncherPreferences) {
        prefs = updated
        repository.savePreferences(updated)
    }

    private fun showIconStylePicker() {
        val styles = IconThemeStyle.entries
        val names = styles.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("iOS 18 Icon Style")
            .setSingleChoiceItems(names, prefs.iconThemeStyle.ordinal) { dlg, which ->
                updatePrefs(prefs.copy(iconThemeStyle = styles[which]))
                dlg.dismiss()
                renderSettings()
            }
            .show()
    }

    private fun buildTintSwatchRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = IosDesign.dpInt(this@SettingsActivity, 14f)
            setPadding(p, IosDesign.dpInt(this@SettingsActivity, 10f), p, IosDesign.dpInt(this@SettingsActivity, 10f))
        }
        val sz = IosDesign.dpInt(this, 30f)
        for ((_, color) in IosDesign.TINT_PRESETS) {
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (color == prefs.iconTintColor) {
                        setStroke(IosDesign.dpInt(this@SettingsActivity, 2.5f), Color.WHITE)
                    }
                }
                setOnClickListener {
                    updatePrefs(prefs.copy(iconTintColor = color))
                    renderSettings()
                }
            }
            row.addView(dot, LinearLayout.LayoutParams(sz, sz).apply {
                marginEnd = IosDesign.dpInt(this@SettingsActivity, 10f)
            })
        }
        return row
    }

    private fun openBiometricAppPicker(isHiddenPicker: Boolean) {
        val title = if (isHiddenPicker) "Manage Hidden Apps" else "Manage Locked Apps"
        BiometricAuthHelper.authenticate(
            activity = this,
            title = title,
            subtitle = "Authenticate to modify privacy settings",
            onSuccess = {
                val apps = repository.loadInstalledApps()
                val labels = apps.map { it.label }.toTypedArray()
                val currentSet = if (isHiddenPicker) prefs.hiddenPackages else prefs.lockedPackages
                val checked = BooleanArray(apps.size) { apps[it].packageName in currentSet }

                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                        checked[which] = isChecked
                    }
                    .setPositiveButton("Save") { _, _ ->
                        val next = mutableSetOf<String>()
                        apps.forEachIndexed { idx, app ->
                            if (checked[idx]) next.add(app.packageName)
                        }
                        if (isHiddenPicker) {
                            updatePrefs(prefs.copy(hiddenPackages = next))
                        } else {
                            updatePrefs(prefs.copy(lockedPackages = next))
                        }
                        renderSettings()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onFailure = { reason ->
                Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun confirmResetLayout() {
        AlertDialog.Builder(this)
            .setTitle("Reset Home Screen?")
            .setMessage("This restores the default iOS 18 widgets, dock, and app pages.")
            .setPositiveButton("Reset") { _, _ ->
                val apps = repository.loadInstalledApps()
                repository.resetWorkspace(apps, prefs)
                Toast.makeText(this, "Home layout restored", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openDefaultHomeSettings() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun isDefaultHomeLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return res?.activityInfo?.packageName == packageName
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm?.isIgnoringBatteryOptimizations(packageName) == true
    }

    private fun requestUnrestrictedBattery() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    // -------------------------------------------------------------------------
    // iOS Grouped Settings UI Builders
    // -------------------------------------------------------------------------

    private fun createSectionHeader(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(0xFF8E8E93.toInt())
            textSize = 12.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(
                IosDesign.dpInt(this@SettingsActivity, 14f),
                IosDesign.dpInt(this@SettingsActivity, 18f),
                0,
                IosDesign.dpInt(this@SettingsActivity, 6f)
            )
        }
    }

    private fun createGroupCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = IosDesign.dp(this@SettingsActivity, 14f)
                setColor(0xFF1C1C1E.toInt())
            }
        }
    }

    private fun createDivider(): View {
        return View(this).apply {
            setBackgroundColor(0xFF2C2C2E.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                marginStart = IosDesign.dpInt(this@SettingsActivity, 16f)
            }
        }
    }

    private fun createActionRow(
        title: String,
        subtitle: String,
        accentColor: Int,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val hp = IosDesign.dpInt(this@SettingsActivity, 16f)
            val vp = IosDesign.dpInt(this@SettingsActivity, 13f)
            setPadding(hp, vp, hp, vp)
            setOnClickListener { onClick() }
        }
        row.addView(TextView(this).apply {
            text = title
            setTextColor(accentColor)
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        row.addView(TextView(this).apply {
            text = subtitle
            setTextColor(0xFF8E8E93.toInt())
            textSize = 13f
            setPadding(0, IosDesign.dpInt(this@SettingsActivity, 2f), 0, 0)
        })
        return row
    }

    private fun createSwitchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hp = IosDesign.dpInt(this@SettingsActivity, 16f)
            val vp = IosDesign.dpInt(this@SettingsActivity, 11f)
            setPadding(hp, vp, hp, vp)
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        textCol.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
        })
        textCol.addView(TextView(this).apply {
            text = subtitle
            setTextColor(0xFF8E8E93.toInt())
            textSize = 12.5f
        })
        val sw = Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }
        row.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(sw)
        return row
    }
}
