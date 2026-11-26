package com.iospring.launcher.ui

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.iospring.launcher.core.ColorOsCompat
import com.iospring.launcher.data.LauncherRepository
import com.iospring.launcher.model.GlassThemeStyle
import com.iospring.launcher.model.IconThemeStyle
import com.iospring.launcher.model.LauncherPreferences
import com.iospring.launcher.model.LockClockStyle
import com.iospring.launcher.service.BadgeStore
import com.iospring.launcher.service.ScreenLockAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Authentic Latest Apple iOS (iOS 26 Liquid Glass) Lock Screen.
 *
 * Engineered for zero idle battery drain, minimal RAM, and silky 120Hz responsiveness:
 * - Zero wake locks, zero 1-second loops — updates clock strictly via ACTION_TIME_TICK while visible.
 * - Live system wallpaper with subtle atmospheric scrim for razor-sharp contrast.
 * - Giant refractive Liquid Glass clock + inline Battery/Date/RAM widgets + stacked Glass notifications.
 * - Real Camera Torch Flashlight button (bottom-left) & Camera launch button (bottom-right).
 * - Smooth 1:1 swipe-up-to-unlock gesture with spring snap-back and seamless Keyguard handoff.
 * - Optional double-tap on empty lock screen to turn screen back off immediately.
 */
class IosLockScreenActivity : ComponentActivity() {

    private lateinit var repository: LauncherRepository
    private lateinit var prefs: LauncherPreferences
    private var isPreviewMode: Boolean = false
    private var isUnlocking: Boolean = false

    private lateinit var rootFrame: FrameLayout
    private lateinit var contentLayer: FrameLayout
    private lateinit var topColumn: LinearLayout
    private lateinit var lockGlyphView: LockGlyphView
    private lateinit var dateTextView: TextView
    private lateinit var clockView: IosLockClockView
    private lateinit var batteryPill: MiniBatteryRingView
    private lateinit var dayPill: TextView
    private lateinit var ramPill: TextView
    private lateinit var notificationsColumn: LinearLayout
    private lateinit var flashlightBtn: CircularQuickActionView
    private lateinit var cameraBtn: CircularQuickActionView
    private lateinit var bottomBarRow: LinearLayout
    private lateinit var swipeHintTv: TextView
    private lateinit var homeIndicator: View

    private var torchEnabled: Boolean = false
    private var torchCameraId: String? = null
    private var tickRegistered: Boolean = false

    private val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val shortDayFormat = SimpleDateFormat("EEE d", Locale.getDefault())
    private val dateBuf = Date()
    private val memInfo = ActivityManager.MemoryInfo()

    // Interactive swipe-up unlock & double-tap screen-off touch state
    private var touchSlop: Int = 16
    private var doubleTapSlop: Int = 100
    private var doubleTapTimeoutMs: Long = 300L
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var draggingUnlock: Boolean = false
    private var velocityTracker: VelocityTracker? = null
    private var lastTapMs: Long = 0L
    private var lastTapX: Float = 0f
    private var lastTapY: Float = 0f

    private val badgeListener: () -> Unit = {
        runOnUiThread { renderLockNotifications() }
    }

    private val liveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isInitialStickyBroadcast) return
            updateClockAndStats()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isShowing = true
        isPreviewMode = intent.getBooleanExtra(EXTRA_PREVIEW, false)

        configureLockWindow()
        ColorOsCompat.configureEdgeToEdge(this)

        repository = LauncherRepository(applicationContext)
        prefs = repository.loadPreferences()
        val vc = ViewConfiguration.get(this)
        touchSlop = vc.scaledTouchSlop
        doubleTapSlop = vc.scaledDoubleTapSlop
        doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong()

        buildLockUi()
        setContentView(rootFrame)

        ColorOsCompat.observeInsets(rootFrame) { insets ->
            val topPad = insets.statusBarTop + IosDesign.dpInt(this, 24f)
            val botPad = maxOf(insets.navBarBottom, IosDesign.dpInt(this, 12f))
            topColumn.setPadding(
                IosDesign.dpInt(this, 20f),
                topPad,
                IosDesign.dpInt(this, 20f),
                IosDesign.dpInt(this, 12f)
            )
            (bottomBarRow.layoutParams as FrameLayout.LayoutParams).bottomMargin =
                botPad + IosDesign.dpInt(this, 36f)
            (swipeHintTv.layoutParams as FrameLayout.LayoutParams).bottomMargin =
                botPad + IosDesign.dpInt(this, 18f)
            (homeIndicator.layoutParams as FrameLayout.LayoutParams).bottomMargin =
                botPad + IosDesign.dpInt(this, 6f)
            contentLayer.requestLayout()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isPreviewMode) {
                    finishWithoutAnimation()
                }
            }
        })

        resolveTorchCameraId()
        updateClockAndStats()
        renderLockNotifications()
    }

    override fun onStart() {
        super.onStart()
        registerLiveReceiver()
        BadgeStore.addListener(badgeListener)
        updateClockAndStats()
        renderLockNotifications()
    }

    override fun onStop() {
        BadgeStore.removeListener(badgeListener)
        unregisterLiveReceiver()
        super.onStop()
    }

    override fun onDestroy() {
        isShowing = false
        releaseVelocityTracker()
        if (torchEnabled) {
            setTorchState(false)
        }
        repository.close()
        super.onDestroy()
    }

    private fun configureLockWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    private fun buildLockUi() {
        rootFrame = FrameLayout(this).apply {
            // Subtle iOS atmospheric top-and-bottom vignette over live wallpaper for crisp contrast
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x52000000, 0x16000000, 0x24000000, 0x62000000)
            )
        }

        contentLayer = FrameLayout(this)

        topColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // 1. Minimalist Apple Padlock Icon
        lockGlyphView = LockGlyphView(this)
        val lockSz = IosDesign.dpInt(this, 22f)
        topColumn.addView(lockGlyphView, LinearLayout.LayoutParams(lockSz, lockSz).apply {
            bottomMargin = IosDesign.dpInt(this@IosLockScreenActivity, 10f)
        })

        // 2. Date Line ("Thursday, 26 November")
        dateTextView = TextView(this).apply {
            setTextColor(0xEEFFFFFF.toInt())
            textSize = 18.5f
            typeface = IosDesign.TYPEFACE_MEDIUM
            gravity = Gravity.CENTER
            setShadowLayer(IosDesign.dp(this@IosLockScreenActivity, 4f), 0f, IosDesign.dp(this@IosLockScreenActivity, 1.5f), 0x88000000.toInt())
        }
        topColumn.addView(dateTextView)

        // 3. Giant iOS 26 Liquid Glass Clock
        clockView = IosLockClockView(this, prefs.lockScreenClockStyle)
        topColumn.addView(
            clockView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                IosDesign.dpInt(this, 120f)
            ).apply {
                topMargin = -IosDesign.dpInt(this@IosLockScreenActivity, 4f)
            }
        )

        // 4. Inline iOS Lock Screen Widgets Row (Battery Ring + Day + Free RAM)
        val widgetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val glassSurfaceStyle = prefs.glassThemeStyle
        batteryPill = MiniBatteryRingView(this).apply {
            background = IosDesign.createThemeSurface(this@IosLockScreenActivity, 999f, glassSurfaceStyle, IconThemeStyle.DARK)
        }
        dayPill = createMiniGlassChip(glassSurfaceStyle)
        ramPill = createMiniGlassChip(glassSurfaceStyle)

        val chipH = IosDesign.dpInt(this, 36f)
        widgetRow.addView(batteryPill, LinearLayout.LayoutParams(IosDesign.dpInt(this, 78f), chipH).apply {
            marginEnd = IosDesign.dpInt(this@IosLockScreenActivity, 8f)
        })
        widgetRow.addView(dayPill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, chipH).apply {
            marginEnd = IosDesign.dpInt(this@IosLockScreenActivity, 8f)
        })
        widgetRow.addView(ramPill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, chipH))
        topColumn.addView(widgetRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = IosDesign.dpInt(this@IosLockScreenActivity, 8f)
        })

        // 5. Stacked Liquid Glass Notifications
        notificationsColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        topColumn.addView(notificationsColumn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = IosDesign.dpInt(this@IosLockScreenActivity, 22f)
        })

        contentLayer.addView(
            topColumn,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP)
        )

        // 6. Bottom Quick-Action Circular Glass Buttons (Flashlight & Camera)
        bottomBarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hp = IosDesign.dpInt(this@IosLockScreenActivity, 36f)
            setPadding(hp, 0, hp, 0)
        }
        val btnSize = IosDesign.dpInt(this, 52f)
        flashlightBtn = CircularQuickActionView(this, isFlashlight = true).apply {
            background = IosDesign.createThemeSurface(this@IosLockScreenActivity, 999f, glassSurfaceStyle, IconThemeStyle.DARK)
            contentDescription = "Flashlight"
            setOnClickListener {
                IosDesign.hapticTick(this, prefs.hapticsEnabled)
                toggleTorch()
            }
        }
        cameraBtn = CircularQuickActionView(this, isFlashlight = false).apply {
            background = IosDesign.createThemeSurface(this@IosLockScreenActivity, 999f, glassSurfaceStyle, IconThemeStyle.DARK)
            contentDescription = "Camera"
            setOnClickListener {
                IosDesign.hapticTick(this, prefs.hapticsEnabled)
                unlockAndLaunchCamera()
            }
        }
        bottomBarRow.addView(flashlightBtn, LinearLayout.LayoutParams(btnSize, btnSize))
        bottomBarRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bottomBarRow.addView(cameraBtn, LinearLayout.LayoutParams(btnSize, btnSize))

        contentLayer.addView(
            bottomBarRow,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                bottomMargin = IosDesign.dpInt(this@IosLockScreenActivity, 54f)
            }
        )

        // 7. Swipe-up hint + iOS Home Indicator pill
        swipeHintTv = TextView(this).apply {
            text = if (isPreviewMode) "Swipe up to exit preview" else "Swipe up to unlock"
            setTextColor(0xB8FFFFFF.toInt())
            textSize = 12.5f
            typeface = IosDesign.TYPEFACE_MEDIUM
            gravity = Gravity.CENTER
            setShadowLayer(IosDesign.dp(this@IosLockScreenActivity, 3f), 0f, IosDesign.dp(this@IosLockScreenActivity, 1f), 0x80000000.toInt())
        }
        contentLayer.addView(
            swipeHintTv,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = IosDesign.dpInt(this@IosLockScreenActivity, 30f)
            }
        )

        homeIndicator = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = IosDesign.dp(this@IosLockScreenActivity, 999f)
                setColor(0xDDFFFFFF.toInt())
            }
        }
        contentLayer.addView(
            homeIndicator,
            FrameLayout.LayoutParams(
                IosDesign.dpInt(this, 134f),
                IosDesign.dpInt(this, 5f),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = IosDesign.dpInt(this@IosLockScreenActivity, 14f)
            }
        )

        rootFrame.addView(contentLayer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        rootFrame.setOnTouchListener { _, event -> handleLockTouch(event) }
    }

    private fun createMiniGlassChip(glassStyle: GlassThemeStyle): TextView {
        return TextView(this).apply {
            setTextColor(0xEEFFFFFF.toInt())
            textSize = 12.5f
            typeface = IosDesign.TYPEFACE_MEDIUM_BOLD
            gravity = Gravity.CENTER
            background = IosDesign.createThemeSurface(this@IosLockScreenActivity, 999f, glassStyle, IconThemeStyle.DARK)
            val hp = IosDesign.dpInt(this@IosLockScreenActivity, 13f)
            setPadding(hp, 0, hp, 0)
        }
    }

    private fun handleLockTouch(ev: MotionEvent): Boolean {
        if (isUnlocking) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                draggingUnlock = false
                releaseVelocityTracker()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(ev) }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val dy = ev.y - downY
                val dx = ev.x - downX
                if (!draggingUnlock && dy < -touchSlop && abs(dy) > abs(dx)) {
                    draggingUnlock = true
                }
                if (draggingUnlock) {
                    val clampedUp = dy.coerceAtMost(0f)
                    val progress = (abs(clampedUp) / IosDesign.dp(this, 260f)).coerceIn(0f, 1f)
                    contentLayer.translationY = clampedUp * 0.72f
                    contentLayer.alpha = (1f - progress * 0.75f).coerceIn(0.25f, 1f)
                    val scale = 1f - progress * 0.04f
                    contentLayer.scaleX = scale
                    contentLayer.scaleY = scale
                    lockGlyphView.unlockProgress = progress
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(ev)
                velocityTracker?.computeCurrentVelocity(1000)
                val vy = velocityTracker?.yVelocity ?: 0f
                releaseVelocityTracker()

                if (draggingUnlock) {
                    val pulledUp = downY - ev.y
                    val threshold = IosDesign.dp(this, 105f)
                    val flingUp = vy < -IosDesign.dp(this, 900f)
                    if (ev.actionMasked == MotionEvent.ACTION_UP && (pulledUp >= threshold || flingUp)) {
                        performUnlock(onUnlocked = null)
                    } else {
                        // Spring back smoothly
                        contentLayer.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(220L)
                            .setInterpolator(IosDesign.RELEASE_OVERSHOOT)
                            .start()
                        lockGlyphView.unlockProgress = 0f
                    }
                    draggingUnlock = false
                } else if (ev.actionMasked == MotionEvent.ACTION_UP &&
                    abs(ev.x - downX) <= touchSlop && abs(ev.y - downY) <= touchSlop
                ) {
                    val now = SystemClock.uptimeMillis()
                    if (lastTapMs > 0L && (now - lastTapMs) in 40L..doubleTapTimeoutMs &&
                        abs(ev.x - lastTapX) <= doubleTapSlop && abs(ev.y - lastTapY) <= doubleTapSlop
                    ) {
                        lastTapMs = 0L
                        if (!isPreviewMode && prefs.doubleTapToLockEnabled && ScreenLockAccessibilityService.isConnected()) {
                            IosDesign.hapticTick(rootFrame, prefs.hapticsEnabled)
                            ScreenLockAccessibilityService.lockScreenNow()
                        }
                    } else {
                        lastTapMs = now
                        lastTapX = ev.x
                        lastTapY = ev.y
                    }
                }
                return true
            }
        }
        return false
    }

    private fun performUnlock(onUnlocked: (() -> Unit)?) {
        if (isUnlocking) return
        isUnlocking = true
        IosDesign.hapticTick(rootFrame, prefs.hapticsEnabled)

        contentLayer.animate()
            .translationY(-IosDesign.dp(this, 180f))
            .alpha(0f)
            .setDuration(170L)
            .setInterpolator(DecelerateInterpolator(2f))
            .withEndAction {
                if (isPreviewMode) {
                    onUnlocked?.invoke()
                    finishWithoutAnimation()
                    return@withEndAction
                }
                val km = getSystemService(KeyguardManager::class.java)
                if (km != null && km.isKeyguardLocked) {
                    km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            onUnlocked?.invoke()
                            finishWithoutAnimation()
                        }

                        override fun onDismissCancelled() {
                            isUnlocking = false
                            contentLayer.translationY = 0f
                            contentLayer.alpha = 1f
                            contentLayer.scaleX = 1f
                            contentLayer.scaleY = 1f
                            lockGlyphView.unlockProgress = 0f
                        }

                        override fun onDismissError() {
                            onUnlocked?.invoke()
                            finishWithoutAnimation()
                        }
                    })
                } else {
                    onUnlocked?.invoke()
                    finishWithoutAnimation()
                }
            }
            .start()
    }

    private fun unlockAndLaunchCamera() {
        performUnlock {
            val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { startActivity(camIntent) }
        }
    }

    private fun finishWithoutAnimation() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun updateClockAndStats() {
        val nowMs = System.currentTimeMillis()
        dateBuf.time = nowMs
        dateTextView.text = dateFormat.format(dateBuf)
        clockView.timeText = timeFormat.format(dateBuf)
        dayPill.text = shortDayFormat.format(dateBuf).uppercase(Locale.getDefault())

        // Live Battery
        val batIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = (batIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val pct = if (level >= 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 100
        val status = batIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        batteryPill.updateBattery(pct, charging)

        // Free RAM
        val am = getSystemService(ActivityManager::class.java)
        am?.getMemoryInfo(memInfo)
        val availGb = memInfo.availMem / (1024f * 1024f * 1024f)
        ramPill.text = String.format(Locale.US, "%.1f GB", availGb)
    }

    private fun renderLockNotifications() {
        notificationsColumn.removeAllViews()
        if (!prefs.lockScreenShowNotifications) return

        val items = BadgeStore.recentNotifications().take(4)
        if (items.isEmpty()) return

        val nowMs = System.currentTimeMillis()
        for (item in items) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = IosDesign.createThemeSurface(
                    this@IosLockScreenActivity,
                    20f,
                    prefs.glassThemeStyle,
                    IconThemeStyle.DARK,
                    strongSurface = true
                )
                val hp = IosDesign.dpInt(this@IosLockScreenActivity, 14f)
                val vp = IosDesign.dpInt(this@IosLockScreenActivity, 10f)
                setPadding(hp, vp, hp, vp)
                setOnClickListener {
                    performUnlock {
                        val launch = packageManager.getLaunchIntentForPackage(item.packageName)
                        if (launch != null) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { startActivity(launch) }
                        }
                    }
                }
            }

            val topMeta = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            topMeta.addView(TextView(this).apply {
                text = item.appName.uppercase(Locale.getDefault())
                setTextColor(0xA6FFFFFF.toInt())
                textSize = 11f
                typeface = IosDesign.TYPEFACE_MEDIUM_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            topMeta.addView(TextView(this).apply {
                text = formatRelativeTime(nowMs - item.postTime)
                setTextColor(0x88FFFFFF.toInt())
                textSize = 11f
                typeface = IosDesign.TYPEFACE_REGULAR
            })
            card.addView(topMeta)

            card.addView(TextView(this).apply {
                text = item.title
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = IosDesign.TYPEFACE_MEDIUM_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, IosDesign.dpInt(this@IosLockScreenActivity, 2f), 0, 0)
            })
            if (item.text.isNotEmpty()) {
                card.addView(TextView(this).apply {
                    text = item.text
                    setTextColor(0xCCFFFFFF.toInt())
                    textSize = 13f
                    typeface = IosDesign.TYPEFACE_REGULAR
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
            }

            notificationsColumn.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = IosDesign.dpInt(this@IosLockScreenActivity, 8f)
            })
        }
    }

    private fun formatRelativeTime(deltaMs: Long): String {
        val mins = (deltaMs / 60_000L).coerceAtLeast(0L)
        return when {
            mins < 1L -> "now"
            mins < 60L -> "${mins}m ago"
            else -> "${mins / 60L}h ago"
        }
    }

    private fun resolveTorchCameraId() {
        try {
            val cm = getSystemService(CameraManager::class.java) ?: return
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    torchCameraId = id
                    return
                }
            }
        } catch (_: Exception) {
            torchCameraId = null
        }
    }

    private fun toggleTorch() {
        val next = !torchEnabled
        if (!setTorchState(next)) {
            Toast.makeText(this, "Flashlight unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setTorchState(on: Boolean): Boolean {
        val camId = torchCameraId ?: return false
        return try {
            val cm = getSystemService(CameraManager::class.java) ?: return false
            cm.setTorchMode(camId, on)
            torchEnabled = on
            flashlightBtn.isActive = on
            flashlightBtn.background = if (on) {
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xF0FFFFFF.toInt())
                }
            } else {
                IosDesign.createThemeSurface(this, 999f, prefs.glassThemeStyle, IconThemeStyle.DARK)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun registerLiveReceiver() {
        if (tickRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(liveReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(liveReceiver, filter)
        }
        tickRegistered = true
    }

    private fun unregisterLiveReceiver() {
        if (!tickRegistered) return
        try {
            unregisterReceiver(liveReceiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered
        }
        tickRegistered = false
    }

    private fun releaseVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    // -------------------------------------------------------------------------
    // Custom Zero-Allocation Apple iOS 26 Lock Screen Views
    // -------------------------------------------------------------------------

    private class IosLockClockView(
        context: Context,
        private val clockStyle: LockClockStyle
    ) : View(context) {

        var timeText: String = "09:41"
            set(value) {
                if (field != value) {
                    field = value
                    invalidate()
                }
            }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            letterSpacing = -0.04f
            typeface = when (clockStyle) {
                LockClockStyle.LIQUID_GLASS -> IosDesign.TYPEFACE_MEDIUM_BOLD
                LockClockStyle.BOLD_CLASSIC -> Typeface.create("sans-serif", Typeface.BOLD)
                LockClockStyle.THIN_ELEGANT -> IosDesign.TYPEFACE_LIGHT
            }
            setShadowLayer(
                IosDesign.dp(context, 10f),
                0f,
                IosDesign.dp(context, 4f),
                0x52000000
            )
        }

        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            letterSpacing = -0.04f
            style = Paint.Style.STROKE
            strokeWidth = IosDesign.dp(context, 1.35f)
            typeface = fillPaint.typeface
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val targetPx = minOf(h * 0.86f, w * 0.29f).coerceAtLeast(IosDesign.sp(context, 62f))
            fillPaint.textSize = targetPx
            rimPaint.textSize = targetPx

            if (clockStyle == LockClockStyle.LIQUID_GLASS && h > 0) {
                fillPaint.shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    intArrayOf(0xE8FFFFFF.toInt(), 0xB2EBF2FF.toInt(), 0xCEFFFFFF.toInt()),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
                rimPaint.shader = LinearGradient(
                    0f, 0f, w.toFloat() * 0.4f, h.toFloat(),
                    intArrayOf(0xB8FFFFFF.toInt(), 0x3AFFFFFF, 0x84FFFFFF.toInt()),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            } else {
                fillPaint.shader = null
                fillPaint.color = 0xF5FFFFFF.toInt()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return
            val cx = width / 2f
            val baseline = height / 2f - (fillPaint.descent() + fillPaint.ascent()) / 2f
            canvas.drawText(timeText, cx, baseline, fillPaint)
            if (clockStyle == LockClockStyle.LIQUID_GLASS) {
                canvas.drawText(timeText, cx, baseline, rimPaint)
            }
        }
    }

    private class LockGlyphView(context: Context) : View(context) {
        var unlockProgress: Float = 0f
            set(value) {
                val c = value.coerceIn(0f, 1f)
                if (field != c) {
                    field = c
                    invalidate()
                }
            }

        private val bodyRect = RectF()
        private val shackleRect = RectF()
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xEAFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        private val shacklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xEAFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = IosDesign.dp(context, 1.9f)
            strokeCap = Paint.Cap.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val s = minOf(width, height).toFloat()
            if (s <= 0f) return
            val lift = unlockProgress * s * 0.14f
            shackleRect.set(s * 0.28f, s * 0.14f - lift, s * 0.72f, s * 0.58f - lift)
            canvas.drawArc(shackleRect, 180f, 180f - unlockProgress * 35f, false, shacklePaint)
            bodyRect.set(s * 0.19f, s * 0.44f, s * 0.81f, s * 0.90f)
            canvas.drawRoundRect(bodyRect, s * 0.13f, s * 0.13f, fillPaint)
        }
    }

    private class MiniBatteryRingView(context: Context) : View(context) {
        private var pct: Int = 100
        private var charging: Boolean = false
        private val ringRect = RectF()

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x38FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = IosDesign.dp(context, 2.5f)
        }
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = IosDesign.dp(context, 2.5f)
            strokeCap = Paint.Cap.ROUND
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xF0FFFFFF.toInt()
            textSize = IosDesign.sp(context, 12f)
            typeface = IosDesign.TYPEFACE_MEDIUM_BOLD
            textAlign = Paint.Align.LEFT
        }

        fun updateBattery(percent: Int, isCharging: Boolean) {
            pct = percent
            charging = isCharging
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val h = height.toFloat()
            if (width <= 0 || h <= 0f) return
            val r = h * 0.28f
            val cx = IosDesign.dp(context, 18f)
            val cy = h / 2f
            ringRect.set(cx - r, cy - r, cx + r, cy + r)
            arcPaint.color = when {
                charging -> IosDesign.IOS_GREEN
                pct <= 20 -> IosDesign.IOS_RED
                pct <= 40 -> IosDesign.IOS_ORANGE
                else -> IosDesign.IOS_GREEN
            }
            canvas.drawArc(ringRect, 0f, 360f, false, trackPaint)
            canvas.drawArc(ringRect, -90f, (pct / 100f) * 360f, false, arcPaint)
            val label = if (charging) "⚡$pct%" else "$pct%"
            val base = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, cx + r + IosDesign.dp(context, 7f), base, textPaint)
        }
    }

    private class CircularQuickActionView(
        context: Context,
        private val isFlashlight: Boolean
    ) : View(context) {

        var isActive: Boolean = false
            set(value) {
                if (field != value) {
                    field = value
                    invalidate()
                }
            }

        private val rect = RectF()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            isClickable = true
            isFocusable = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val s = minOf(width, height).toFloat()
            if (s <= 0f) return
            val color = if (isActive) 0xFF1C1C1E.toInt() else 0xF2FFFFFF.toInt()
            paint.color = color
            paint.style = Paint.Style.FILL

            if (isFlashlight) {
                // Crisp vector Apple Flashlight glyph
                rect.set(s * 0.36f, s * 0.23f, s * 0.64f, s * 0.34f)
                canvas.drawRoundRect(rect, s * 0.04f, s * 0.04f, paint)
                rect.set(s * 0.42f, s * 0.36f, s * 0.58f, s * 0.77f)
                canvas.drawRoundRect(rect, s * 0.05f, s * 0.05f, paint)
                // Switch indicator dot
                paint.color = if (isActive) 0xFF34C759.toInt() else 0x881C1C1E.toInt()
                canvas.drawCircle(s * 0.50f, s * 0.48f, s * 0.036f, paint)
            } else {
                // Crisp vector Apple Camera glyph
                rect.set(s * 0.38f, s * 0.28f, s * 0.62f, s * 0.36f)
                canvas.drawRoundRect(rect, s * 0.04f, s * 0.04f, paint)
                rect.set(s * 0.23f, s * 0.34f, s * 0.77f, s * 0.71f)
                canvas.drawRoundRect(rect, s * 0.08f, s * 0.08f, paint)
                paint.color = 0xAA1C1C1E.toInt()
                canvas.drawCircle(s * 0.50f, s * 0.53f, s * 0.115f, paint)
                paint.color = color
                canvas.drawCircle(s * 0.50f, s * 0.53f, s * 0.065f, paint)
            }
        }
    }

    companion object {
        const val EXTRA_PREVIEW = "extra_preview"

        @Volatile
        var isShowing: Boolean = false
            private set
    }
}
