package com.iospring.launcher.core

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.iospring.launcher.model.IconThemeStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Built-in pure-vector iOS 18 Icon Pack renderer.
 * Zero external PNG bloat (low RAM, instant rendering, razor-sharp at any DPI).
 * Matches popular system & third-party apps with authentic iOS 18 Light, Dark, and Tinted styling.
 */
object IosIconPackRenderer {

    enum class IconGlyph {
        PHONE,
        MESSAGES,
        CAMERA,
        PHOTOS,
        CLOCK,
        CALENDAR,
        SETTINGS,
        MAIL,
        NOTES,
        WEATHER,
        CALCULATOR,
        MAPS,
        FILES,
        BROWSER,
        CONTACTS,
        APP_STORE,
        MUSIC,
        SPOTIFY,
        RECORDER,
        COMPASS,
        FLASHLIGHT,
        WALLET,
        TERMINAL,
        WHATSAPP,
        INSTAGRAM,
        TELEGRAM,
        YOUTUBE,
        TIKTOK,
        FACEBOOK,
        MESSENGER,
        X_TWITTER,
        DISCORD,
        NETFLIX,
        SHOPPING,
        RIDE_HAILING,
        VIDEO_CALL,
        GOOGLE_SEARCH,
        CLOUD_DRIVE,
        REDDIT,
        LINKEDIN,
        SNAPCHAT,
        PINTEREST,
        TWITCH,
        TRANSLATE
    }

    /**
     * Resolves an Android package (and optional label) to a built-in iOS 18 pack glyph,
     * or returns null so lesser-known / game apps keep their original squircle-masked icon.
     */
    fun matchGlyph(packageName: String, label: String = ""): IconGlyph? {
        val pkg = packageName.lowercase(Locale.US)
        val lbl = label.lowercase(Locale.US)

        return when {
            // Specific popular third-party apps first (avoid generic keyword collisions)
            pkg.contains("whatsapp") -> IconGlyph.WHATSAPP
            pkg.contains("instagram") -> IconGlyph.INSTAGRAM
            pkg.contains("telegram") || pkg.contains("org.thunderdog.challegram") -> IconGlyph.TELEGRAM
            pkg.contains("youtube.music") -> IconGlyph.MUSIC
            pkg.contains("youtube") || pkg.contains("vanced") || pkg.contains("revanced") -> IconGlyph.YOUTUBE
            pkg.contains("musically") || pkg.contains("trill") || pkg.contains("tiktok") -> IconGlyph.TIKTOK
            pkg.contains("facebook.orca") || pkg.contains("messenger") -> IconGlyph.MESSENGER
            pkg.contains("facebook.katana") || pkg.contains("facebook.lite") -> IconGlyph.FACEBOOK
            pkg.contains("twitter") || pkg == "com.x.android" -> IconGlyph.X_TWITTER
            pkg.contains("discord") -> IconGlyph.DISCORD
            pkg.contains("netflix") -> IconGlyph.NETFLIX
            pkg.contains("spotify") -> IconGlyph.SPOTIFY
            pkg.contains("reddit") -> IconGlyph.REDDIT
            pkg.contains("linkedin") -> IconGlyph.LINKEDIN
            pkg.contains("snapchat") -> IconGlyph.SNAPCHAT
            pkg.contains("pinterest") -> IconGlyph.PINTEREST
            pkg.contains("twitch") -> IconGlyph.TWITCH
            pkg.contains("zoom") || pkg.contains("tachyon") || pkg.contains("teams") || pkg.contains("meet") -> IconGlyph.VIDEO_CALL
            pkg.contains("shopee") || pkg.contains("tokopedia") || pkg.contains("lazada") ||
                pkg.contains("blibli") || pkg.contains("amazon.mshop") -> IconGlyph.SHOPPING
            pkg.contains("gojek") || pkg.contains("grab") || pkg.contains("uber") -> IconGlyph.RIDE_HAILING
            pkg.contains("dana") || pkg.contains("ovo") || pkg.contains("gopay") ||
                pkg.contains("wallet") || pkg.contains("bca") || pkg.contains("mandiri") ||
                pkg.contains("livin") || pkg.contains("brimo") || pkg.contains("seabank") ||
                pkg.contains("jago") || pkg.contains("paypal") -> IconGlyph.WALLET
            pkg.contains("apps.docs") || pkg.contains("drive") || pkg.contains("dropbox") ||
                pkg.contains("onedrive") -> IconGlyph.CLOUD_DRIVE
            pkg.contains("translate") -> IconGlyph.TRANSLATE
            pkg.contains("termux") || pkg.contains("terminal") -> IconGlyph.TERMINAL
            pkg == "com.google.android.googlequicksearchbox" -> IconGlyph.GOOGLE_SEARCH

            // Core iOS / Android system apps
            pkg.contains("phonemanager") || pkg.contains("clonephone") -> IconGlyph.SETTINGS
            pkg.contains("dialer") || pkg.contains("incallui") || pkg.contains("telecom") ||
                pkg == "com.android.phone" || lbl == "phone" || lbl == "telepon" -> IconGlyph.PHONE
            pkg.contains("messaging") || pkg.contains("mms") || pkg == "com.google.android.apps.messaging" ||
                lbl == "messages" || lbl == "pesan" -> IconGlyph.MESSAGES
            pkg.contains("camera") || lbl == "camera" || lbl == "kamera" -> IconGlyph.CAMERA
            pkg.contains("gallery") || pkg.contains("photos") || pkg.contains("album") ||
                lbl == "photos" || lbl == "gallery" || lbl == "galeri" -> IconGlyph.PHOTOS
            pkg.contains("deskclock") || pkg.contains("alarmclock") || pkg.contains(".clock") ||
                lbl == "clock" || lbl == "jam" -> IconGlyph.CLOCK
            pkg.contains("calendar") || lbl == "calendar" || lbl == "kalender" -> IconGlyph.CALENDAR
            pkg == "com.android.settings" || pkg.endsWith(".settings") ||
                lbl == "settings" || lbl == "pengaturan" -> IconGlyph.SETTINGS
            pkg.contains("gmail") || pkg.contains("email") || pkg.contains("outlook") ||
                pkg.contains("mail") -> IconGlyph.MAIL
            pkg.contains("notes") || pkg.contains("keep") || pkg.contains("notepad") ||
                pkg.contains("memo") || lbl == "notes" || lbl == "catatan" -> IconGlyph.NOTES
            pkg.contains("weather") || lbl == "weather" || lbl == "cuaca" -> IconGlyph.WEATHER
            pkg.contains("calculator") || pkg.contains("calc") ||
                lbl == "calculator" || lbl == "kalkulator" -> IconGlyph.CALCULATOR
            pkg.contains("maps") || pkg.contains("waze") || lbl == "maps" -> IconGlyph.MAPS
            pkg.contains("filemanager") || pkg.contains("documentsui") || pkg.contains("files") ||
                lbl == "files" || lbl == "file manager" -> IconGlyph.FILES
            pkg.contains("chrome") || pkg.contains("browser") || pkg.contains("firefox") ||
                pkg.contains("brave") || pkg.contains("opera") || pkg.contains("emmx") ||
                pkg.contains("safari") -> IconGlyph.BROWSER
            pkg.contains("contacts") || lbl == "contacts" || lbl == "kontak" -> IconGlyph.CONTACTS
            pkg == "com.android.vending" || pkg.contains("heytap.market") || pkg.contains("appmarket") ||
                lbl == "play store" || lbl == "app market" -> IconGlyph.APP_STORE
            pkg.contains("music") || pkg.contains("soundcloud") || pkg.contains("apple.android.music") -> IconGlyph.MUSIC
            pkg.contains("soundrecorder") || pkg.contains("voicerecorder") || pkg.contains("recorder") -> IconGlyph.RECORDER
            pkg.contains("compass") || lbl == "compass" || lbl == "kompas" -> IconGlyph.COMPASS
            pkg.contains("flashlight") || pkg.contains("torch") -> IconGlyph.FLASHLIGHT
            else -> null
        }
    }

    /**
     * Draws a crisp iOS 18 icon onto [canvas] (already clipped to the squircle mask).
     * Returns true if rendered by the built-in pack.
     */
    fun drawPackIcon(
        canvas: Canvas,
        size: Int,
        glyph: IconGlyph,
        style: IconThemeStyle,
        tintColor: Int
    ) {
        val s = size.toFloat()
        val isDark = style == IconThemeStyle.DARK
        val isTinted = style == IconThemeStyle.TINTED
        val fgAccent = if (isTinted) lightenColor(tintColor, 0.25f) else Color.WHITE

        when (glyph) {
            IconGlyph.PHONE -> {
                drawPlate(canvas, s, 0xFF66E877.toInt(), 0xFF1BC740.toInt(), style, tintColor)
                val color = if (isDark) 0xFF34D859.toInt() else fgAccent
                drawHandset(canvas, s, color)
            }

            IconGlyph.MESSAGES -> {
                drawPlate(canvas, s, 0xFF65E878.toInt(), 0xFF1EC842.toInt(), style, tintColor)
                val color = if (isDark) 0xFF34D859.toInt() else fgAccent
                drawSpeechBubble(canvas, s, color, withTailLeft = true)
            }

            IconGlyph.WHATSAPP -> {
                drawPlate(canvas, s, 0xFF5CE676.toInt(), 0xFF12B84A.toInt(), style, tintColor)
                val color = if (isDark) 0xFF38DE64.toInt() else fgAccent
                drawWhatsAppMark(canvas, s, color)
            }

            IconGlyph.CAMERA -> {
                drawPlate(canvas, s, 0xFFE5E5EA.toInt(), 0xFFAEAEB2.toInt(), style, tintColor, darkOverride = true)
                drawCameraGlyph(canvas, s, style, tintColor)
            }

            IconGlyph.PHOTOS -> {
                drawLightOrDarkPlate(canvas, s, style, tintColor)
                drawPhotosFlower(canvas, s, isTinted, tintColor)
            }

            IconGlyph.CLOCK -> {
                drawPlate(canvas, s, 0xFF232326.toInt(), 0xFF0E0E10.toInt(), style, tintColor, forceDark = true)
                drawClockFace(canvas, s, style, tintColor)
            }

            IconGlyph.CALENDAR -> {
                drawCalendarIcon(canvas, s, style, tintColor)
            }

            IconGlyph.SETTINGS -> {
                drawPlate(canvas, s, 0xFFD8D8DE.toInt(), 0xFF8E8E93.toInt(), style, tintColor, darkOverride = true)
                drawSettingsGear(canvas, s, style, tintColor)
            }

            IconGlyph.MAIL -> {
                drawPlate(canvas, s, 0xFF52C6FF.toInt(), 0xFF007AFF.toInt(), style, tintColor)
                val color = if (isDark) 0xFF47B5FF.toInt() else fgAccent
                drawEnvelope(canvas, s, color)
            }

            IconGlyph.NOTES -> {
                drawNotesIcon(canvas, s, style, tintColor)
            }

            IconGlyph.WEATHER -> {
                drawPlate(canvas, s, 0xFF4BA6FF.toInt(), 0xFF1C6AE4.toInt(), style, tintColor)
                drawWeatherGlyph(canvas, s, isTinted, tintColor)
            }

            IconGlyph.CALCULATOR -> {
                drawPlate(canvas, s, 0xFF2C2C2E.toInt(), 0xFF141416.toInt(), style, tintColor, forceDark = true)
                drawCalculatorGlyph(canvas, s, isTinted, tintColor)
            }

            IconGlyph.MAPS -> {
                drawMapsIcon(canvas, s, style, tintColor)
            }

            IconGlyph.FILES -> {
                drawLightOrDarkPlate(canvas, s, style, tintColor)
                val folderColor = if (isTinted) fgAccent else 0xFF1F9CF0.toInt()
                drawFolderGlyph(canvas, s, folderColor)
            }

            IconGlyph.BROWSER -> {
                drawLightOrDarkPlate(canvas, s, style, tintColor)
                drawSafariCompass(canvas, s, isTinted, tintColor)
            }

            IconGlyph.CONTACTS -> {
                drawContactsIcon(canvas, s, style, tintColor)
            }

            IconGlyph.APP_STORE -> {
                drawPlate(canvas, s, 0xFF39B2FF.toInt(), 0xFF0066F5.toInt(), style, tintColor)
                val color = if (isDark) 0xFF47B5FF.toInt() else fgAccent
                drawAppStoreA(canvas, s, color)
            }

            IconGlyph.MUSIC -> {
                drawPlate(canvas, s, 0xFFFF5E7E.toInt(), 0xFFFA233B.toInt(), style, tintColor)
                val color = if (isDark) 0xFFFF4D6D.toInt() else fgAccent
                drawMusicNote(canvas, s, color)
            }

            IconGlyph.SPOTIFY -> {
                drawPlate(canvas, s, 0xFF252528.toInt(), 0xFF121214.toInt(), style, tintColor, forceDark = true)
                val waveColor = if (isTinted) fgAccent else 0xFF1ED760.toInt()
                drawSpotifyWaves(canvas, s, waveColor)
            }

            IconGlyph.RECORDER -> {
                drawPlate(canvas, s, 0xFF242428.toInt(), 0xFF0E0E10.toInt(), style, tintColor, forceDark = true)
                val barColor = if (isTinted) fgAccent else 0xFFFF3B30.toInt()
                drawWaveform(canvas, s, barColor)
            }

            IconGlyph.COMPASS -> {
                drawPlate(canvas, s, 0xFF242428.toInt(), 0xFF0D0D0F.toInt(), style, tintColor, forceDark = true)
                drawCompassGlyph(canvas, s, isTinted, tintColor)
            }

            IconGlyph.FLASHLIGHT -> {
                drawPlate(canvas, s, 0xFF3A4154.toInt(), 0xFF181C26.toInt(), style, tintColor, forceDark = true)
                drawFlashlightGlyph(canvas, s, if (isTinted) fgAccent else 0xFFFFD60A.toInt())
            }

            IconGlyph.WALLET -> {
                drawPlate(canvas, s, 0xFF2C2C30.toInt(), 0xFF141418.toInt(), style, tintColor, forceDark = true)
                drawWalletGlyph(canvas, s, isTinted, tintColor)
            }

            IconGlyph.TERMINAL -> {
                drawPlate(canvas, s, 0xFF22242A.toInt(), 0xFF0F1014.toInt(), style, tintColor, forceDark = true)
                val promptColor = if (isTinted) fgAccent else 0xFF30D158.toInt()
                drawCenteredGlyphText(canvas, s, ">_", promptColor, s * 0.42f, Typeface.MONOSPACE)
            }

            IconGlyph.INSTAGRAM -> {
                drawInstagramPlate(canvas, s, style, tintColor)
                val color = if (isDark) 0xFFFF6B9D.toInt() else fgAccent
                drawInstagramCamera(canvas, s, color)
            }

            IconGlyph.TELEGRAM -> {
                drawPlate(canvas, s, 0xFF47C1FF.toInt(), 0xFF1D8FE1.toInt(), style, tintColor)
                val color = if (isDark) 0xFF47C1FF.toInt() else fgAccent
                drawPaperPlane(canvas, s, color)
            }

            IconGlyph.YOUTUBE -> {
                drawLightOrDarkPlate(canvas, s, style, tintColor)
                val pillColor = if (isTinted) fgAccent else 0xFFFF1E1E.toInt()
                drawYouTubeMark(canvas, s, pillColor, if (isDark || isTinted) 0xFF18181A.toInt() else Color.WHITE)
            }

            IconGlyph.TIKTOK -> {
                drawPlate(canvas, s, 0xFF1F1F23.toInt(), 0xFF09090B.toInt(), style, tintColor, forceDark = true)
                drawTikTokMark(canvas, s, isTinted, tintColor)
            }

            IconGlyph.FACEBOOK -> {
                drawPlate(canvas, s, 0xFF2D88FF.toInt(), 0xFF145CE0.toInt(), style, tintColor)
                val color = if (isDark) 0xFF3B8DFF.toInt() else fgAccent
                drawCenteredGlyphText(canvas, s, "f", color, s * 0.68f, Typeface.DEFAULT_BOLD, yOffset = s * 0.06f)
            }

            IconGlyph.MESSENGER -> {
                drawPlate(canvas, s, 0xFF9C4BFF.toInt(), 0xFF0084FF.toInt(), style, tintColor)
                val color = if (isDark) 0xFF7FA6FF.toInt() else fgAccent
                drawMessengerMark(canvas, s, color)
            }

            IconGlyph.X_TWITTER -> {
                drawPlate(canvas, s, 0xFF202024.toInt(), 0xFF070709.toInt(), style, tintColor, forceDark = true)
                drawCenteredGlyphText(canvas, s, "𝕏", fgAccent, s * 0.54f, Typeface.DEFAULT_BOLD)
            }

            IconGlyph.DISCORD -> {
                drawPlate(canvas, s, 0xFF7289FF.toInt(), 0xFF505CE8.toInt(), style, tintColor)
                val color = if (isDark) 0xFF798BFF.toInt() else fgAccent
                drawDiscordMark(canvas, s, color)
            }

            IconGlyph.NETFLIX -> {
                drawPlate(canvas, s, 0xFF1C1C1E.toInt(), 0xFF09090B.toInt(), style, tintColor, forceDark = true)
                val color = if (isTinted) fgAccent else 0xFFE50914.toInt()
                drawCenteredGlyphText(canvas, s, "N", color, s * 0.62f, Typeface.DEFAULT_BOLD)
            }

            IconGlyph.SHOPPING -> {
                drawPlate(canvas, s, 0xFFFF7A3D.toInt(), 0xFFEE3B2B.toInt(), style, tintColor)
                val color = if (isDark) 0xFFFF6D3B.toInt() else fgAccent
                drawShoppingBag(canvas, s, color)
            }

            IconGlyph.RIDE_HAILING -> {
                drawPlate(canvas, s, 0xFF35E075.toInt(), 0xFF00A859.toInt(), style, tintColor)
                val color = if (isDark) 0xFF2ED96C.toInt() else fgAccent
                drawRideMark(canvas, s, color)
            }

            IconGlyph.VIDEO_CALL -> {
                drawPlate(canvas, s, 0xFF4CC4FF.toInt(), 0xFF0B7BFF.toInt(), style, tintColor)
                val color = if (isDark) 0xFF4CC4FF.toInt() else fgAccent
                drawVideoCam(canvas, s, color)
            }

            IconGlyph.GOOGLE_SEARCH -> {
                drawLightOrDarkPlate(canvas, s, style, tintColor)
                drawGoogleG(canvas, s, isTinted, tintColor)
            }

            IconGlyph.CLOUD_DRIVE -> {
                drawLightOrDarkPlate(canvas, s, style, tintColor)
                drawCloudMark(canvas, s, if (isTinted) fgAccent else 0xFF0A84FF.toInt())
            }

            IconGlyph.REDDIT -> {
                drawPlate(canvas, s, 0xFFFF6633.toInt(), 0xFFE83A00.toInt(), style, tintColor)
                val color = if (isDark) 0xFFFF5C26.toInt() else fgAccent
                drawRedditMark(canvas, s, color)
            }

            IconGlyph.LINKEDIN -> {
                drawPlate(canvas, s, 0xFF2386D8.toInt(), 0xFF0A5CA8.toInt(), style, tintColor)
                val color = if (isDark) 0xFF3895E8.toInt() else fgAccent
                drawCenteredGlyphText(canvas, s, "in", color, s * 0.50f, Typeface.DEFAULT_BOLD)
            }

            IconGlyph.SNAPCHAT -> {
                drawPlate(canvas, s, 0xFFFFFC00.toInt(), 0xFFF2DE00.toInt(), style, tintColor)
                val ghostColor = if (isDark) 0xFFFFEA00.toInt() else fgAccent
                drawGhostMark(canvas, s, ghostColor, strokeDark = !isDark && !isTinted)
            }

            IconGlyph.PINTEREST -> {
                drawPlate(canvas, s, 0xFFF02B3D.toInt(), 0xFFC40C20.toInt(), style, tintColor)
                val color = if (isDark) 0xFFFF3B4E.toInt() else fgAccent
                drawCenteredGlyphText(canvas, s, "P", color, s * 0.58f, Typeface.SERIF)
            }

            IconGlyph.TWITCH -> {
                drawPlate(canvas, s, 0xFFA970FF.toInt(), 0xFF7B3FE4.toInt(), style, tintColor)
                val color = if (isDark) 0xFFB07BFF.toInt() else fgAccent
                drawSpeechBubble(canvas, s, color, withTailLeft = true)
            }

            IconGlyph.TRANSLATE -> {
                drawPlate(canvas, s, 0xFF42A5FF.toInt(), 0xFF1A6BFF.toInt(), style, tintColor)
                val color = if (isDark) 0xFF56B2FF.toInt() else fgAccent
                drawCenteredGlyphText(canvas, s, "A文", color, s * 0.36f, Typeface.DEFAULT_BOLD)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared Backdrop Plates
    // -------------------------------------------------------------------------

    private fun drawPlate(
        canvas: Canvas,
        s: Float,
        topColor: Int,
        bottomColor: Int,
        style: IconThemeStyle,
        tintColor: Int,
        forceDark: Boolean = false,
        darkOverride: Boolean = false
    ) {
        val (top, bottom) = when (style) {
            IconThemeStyle.LIGHT -> topColor to bottomColor
            IconThemeStyle.DARK -> {
                if (forceDark) topColor to bottomColor
                else if (darkOverride) 0xFF3A3A3E.toInt() to 0xFF1E1E22.toInt()
                else 0xFF2D2D31.toInt() to 0xFF161619.toInt()
            }
            IconThemeStyle.TINTED -> {
                blend(0xFF242428.toInt(), tintColor, 0.18f) to blend(0xFF131317.toInt(), tintColor, 0.08f)
            }
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, s, top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, s, s, paint)
    }

    private fun drawLightOrDarkPlate(canvas: Canvas, s: Float, style: IconThemeStyle, tintColor: Int) {
        drawPlate(canvas, s, 0xFFFFFFFF.toInt(), 0xFFF2F2F7.toInt(), style, tintColor)
    }

    private fun drawInstagramPlate(canvas: Canvas, s: Float, style: IconThemeStyle, tintColor: Int) {
        if (style != IconThemeStyle.LIGHT) {
            drawPlate(canvas, s, 0xFF2D2D31.toInt(), 0xFF161619.toInt(), style, tintColor)
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, s, s, 0f,
                intArrayOf(0xFFFFD521.toInt(), 0xFFF50057.toInt(), 0xFF9C27B0.toInt(), 0xFF4F5BD5.toInt()),
                floatArrayOf(0f, 0.38f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, s, s, paint)
    }

    // -------------------------------------------------------------------------
    // Individual Authentic iOS Vector Glyphs
    // -------------------------------------------------------------------------

    private fun drawHandset(canvas: Canvas, s: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.rotate(-135f, s / 2f, s / 2f)
        val arcRect = RectF(s * 0.24f, s * 0.30f, s * 0.76f, s * 0.78f)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = s * 0.135f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(arcRect, 200f, 140f, false, strokePaint)
        canvas.drawRoundRect(RectF(s * 0.17f, s * 0.44f, s * 0.35f, s * 0.62f), s * 0.05f, s * 0.05f, paint)
        canvas.drawRoundRect(RectF(s * 0.65f, s * 0.44f, s * 0.83f, s * 0.62f), s * 0.05f, s * 0.05f, paint)
        canvas.restore()
    }

    private fun drawSpeechBubble(canvas: Canvas, s: Float, color: Int, withTailLeft: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawOval(RectF(s * 0.18f, s * 0.21f, s * 0.82f, s * 0.73f), paint)
        val tail = Path().apply {
            if (withTailLeft) {
                moveTo(s * 0.34f, s * 0.66f)
                lineTo(s * 0.21f, s * 0.80f)
                lineTo(s * 0.47f, s * 0.70f)
            } else {
                moveTo(s * 0.66f, s * 0.66f)
                lineTo(s * 0.79f, s * 0.80f)
                lineTo(s * 0.53f, s * 0.70f)
            }
            close()
        }
        canvas.drawPath(tail, paint)
    }

    private fun drawWhatsAppMark(canvas: Canvas, s: Float, color: Int) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = s * 0.075f
        }
        canvas.drawCircle(s * 0.50f, s * 0.48f, s * 0.27f, ring)
        val tail = Path().apply {
            moveTo(s * 0.31f, s * 0.68f)
            lineTo(s * 0.21f, s * 0.80f)
            lineTo(s * 0.41f, s * 0.74f)
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawPath(tail, fill)
        canvas.save()
        canvas.scale(0.55f, 0.55f, s * 0.50f, s * 0.48f)
        drawHandset(canvas, s, color)
        canvas.restore()
    }

    private fun drawCameraGlyph(canvas: Canvas, s: Float, style: IconThemeStyle, tintColor: Int) {
        val bodyColor = when (style) {
            IconThemeStyle.LIGHT -> 0xFF242426.toInt()
            IconThemeStyle.DARK -> 0xFFD1D1D6.toInt()
            IconThemeStyle.TINTED -> lightenColor(tintColor, 0.25f)
        }
        val innerColor = when (style) {
            IconThemeStyle.LIGHT -> 0xFFE5E5EA.toInt()
            IconThemeStyle.DARK -> 0xFF1C1C1E.toInt()
            IconThemeStyle.TINTED -> 0xFF18181C.toInt()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bodyColor; this.style = Paint.Style.FILL }
        // Top viewfinder bump
        canvas.drawRoundRect(RectF(s * 0.35f, s * 0.24f, s * 0.65f, s * 0.36f), s * 0.06f, s * 0.06f, paint)
        // Camera body
        canvas.drawRoundRect(RectF(s * 0.16f, s * 0.31f, s * 0.84f, s * 0.76f), s * 0.10f, s * 0.10f, paint)
        // Lens outer ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = innerColor
            this.style = Paint.Style.STROKE
            strokeWidth = s * 0.045f
        }
        canvas.drawCircle(s * 0.50f, s * 0.54f, s * 0.15f, ringPaint)
        canvas.drawCircle(s * 0.50f, s * 0.54f, s * 0.07f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = innerColor })
        // Iconic yellow/amber flash indicator dot
        if (style != IconThemeStyle.TINTED) {
            canvas.drawCircle(s * 0.73f, s * 0.40f, s * 0.032f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFCC00.toInt()
            })
        }
    }

    private fun drawPhotosFlower(canvas: Canvas, s: Float, isTinted: Boolean, tintColor: Int) {
        val petalColors = intArrayOf(
            0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(),
            0xFF32ADE6.toInt(), 0xFF007AFF.toInt(), 0xFFAF52DE.toInt(), 0xFFFF2D55.toInt()
        )
        val cx = s * 0.50f
        val cy = s * 0.50f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (i in 0 until 8) {
            canvas.save()
            canvas.rotate(i * 45f, cx, cy)
            val base = if (isTinted) lightenColor(tintColor, (i % 3) * 0.12f) else petalColors[i]
            paint.color = (base and 0x00FFFFFF) or 0xCC000000.toInt()
            canvas.drawRoundRect(
                RectF(cx - s * 0.085f, cy - s * 0.34f, cx + s * 0.085f, cy - s * 0.05f),
                s * 0.085f, s * 0.085f, paint
            )
            canvas.restore()
        }
    }

    private fun drawClockFace(canvas: Canvas, s: Float, style: IconThemeStyle, tintColor: Int) {
        val cx = s * 0.50f
        val cy = s * 0.50f
        val r = s * 0.39f
        val dialColor = if (style == IconThemeStyle.LIGHT) Color.WHITE else 0xFF2C2C2E.toInt()
        val handColor = if (style == IconThemeStyle.LIGHT) 0xFF1C1C1E.toInt() else Color.WHITE
        val accent = if (style == IconThemeStyle.TINTED) lightenColor(tintColor, 0.25f) else 0xFFFF9500.toInt()

        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dialColor })
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = handColor
            strokeWidth = s * 0.018f
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 12) {
            val a = (i * 30.0 - 90.0) * PI / 180.0
            canvas.drawLine(
                cx + (cos(a) * r * 0.78f).toFloat(), cy + (sin(a) * r * 0.78f).toFloat(),
                cx + (cos(a) * r * 0.90f).toFloat(), cy + (sin(a) * r * 0.90f).toFloat(),
                tickPaint
            )
        }
        val cal = Calendar.getInstance()
        val hr = cal.get(Calendar.HOUR_OF_DAY) % 12
        val mn = cal.get(Calendar.MINUTE)
        val hrA = ((hr + mn / 60.0) * 30.0 - 90.0) * PI / 180.0
        val mnA = (mn * 6.0 - 90.0) * PI / 180.0
        val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = handColor
            strokeCap = Paint.Cap.ROUND
            strokeWidth = s * 0.042f
        }
        canvas.drawLine(cx, cy, cx + (cos(hrA) * r * 0.46f).toFloat(), cy + (sin(hrA) * r * 0.46f).toFloat(), hp)
        hp.strokeWidth = s * 0.028f
        canvas.drawLine(cx, cy, cx + (cos(mnA) * r * 0.68f).toFloat(), cy + (sin(mnA) * r * 0.68f).toFloat(), hp)
        canvas.drawCircle(cx, cy, s * 0.036f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
    }

    private fun drawCalendarIcon(canvas: Canvas, s: Float, style: IconThemeStyle, tintColor: Int) {
        drawLightOrDarkPlate(canvas, s, style, tintColor)
        val now = Calendar.getInstance().time
        val dayName = SimpleDateFormat("EEE", Locale.US).format(now).uppercase(Locale.US)
        val dayNum = SimpleDateFormat("d", Locale.US).format(now)

        val headerColor = if (style == IconThemeStyle.TINTED) lightenColor(tintColor, 0.25f) else 0xFFFF3B30.toInt()
        val numColor = if (style == IconThemeStyle.LIGHT) 0xFF1C1C1E.toInt() else Color.WHITE

        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = headerColor
            textSize = s * 0.175f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(dayName, s * 0.50f, s * 0.30f, dayPaint)

        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = numColor
            textSize = s * 0.48f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(dayNum, s * 0.50f, s * 0.77f, numPaint)
    }

    private fun drawSettingsGear(canvas: Canvas, s: Float, style: IconThemeStyle, tintColor: Int) {
        val cx = s * 0.50f
        val cy = s * 0.50f
        val gearColor = when (style) {
            IconThemeStyle.LIGHT -> 0xFF3A3A3C.toInt()
            IconThemeStyle.DARK -> 0xFFD1D1D6.toInt()
            IconThemeStyle.TINTED -> lightenColor(tintColor, 0.25f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gearColor
            this.style = Paint.Style.STROKE
            strokeWidth = s * 0.085f
        }
        canvas.drawCircle(cx, cy, s * 0.23f, paint)
        val tooth = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gearColor
            this.style = Paint.Style.FILL
        }
        for (i in 0 until 12) {
            canvas.save()
            canvas.rotate(i * 30f, cx, cy)
            canvas.drawRoundRect(
                RectF(cx - s * 0.042f, cy - s * 0.36f, cx + s * 0.042f, cy - s * 0.24f),
                s * 0.02f, s * 0.02f, tooth
            )
            canvas.restore()
        }
        canvas.drawCircle(cx, cy, s * 0.11f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gearColor
            this.style = Paint.Style.STROKE
            strokeWidth = s * 0.032f
        })
    }

    private fun drawEnvelope(canvas: Canvas, s: Float, color: Int) {
        val rect = RectF(s * 0.16f, s * 0.27f, s * 0.84f, s * 0.73f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, s * 0.07f, s * 0.07f, paint)
        val flap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0x38000000
            style = Paint.Style.STROKE
            strokeWidth = s * 0.042f
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path().apply {
            moveTo(rect.left + s * 0.03f, rect.top + s * 0.04f)
            lineTo(s * 0.50f, s * 0.54f)
            lineTo(rect.right - s * 0.03f, rect.top + s * 0.04f)
        }
        canvas.drawPath(path, flap)
    }

    private fun drawNotesIcon(canvas: Canvas, s: Float, style: IconThemeStyle, tintColor: Int) {
        drawLightOrDarkPlate(canvas, s, style, tintColor)
        val headerColor = if (style == IconThemeStyle.TINTED) lightenColor(tintColor, 0.2f) else 0xFFFFC300.toInt()
        canvas.drawRect(0f, 0f, s, s * 0.28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = headerColor })
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (style == IconThemeStyle.LIGHT) 0xFFD1D1D6.toInt() else 0x44FFFFFF
            strokeWidth = s * 0.022f
        }
        for (i in 1..3) {
            val y = s * (0.28f + i * 0.18f)
            canvas.drawLine(s * 0.14f, y, s * 0.86f, y, linePaint)
        }
    }

    private fun drawWeatherGlyph(canvas: Canvas, s: Float, isTinted: Boolean, tintColor: Int) {
        val sunColor = if (isTinted) lightenColor(tintColor, 0.35f) else 0xFFFFD60A.toInt()
        canvas.drawCircle(s * 0.60f, s * 0.38f, s * 0.17f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sunColor })
        val cloud = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isTinted) lightenColor(tintColor, 0.55f) else 0xF5FFFFFF.toInt()
        }
        canvas.drawCircle(s * 0.38f, s * 0.58f, s * 0.14f, cloud)
        canvas.drawCircle(s * 0.52f, s * 0.54f, s * 0.16f, cloud)
        canvas.drawCircle(s * 0.66f, s * 0.60f, s * 0.12f, cloud)
        canvas.drawRoundRect(RectF(s * 0.24f, s * 0.56f, s * 0.78f, s * 0.72f), s * 0.08f, s * 0.08f, cloud)
    }

    private fun drawCalculatorGlyph(canvas: Canvas, s: Float, isTinted: Boolean, tintColor: Int) {
        // Mini display bar
        canvas.drawRoundRect(
            RectF(s * 0.16f, s * 0.16f, s * 0.84f, s * 0.32f),
            s * 0.06f, s * 0.06f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
        )
        val accent = if (isTinted) lightenColor(tintColor, 0.25f) else 0xFFFF9F0A.toInt()
        val btn = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = s * 0.085f
        for (row in 0..2) {
            for (col in 0..2) {
                btn.color = if (col == 2) accent else if (row == 0) 0xFF8E8E93.toInt() else 0xFF48484A.toInt()
                val cx = s * (0.26f + col * 0.24f)
                val cy = s * (0.46f + row * 0.18f)
                canvas.drawCircle(cx, cy, r, btn)
            }
        }
    }

    private fun drawMapsIcon(canvas: Canvas, s: Float, style: IconThemeStyle, tintColor: Int) {
        if (style != IconThemeStyle.LIGHT) {
            drawPlate(canvas, s, 0xFF2D2D31.toInt(), 0xFF161619.toInt(), style, tintColor)
            val color = if (style == IconThemeStyle.TINTED) lightenColor(tintColor, 0.25f) else 0xFF34C759.toInt()
            drawNavArrow(canvas, s, color)
            return
        }
        canvas.drawRect(0f, 0f, s, s, Paint().apply { color = 0xFFF2EFE9.toInt() })
        // Green park
        canvas.drawRect(0f, 0f, s * 0.45f, s * 0.48f, Paint().apply { color = 0xFF7CD97B.toInt() })
        // Road bands
        val road = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = s * 0.12f }
        canvas.drawLine(0f, s * 0.52f, s, s * 0.52f, road)
        canvas.drawLine(s * 0.50f, 0f, s * 0.50f, s, road)
        // Blue route ribbon
        val route = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF007AFF.toInt()
            strokeWidth = s * 0.11f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(s * 0.50f, s * 0.85f, s * 0.50f, s * 0.36f, route)
        // Navigation chevron circle
        canvas.drawCircle(s * 0.50f, s * 0.50f, s * 0.20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF007AFF.toInt() })
        drawNavArrow(canvas, s, Color.WHITE, scale = 0.42f)
    }

    private fun drawNavArrow(canvas: Canvas, s: Float, color: Int, scale: Float = 0.72f) {
        canvas.save()
        canvas.scale(scale, scale, s * 0.50f, s * 0.50f)
        val p = Path().apply {
            moveTo(s * 0.50f, s * 0.22f)
            lineTo(s * 0.74f, s * 0.76f)
            lineTo(s * 0.50f, s * 0.64f)
            lineTo(s * 0.26f, s * 0.76f)
            close()
        }
        canvas.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        canvas.restore()
    }

    private fun drawFolderGlyph(canvas: Canvas, s: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(RectF(s * 0.15f, s * 0.25f, s * 0.48f, s * 0.42f), s * 0.06f, s * 0.06f, paint)
        canvas.drawRoundRect(RectF(s * 0.15f, s * 0.32f, s * 0.85f, s * 0.76f), s * 0.09f, s * 0.09f, paint)
    }

    private fun drawSafariCompass(canvas: Canvas, s: Float, isTinted: Boolean, tintColor: Int) {
        val cx = s * 0.50f
        val cy = s * 0.50f
        val r = s * 0.37f
        val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, cy - r, 0f, cy + r,
                if (isTinted) lightenColor(tintColor, 0.2f) else 0xFF43C6FF.toInt(),
                if (isTinted) tintColor else 0xFF007AFF.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, r, dialPaint)
        canvas.save()
        canvas.rotate(45f, cx, cy)
        val north = Path().apply {
            moveTo(cx, cy - r * 0.72f)
            lineTo(cx + r * 0.20f, cy)
            lineTo(cx - r * 0.20f, cy)
            close()
        }
        val south = Path().apply {
            moveTo(cx, cy + r * 0.72f)
            lineTo(cx + r * 0.20f, cy)
            lineTo(cx - r * 0.20f, cy)
            close()
        }
        canvas.drawPath(north, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isTinted) Color.WHITE else 0xFFFF3B30.toInt()
        })
        canvas.drawPath(south, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.restore()
    }

    private fun drawContactsIcon(canvas: Canvas, s: Float, style: IconThemeStyle, tintColor: Int) {
        drawLightOrDarkPlate(canvas, s, style, tintColor)
        val avatarColor = when (style) {
            IconThemeStyle.LIGHT -> 0xFF8E8E93.toInt()
            IconThemeStyle.DARK -> 0xFFAEAEB2.toInt()
            IconThemeStyle.TINTED -> lightenColor(tintColor, 0.25f)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = avatarColor }
        canvas.drawCircle(s * 0.46f, s * 0.37f, s * 0.14f, p)
        canvas.drawArc(RectF(s * 0.20f, s * 0.54f, s * 0.72f, s * 0.92f), 180f, 180f, true, p)
        if (style != IconThemeStyle.TINTED) {
            val tabs = intArrayOf(0xFF007AFF.toInt(), 0xFF34C759.toInt(), 0xFFFF9500.toInt(), 0xFFFF3B30.toInt())
            tabs.forEachIndexed { idx, c ->
                val top = idx * (s / 4f)
                canvas.drawRect(s * 0.84f, top, s, top + s / 4f, Paint().apply { color = c })
            }
        }
    }

    private fun drawAppStoreA(canvas: Canvas, s: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = s * 0.088f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(s * 0.50f, s * 0.22f, s * 0.25f, s * 0.74f, p)
        canvas.drawLine(s * 0.50f, s * 0.22f, s * 0.75f, s * 0.74f, p)
        canvas.drawLine(s * 0.22f, s * 0.62f, s * 0.78f, s * 0.62f, p)
    }

    private fun drawMusicNote(canvas: Canvas, s: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(s * 0.34f, s * 0.69f, s * 0.105f, p)
        canvas.drawCircle(s * 0.64f, s * 0.63f, s * 0.105f, p)
        val stem = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = s * 0.075f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(s * 0.41f, s * 0.68f, s * 0.41f, s * 0.30f, stem)
        canvas.drawLine(s * 0.71f, s * 0.62f, s * 0.71f, s * 0.24f, stem)
        val beam = Path().apply {
            moveTo(s * 0.38f, s * 0.30f)
            lineTo(s * 0.74f, s * 0.22f)
            lineTo(s * 0.74f, s * 0.33f)
            lineTo(s * 0.38f, s * 0.41f)
            close()
        }
        canvas.drawPath(beam, p)
    }

    private fun drawSpotifyWaves(canvas: Canvas, s: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        p.strokeWidth = s * 0.075f
        canvas.drawArc(RectF(s * 0.14f, s * 0.26f, s * 0.86f, s * 0.80f), 215f, 110f, false, p)
        p.strokeWidth = s * 0.065f
        canvas.drawArc(RectF(s * 0.20f, s * 0.40f, s * 0.80f, s * 0.84f), 218f, 104f, false, p)
        p.strokeWidth = s * 0.055f
        canvas.drawArc(RectF(s * 0.26f, s * 0.53f, s * 0.74f, s * 0.88f), 222f, 96f, false, p)
    }

    private fun drawWaveform(canvas: Canvas, s: Float, color: Int) {
        val heights = floatArrayOf(0.14f, 0.28f, 0.48f, 0.62f, 0.36f, 0.54f, 0.26f, 0.14f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = s * 0.048f
            strokeCap = Paint.Cap.ROUND
        }
        val step = (s * 0.72f) / (heights.size - 1)
        val startX = s * 0.14f
        heights.forEachIndexed { idx, h ->
            val x = startX + idx * step
            val half = s * h * 0.5f
            canvas.drawLine(x, s * 0.50f - half, x, s * 0.50f + half, p)
        }
    }

    private fun drawCompassGlyph(canvas: Canvas, s: Float, isTinted: Boolean, tintColor: Int) {
        val cx = s * 0.50f
        val cy = s * 0.50f
        canvas.drawCircle(cx, cy, s * 0.34f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = s * 0.03f
        })
        val tri = Path().apply {
            moveTo(cx, cy - s * 0.30f)
            lineTo(cx + s * 0.07f, cy - s * 0.16f)
            lineTo(cx - s * 0.07f, cy - s * 0.16f)
            close()
        }
        canvas.drawPath(tri, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isTinted) lightenColor(tintColor, 0.25f) else 0xFFFF3B30.toInt()
        })
        val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = s * 0.025f }
        canvas.drawLine(cx - s * 0.12f, cy, cx + s * 0.12f, cy, cross)
        canvas.drawLine(cx, cy - s * 0.12f, cx, cy + s * 0.12f, cross)
    }

    private fun drawFlashlightGlyph(canvas: Canvas, s: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val head = Path().apply {
            moveTo(s * 0.32f, s * 0.22f)
            lineTo(s * 0.68f, s * 0.22f)
            lineTo(s * 0.60f, s * 0.42f)
            lineTo(s * 0.40f, s * 0.42f)
            close()
        }
        canvas.drawPath(head, p)
        canvas.drawRoundRect(RectF(s * 0.41f, s * 0.40f, s * 0.59f, s * 0.78f), s * 0.05f, s * 0.05f, p)
    }

    private fun drawWalletGlyph(canvas: Canvas, s: Float, isTinted: Boolean, tintColor: Int) {
        val colors = if (isTinted) {
            intArrayOf(lightenColor(tintColor, 0.35f), lightenColor(tintColor, 0.20f), tintColor)
        } else {
            intArrayOf(0xFF0A84FF.toInt(), 0xFFFF9F0A.toInt(), 0xFF30D158.toInt())
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        colors.forEachIndexed { idx, c ->
            p.color = c
            val top = s * (0.22f + idx * 0.09f)
            canvas.drawRoundRect(RectF(s * 0.16f, top, s * 0.84f, top + s * 0.24f), s * 0.07f, s * 0.07f, p)
        }
        p.color = if (isTinted) 0xFF26262C.toInt() else 0xFFE5E5EA.toInt()
        canvas.drawRoundRect(RectF(s * 0.14f, s * 0.46f, s * 0.86f, s * 0.78f), s * 0.09f, s * 0.09f, p)
    }

    private fun drawInstagramCamera(canvas: Canvas, s: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = s * 0.068f
        }
        canvas.drawRoundRect(RectF(s * 0.21f, s * 0.21f, s * 0.79f, s * 0.79f), s * 0.16f, s * 0.16f, p)
        canvas.drawCircle(s * 0.50f, s * 0.50f, s * 0.145f, p)
        canvas.drawCircle(s * 0.67f, s * 0.33f, s * 0.036f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private fun drawPaperPlane(canvas: Canvas, s: Float, color: Int) {
        val p = Path().apply {
            moveTo(s * 0.77f, s * 0.25f)
            lineTo(s * 0.19f, s * 0.49f)
            lineTo(s * 0.42f, s * 0.57f)
            lineTo(s * 0.67f, s * 0.35f)
            lineTo(s * 0.48f, s * 0.61f)
            lineTo(s * 0.66f, s * 0.75f)
            close()
        }
        canvas.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private fun drawYouTubeMark(canvas: Canvas, s: Float, pillColor: Int, playColor: Int) {
        canvas.drawRoundRect(
            RectF(s * 0.15f, s * 0.28f, s * 0.85f, s * 0.72f),
            s * 0.13f, s * 0.13f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillColor }
        )
        val play = Path().apply {
            moveTo(s * 0.43f, s * 0.39f)
            lineTo(s * 0.63f, s * 0.50f)
            lineTo(s * 0.43f, s * 0.61f)
            close()
        }
        canvas.drawPath(play, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = playColor })
    }

    private fun drawTikTokMark(canvas: Canvas, s: Float, isTinted: Boolean, tintColor: Int) {
        if (!isTinted) {
            canvas.save()
            canvas.translate(-s * 0.025f, -s * 0.02f)
            drawSingleEighthNote(canvas, s, 0xFF25F4EE.toInt())
            canvas.restore()
            canvas.save()
            canvas.translate(s * 0.025f, s * 0.02f)
            drawSingleEighthNote(canvas, s, 0xFFFE2C55.toInt())
            canvas.restore()
        }
        drawSingleEighthNote(canvas, s, if (isTinted) lightenColor(tintColor, 0.25f) else Color.WHITE)
    }

    private fun drawSingleEighthNote(canvas: Canvas, s: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = s * 0.095f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(s * 0.54f, s * 0.25f, s * 0.54f, s * 0.62f, p)
        canvas.drawArc(RectF(s * 0.28f, s * 0.48f, s * 0.54f, s * 0.74f), 0f, 250f, false, p)
        canvas.drawArc(RectF(s * 0.54f, s * 0.16f, s * 0.80f, s * 0.42f), 90f, 90f, false, p)
    }

    private fun drawMessengerMark(canvas: Canvas, s: Float, color: Int) {
        drawSpeechBubble(canvas, s, color, withTailLeft = true)
        val bolt = Path().apply {
            moveTo(s * 0.34f, s * 0.53f)
            lineTo(s * 0.47f, s * 0.39f)
            lineTo(s * 0.53f, s * 0.48f)
            lineTo(s * 0.66f, s * 0.41f)
            lineTo(s * 0.53f, s * 0.55f)
            lineTo(s * 0.47f, s * 0.46f)
            close()
        }
        canvas.drawPath(bolt, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF2B6BFF.toInt() })
    }

    private fun drawDiscordMark(canvas: Canvas, s: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(RectF(s * 0.20f, s * 0.28f, s * 0.80f, s * 0.70f), s * 0.18f, s * 0.18f, p)
        val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF505CE8.toInt() }
        canvas.drawCircle(s * 0.39f, s * 0.49f, s * 0.065f, eye)
        canvas.drawCircle(s * 0.61f, s * 0.49f, s * 0.065f, eye)
    }

    private fun drawShoppingBag(canvas: Canvas, s: Float, color: Int) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(RectF(s * 0.22f, s * 0.35f, s * 0.78f, s * 0.79f), s * 0.09f, s * 0.09f, fill)
        val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = s * 0.068f
        }
        canvas.drawArc(RectF(s * 0.35f, s * 0.19f, s * 0.65f, s * 0.49f), 180f, 180f, false, handle)
    }

    private fun drawRideMark(canvas: Canvas, s: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = s * 0.10f
        }
        canvas.drawCircle(s * 0.50f, s * 0.50f, s * 0.24f, p)
        canvas.drawCircle(s * 0.50f, s * 0.50f, s * 0.085f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private fun drawVideoCam(canvas: Canvas, s: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(RectF(s * 0.17f, s * 0.33f, s * 0.63f, s * 0.67f), s * 0.09f, s * 0.09f, p)
        val lens = Path().apply {
            moveTo(s * 0.62f, s * 0.44f)
            lineTo(s * 0.83f, s * 0.33f)
            lineTo(s * 0.83f, s * 0.67f)
            lineTo(s * 0.62f, s * 0.56f)
            close()
        }
        canvas.drawPath(lens, p)
    }

    private fun drawGoogleG(canvas: Canvas, s: Float, isTinted: Boolean, tintColor: Int) {
        val rect = RectF(s * 0.22f, s * 0.22f, s * 0.78f, s * 0.78f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = s * 0.115f
            strokeCap = Paint.Cap.BUTT
        }
        val t = lightenColor(tintColor, 0.25f)
        p.color = if (isTinted) t else 0xFFEA4335.toInt()
        canvas.drawArc(rect, 215f, 100f, false, p)
        p.color = if (isTinted) t else 0xFFFBBC05.toInt()
        canvas.drawArc(rect, 135f, 80f, false, p)
        p.color = if (isTinted) t else 0xFF34A853.toInt()
        canvas.drawArc(rect, 45f, 90f, false, p)
        p.color = if (isTinted) t else 0xFF4285F4.toInt()
        canvas.drawArc(rect, -10f, 60f, false, p)
        canvas.drawLine(s * 0.50f, s * 0.50f, s * 0.80f, s * 0.50f, p)
    }

    private fun drawCloudMark(canvas: Canvas, s: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(s * 0.36f, s * 0.56f, s * 0.14f, p)
        canvas.drawCircle(s * 0.52f, s * 0.45f, s * 0.18f, p)
        canvas.drawCircle(s * 0.68f, s * 0.57f, s * 0.13f, p)
        canvas.drawRoundRect(RectF(s * 0.22f, s * 0.54f, s * 0.81f, s * 0.70f), s * 0.08f, s * 0.08f, p)
    }

    private fun drawRedditMark(canvas: Canvas, s: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawOval(RectF(s * 0.20f, s * 0.36f, s * 0.80f, s * 0.76f), p)
        canvas.drawCircle(s * 0.23f, s * 0.45f, s * 0.07f, p)
        canvas.drawCircle(s * 0.77f, s * 0.45f, s * 0.07f, p)
        canvas.drawCircle(s * 0.63f, s * 0.23f, s * 0.055f, p)
        val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFE83A00.toInt() }
        canvas.drawCircle(s * 0.40f, s * 0.53f, s * 0.05f, eye)
        canvas.drawCircle(s * 0.60f, s * 0.53f, s * 0.05f, eye)
    }

    private fun drawGhostMark(canvas: Canvas, s: Float, color: Int, strokeDark: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(s * 0.50f, s * 0.42f, s * 0.18f, p)
        canvas.drawRoundRect(RectF(s * 0.30f, s * 0.40f, s * 0.70f, s * 0.74f), s * 0.12f, s * 0.12f, p)
        if (strokeDark) {
            val st = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = 0xFF1C1C1E.toInt()
                style = Paint.Style.STROKE
                strokeWidth = s * 0.035f
            }
            canvas.drawRoundRect(RectF(s * 0.30f, s * 0.25f, s * 0.70f, s * 0.74f), s * 0.18f, s * 0.18f, st)
        }
    }

    private fun drawCenteredGlyphText(
        canvas: Canvas,
        s: Float,
        text: String,
        color: Int,
        textSizePx: Float,
        typeface: Typeface,
        yOffset: Float = 0f
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSizePx
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
        }
        val baseline = s * 0.50f - (paint.descent() + paint.ascent()) / 2f + yOffset
        canvas.drawText(text, s * 0.50f, baseline, paint)
    }

    private fun lightenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun blend(base: Int, overlay: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(base) * inv + Color.red(overlay) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(base) * inv + Color.green(overlay) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(base) * inv + Color.blue(overlay) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
