package com.iospring.launcher.widget

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.iospring.launcher.model.BuiltInWidgetType
import com.iospring.launcher.model.HomeItem
import com.iospring.launcher.ui.IosDesign
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

interface RefreshableIosWidget {
    fun refreshWidgetData()
}

object IosBuiltInWidgetFactory {
    fun createView(
        context: Context,
        item: HomeItem.BuiltInWidgetItem,
        onEditNote: ((HomeItem.BuiltInWidgetItem) -> Unit)? = null,
        onOpenCalendar: (() -> Unit)? = null,
        onOpenClock: (() -> Unit)? = null
    ): View {
        val wrapper = FrameLayout(context)
        val pad = IosDesign.dpInt(context, 6f)
        wrapper.setPadding(pad, pad, pad, pad)

        val widgetView: View = when (item.widgetType) {
            BuiltInWidgetType.CLOCK_2X2 -> IosClockWidgetView(context).apply {
                setOnLongClickListener {
                    onOpenClock?.invoke()
                    true
                }
            }
            BuiltInWidgetType.CALENDAR_2X2 -> IosCalendarWidgetView(context).apply {
                setOnClickListener { onOpenCalendar?.invoke() }
            }
            BuiltInWidgetType.BATTERY_2X2 -> IosBatteryWidgetView(context)
            BuiltInWidgetType.QUICK_STATUS_4X2 -> IosQuickStatusWidgetView(context)
            BuiltInWidgetType.NOTES_4X2 -> IosNotesWidgetView(context, item.customNote).apply {
                setOnClickListener { onEditNote?.invoke(item) }
            }
        }
        wrapper.addView(widgetView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        return wrapper
    }
}

/**
 * Signature iOS 18 2x2 Clock Widget with tap-to-toggle Analog / Digital display.
 */
class IosClockWidgetView(context: Context) : View(context), RefreshableIosWidget {

    private var digitalMode: Boolean = false
    private val cardRect = RectF()
    private val cornerRadius = IosDesign.dp(context, 22f)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = IosDesign.IOS_ORANGE
        style = Paint.Style.FILL
    }
    private val digitalTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-light", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    init {
        isClickable = true
        setOnClickListener {
            digitalMode = !digitalMode
            invalidate()
        }
    }

    override fun refreshWidgetData() {
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cal = Calendar.getInstance()
        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val isNight = hour24 < 6 || hour24 >= 18

        cardRect.set(0f, 0f, w, h)
        bgPaint.color = if (isNight) 0xE61C1C1E.toInt() else 0xEBFFFFFF.toInt()
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

        if (digitalMode) {
            drawDigital(canvas, w, h, cal, isNight)
        } else {
            drawAnalog(canvas, w, h, hour24, minute, isNight)
        }
    }

    private fun drawAnalog(canvas: Canvas, w: Float, h: Float, hour24: Int, minute: Int, isNight: Boolean) {
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.42f

        dialPaint.color = if (isNight) 0xFF2C2C2E.toInt() else 0xFFF5F5F7.toInt()
        canvas.drawCircle(cx, cy, radius, dialPaint)

        // 12 hour ticks
        for (i in 0 until 12) {
            val angle = (i * 30.0 - 90.0) * PI / 180.0
            val major = i % 3 == 0
            val inner = radius * if (major) 0.80f else 0.87f
            val outer = radius * 0.94f
            tickPaint.color = if (isNight) {
                if (major) Color.WHITE else 0x88FFFFFF.toInt()
            } else {
                if (major) 0xFF1C1C1E.toInt() else 0x881C1C1E.toInt()
            }
            tickPaint.strokeWidth = IosDesign.dp(context, if (major) 2.0f else 1.2f)
            canvas.drawLine(
                cx + (cos(angle) * inner).toFloat(),
                cy + (sin(angle) * inner).toFloat(),
                cx + (cos(angle) * outer).toFloat(),
                cy + (sin(angle) * outer).toFloat(),
                tickPaint
            )
        }

        // Cardinal numbers 12, 3, 6, 9
        numPaint.color = if (isNight) Color.WHITE else 0xFF1C1C1E.toInt()
        numPaint.textSize = radius * 0.25f
        val numDist = radius * 0.65f
        val cardinals = listOf("12" to -90.0, "3" to 0.0, "6" to 90.0, "9" to 180.0)
        for ((txt, deg) in cardinals) {
            val rad = deg * PI / 180.0
            val nx = cx + (cos(rad) * numDist).toFloat()
            val ny = cy + (sin(rad) * numDist).toFloat() - (numPaint.descent() + numPaint.ascent()) / 2f
            canvas.drawText(txt, nx, ny, numPaint)
        }

        // Hour hand
        val hourAngle = (((hour24 % 12) + minute / 60.0) * 30.0 - 90.0) * PI / 180.0
        handPaint.color = if (isNight) Color.WHITE else 0xFF1C1C1E.toInt()
        handPaint.strokeWidth = IosDesign.dp(context, 3.6f)
        canvas.drawLine(
            cx, cy,
            cx + (cos(hourAngle) * radius * 0.48f).toFloat(),
            cy + (sin(hourAngle) * radius * 0.48f).toFloat(),
            handPaint
        )

        // Minute hand
        val minAngle = (minute * 6.0 - 90.0) * PI / 180.0
        handPaint.strokeWidth = IosDesign.dp(context, 2.5f)
        canvas.drawLine(
            cx, cy,
            cx + (cos(minAngle) * radius * 0.72f).toFloat(),
            cy + (sin(minAngle) * radius * 0.72f).toFloat(),
            handPaint
        )

        // iOS Orange center pin
        canvas.drawCircle(cx, cy, IosDesign.dp(context, 3.4f), accentPaint)
    }

    private fun drawDigital(canvas: Canvas, w: Float, h: Float, cal: Calendar, isNight: Boolean) {
        val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
        val dayText = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(cal.time)
        digitalTimePaint.color = if (isNight) Color.WHITE else 0xFF1C1C1E.toInt()
        digitalTimePaint.textSize = minOf(w, h) * 0.28f
        canvas.drawText(timeText, w / 2f, h * 0.50f, digitalTimePaint)

        subPaint.color = IosDesign.IOS_ORANGE
        subPaint.textSize = minOf(w, h) * 0.105f
        canvas.drawText(dayText.uppercase(Locale.getDefault()), w / 2f, h * 0.70f, subPaint)

        subPaint.color = if (isNight) 0x88FFFFFF.toInt() else 0x881C1C1E.toInt()
        subPaint.textSize = minOf(w, h) * 0.085f
        canvas.drawText("TAP FOR ANALOG", w / 2f, h * 0.85f, subPaint)
    }
}

/**
 * Authentic iOS 18 2x2 Calendar Widget (22dp corners, red weekday, large clean date).
 */
class IosCalendarWidgetView(context: Context) : LinearLayout(context), RefreshableIosWidget {

    private val weekdayView = TextView(context).apply {
        setTextColor(IosDesign.IOS_RED)
        textSize = 12f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private val dayNumberView = TextView(context).apply {
        setTextColor(0xFF1C1C1E.toInt())
        textSize = 36f
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        includeFontPadding = false
    }

    private val monthView = TextView(context).apply {
        setTextColor(0xFF636366.toInt())
        textSize = 12f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.TOP or Gravity.START
        val pad = IosDesign.dpInt(context, 16f)
        setPadding(pad, pad, pad, pad)
        background = IosDesign.createGlassDrawable(
            context = context,
            cornerRadiusDp = 22f,
            fillColor = 0xF0FFFFFF.toInt(),
            strokeColor = 0x30FFFFFF,
            strokeWidthDp = 0.8f
        )

        addView(weekdayView)
        addView(dayNumberView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = IosDesign.dpInt(context, 2f)
        })
        addView(monthView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = IosDesign.dpInt(context, 8f)
        })

        refreshWidgetData()
    }

    override fun refreshWidgetData() {
        val now = Date()
        weekdayView.text = SimpleDateFormat("EEEE", Locale.getDefault()).format(now).uppercase(Locale.getDefault())
        dayNumberView.text = SimpleDateFormat("d", Locale.getDefault()).format(now)
        monthView.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(now)
    }
}

/**
 * Authentic iOS 18 2x2 Batteries Widget showing Phone battery ring + connected Bluetooth battery ring.
 */
class IosBatteryWidgetView(context: Context) : View(context), RefreshableIosWidget {

    private var phonePct: Int = 100
    private var isCharging: Boolean = false
    private var btName: String? = null
    private var btPct: Int? = null
    private var lastBtPollMs: Long = 0L

    private val cardRect = RectF()
    private val ringRect = RectF()
    private val cornerRadius = IosDesign.dp(context, 22f)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC1C1C1E.toInt()
        style = Paint.Style.FILL
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    init {
        refreshWidgetData()
    }

    @SuppressLint("MissingPermission")
    override fun refreshWidgetData() {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            if (level >= 0) {
                phonePct = ((level * 100f) / scale).toInt().coerceIn(0, 100)
            }
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }

        // Safely check connected Bluetooth audio/accessory battery (Android 12+ permission guarded, throttled)
        val nowMs = SystemClock.elapsedRealtime()
        val canReadBt = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (canReadBt && (lastBtPollMs == 0L || nowMs - lastBtPollMs >= 30_000L)) {
            lastBtPollMs = nowMs
            btName = null
            btPct = null
            try {
                val bm = context.getSystemService(BluetoothManager::class.java)
                val bonded = bm?.adapter?.bondedDevices.orEmpty()
                for (device in bonded) {
                    val connected = runCatching {
                        device.javaClass.getMethod("isConnected").invoke(device) as? Boolean
                    }.getOrNull() == true
                    if (connected) {
                        val level = runCatching {
                            device.javaClass.getMethod("getBatteryLevel").invoke(device) as? Int
                        }.getOrNull()
                        if (level != null && level in 0..100) {
                            btName = device.name ?: "Bluetooth"
                            btPct = level
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore Bluetooth errors safely
            }
        } else if (!canReadBt) {
            btName = null
            btPct = null
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        cardRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

        val hasBt = btPct != null
        if (!hasBt) {
            val r = minOf(w, h) * 0.30f
            drawBatteryRing(canvas, w / 2f, h * 0.44f, r, phonePct, isCharging, "Phone")
        } else {
            val r = minOf(w, h) * 0.21f
            drawBatteryRing(canvas, w * 0.29f, h * 0.46f, r, phonePct, isCharging, "Phone")
            drawBatteryRing(canvas, w * 0.71f, h * 0.46f, r, btPct ?: 0, false, (btName ?: "BT").take(8))
        }
    }

    private fun drawBatteryRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        pct: Int,
        charging: Boolean,
        caption: String
    ) {
        val stroke = IosDesign.dp(context, 6f)
        trackPaint.strokeWidth = stroke
        progressPaint.strokeWidth = stroke
        progressPaint.color = when {
            charging -> IosDesign.IOS_GREEN
            pct <= 20 -> IosDesign.IOS_RED
            pct <= 40 -> IosDesign.IOS_ORANGE
            else -> IosDesign.IOS_GREEN
        }

        ringRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(ringRect, 0f, 360f, false, trackPaint)
        canvas.drawArc(ringRect, -90f, (pct / 100f) * 360f, false, progressPaint)

        pctPaint.textSize = radius * 0.56f
        val pctLabel = if (charging) "⚡$pct%" else "$pct%"
        val baseline = cy - (pctPaint.descent() + pctPaint.ascent()) / 2f
        canvas.drawText(pctLabel, cx, baseline, pctPaint)

        labelPaint.textSize = IosDesign.sp(context, 11f)
        canvas.drawText(caption, cx, cy + radius + IosDesign.dp(context, 18f), labelPaint)
    }
}

/**
 * 4x2 Frosted Glass Device Summary Widget (Greeting, Date, Battery & Free RAM).
 */
class IosQuickStatusWidgetView(context: Context) : LinearLayout(context), RefreshableIosWidget {

    private val titleTv = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 18f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val dateTv = TextView(context).apply {
        setTextColor(0xCCFFFFFF.toInt())
        textSize = 13f
    }
    private val statsTv = TextView(context).apply {
        setTextColor(0xEEFFFFFF.toInt())
        textSize = 12.5f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = IosDesign.dpInt(context, 18f)
        setPadding(pad, pad, pad, pad)
        background = IosDesign.createGlassDrawable(
            context = context,
            cornerRadiusDp = 22f,
            fillColor = 0x661C1C1E,
            strokeColor = 0x38FFFFFF
        )
        addView(titleTv)
        addView(dateTv, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = IosDesign.dpInt(context, 4f)
        })
        addView(statsTv, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = IosDesign.dpInt(context, 10f)
        })
        refreshWidgetData()
    }

    override fun refreshWidgetData() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        titleTv.text = when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
        dateTv.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())

        val am = context.getSystemService(ActivityManager::class.java)
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val availGb = memInfo.availMem / (1024f * 1024f * 1024f)

        val batIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = (batIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val pct = if (level >= 0) ((level * 100f) / scale).toInt() else 100

        statsTv.text = String.format(Locale.US, "Battery %d%%  •  %.1f GB RAM Free", pct, availGb)
    }
}

/**
 * Authentic iOS 18 Quick Note 4x2 Widget (yellow header bar + clean editable note body).
 */
class IosNotesWidgetView(context: Context, initialText: String) : LinearLayout(context), RefreshableIosWidget {

    private val bodyTv = TextView(context).apply {
        setTextColor(0xFF1C1C1E.toInt())
        textSize = 14f
        maxLines = 4
        text = initialText
    }

    init {
        orientation = VERTICAL
        background = IosDesign.createGlassDrawable(
            context = context,
            cornerRadiusDp = 22f,
            fillColor = 0xF4FFFDF5.toInt(),
            strokeColor = 0x33000000
        )
        clipToOutline = true

        val header = TextView(context).apply {
            text = "Quick Note"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setBackgroundColor(0xFFEAB308.toInt())
            val hp = IosDesign.dpInt(context, 14f)
            val vp = IosDesign.dpInt(context, 8f)
            setPadding(hp, vp, hp, vp)
        }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val bp = IosDesign.dpInt(context, 14f)
        bodyTv.setPadding(bp, IosDesign.dpInt(context, 10f), bp, bp)
        addView(bodyTv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun updateNoteText(text: String) {
        bodyTv.text = text
    }

    override fun refreshWidgetData() = Unit
}
