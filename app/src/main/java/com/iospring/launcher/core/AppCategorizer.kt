package com.iospring.launcher.core

import android.content.pm.ApplicationInfo
import com.iospring.launcher.model.AppCategory

/**
 * Classifies Android apps into authentic iOS App Library categories using
 * Android's ApplicationInfo.category plus curated package/label heuristics
 * (including popular Indonesian/SEA and OPPO ColorOS apps).
 */
object AppCategorizer {

    fun classify(packageName: String, label: String, androidCategory: Int): AppCategory {
        val pkg = packageName.lowercase()
        val lbl = label.lowercase()

        // 1. Explicit package heuristics for crisp accuracy
        // Check explicit system utilities first so "Phone Manager" / "Clone Phone" don't match "phone" in SOCIAL
        when {
            matchesAny(pkg, lbl, PRIORITY_UTILITY_KEYS) -> return AppCategory.UTILITIES
            matchesAny(pkg, lbl, SOCIAL_KEYS) -> return AppCategory.SOCIAL
            matchesAny(pkg, lbl, GAME_KEYS) -> return AppCategory.GAMES
            matchesAny(pkg, lbl, SHOPPING_KEYS) -> return AppCategory.SHOPPING
            matchesAny(pkg, lbl, ENTERTAINMENT_KEYS) -> return AppCategory.ENTERTAINMENT
            matchesAny(pkg, lbl, CREATIVITY_KEYS) -> return AppCategory.CREATIVITY
            matchesAny(pkg, lbl, PRODUCTIVITY_KEYS) -> return AppCategory.PRODUCTIVITY
            matchesAny(pkg, lbl, INFO_KEYS) -> return AppCategory.INFORMATION
            matchesAny(pkg, lbl, UTILITY_KEYS) -> return AppCategory.UTILITIES
        }

        // 2. Fallback to Android OS metadata category
        return when (androidCategory) {
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO -> AppCategory.ENTERTAINMENT
            ApplicationInfo.CATEGORY_IMAGE -> AppCategory.CREATIVITY
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
            ApplicationInfo.CATEGORY_NEWS,
            ApplicationInfo.CATEGORY_MAPS -> AppCategory.INFORMATION
            else -> AppCategory.UTILITIES
        }
    }

    private fun matchesAny(pkg: String, lbl: String, keys: List<String>): Boolean {
        return keys.any { key -> pkg.contains(key) || lbl.contains(key) }
    }

    private val PRIORITY_UTILITY_KEYS = listOf(
        "phonemanager", "phone manager", "clonephone", "clone phone",
        "filemanager", "file manager", "settings", "calculator", "flashlight"
    )

    private val SOCIAL_KEYS = listOf(
        "whatsapp", "instagram", "telegram", "facebook", "messenger", "twitter", "x.android",
        "discord", "snapchat", "threads", "line", "wechat", "signal", "viber", "linkedin",
        "dialer", "contacts", "mms", "messaging", "phone", "call"
    )

    private val GAME_KEYS = listOf(
        "game", "mobile.legends", "pubg", "freefire", "genshin", "roblox", "minecraft",
        "supercell", "clash", "brawl", "honkai", "valorant", "chess", "solitaire", "heytap.game"
    )

    private val SHOPPING_KEYS = listOf(
        "shopee", "tokopedia", "lazada", "blibli", "bukalapak", "amazon", "gojek", "grab",
        "dana", "ovo", "gopay", "traveloka", "tiket", "mcd", "starbucks", "food", "shop", "store"
    )

    private val ENTERTAINMENT_KEYS = listOf(
        "youtube", "spotify", "netflix", "disney", "tiktok", "musically", "viu", "vidio",
        "twitch", "soundcloud", "music", "video", "cinema", "podcast", "primevideo", "hbo"
    )

    private val CREATIVITY_KEYS = listOf(
        "camera", "gallery", "photos", "capcut", "lightroom", "snapseed", "canva", "pixellab",
        "inshot", "vsco", "picsart", "adobe", "sketch", "draw", "photo", "coloros.gallery"
    )

    private val PRODUCTIVITY_KEYS = listOf(
        "gmail", "mail", "calendar", "docs", "sheets", "slides", "drive", "notion", "keep",
        "notes", "office", "word", "excel", "outlook", "slack", "zoom", "teams", "meet",
        "bca", "mandiri", "bri", "bni", "bank", "jago", "livin", "seabank", "finance", "wallet"
    )

    private val INFO_KEYS = listOf(
        "chrome", "browser", "firefox", "brave", "opera", "edge", "maps", "waze", "weather",
        "news", "reddit", "medium", "kindle", "wikipedia", "translate", "earth"
    )

    private val UTILITY_KEYS = listOf(
        "settings", "clock", "alarm", "calculator", "files", "filemanager", "recorder",
        "compass", "flashlight", "backup", "clone", "coloros.phonemanager", "oplus", "oppo",
        "vending", "play", "vpn", "authenticator", "terminal", "adb", "system"
    )
}
