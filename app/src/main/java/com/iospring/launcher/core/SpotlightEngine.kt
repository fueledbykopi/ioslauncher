package com.iospring.launcher.core

import com.iospring.launcher.model.AppEntry
import com.iospring.launcher.model.SpotlightResult
import kotlin.math.abs
import kotlin.math.floor

/**
 * Instant, offline Spotlight search & inline calculator engine.
 */
object SpotlightEngine {

    fun search(
        query: String,
        allApps: List<AppEntry>,
        hiddenPackages: Set<String>,
        usageScores: Map<String, Long>
    ): SpotlightResult {
        val trimmed = query.trim()

        if (trimmed.isEmpty()) {
            val suggested = allApps
                .asSequence()
                .filterNot { it.packageName in hiddenPackages }
                .sortedWith(
                    compareByDescending<AppEntry> { usageScores[it.componentKey] ?: 0L }
                        .thenBy { it.lowerLabel }
                )
                .take(8)
                .toList()
            return SpotlightResult(
                suggestedApps = suggested,
                matchedApps = emptyList()
            )
        }

        val mathResult = evaluateMathExpression(trimmed)
        val lowerQuery = trimmed.lowercase()
        val isT9Query = lowerQuery.length >= 2 && lowerQuery.all { it in '2'..'9' }

        val scored = ArrayList<Pair<AppEntry, Int>>(16)
        for (i in allApps.indices) {
            val app = allApps[i]
            if (app.packageName in hiddenPackages) continue
            val score = matchScore(app.lowerLabel, app.lowerPackage, lowerQuery, isT9Query)
            if (score > 0) {
                val boost = (usageScores[app.componentKey] ?: 0L).coerceAtMost(50L).toInt()
                scored.add(Pair(app, score + boost))
            }
        }
        scored.sortWith(
            compareByDescending<Pair<AppEntry, Int>> { it.second }
                .thenBy { it.first.lowerLabel }
        )

        val matched = ArrayList<AppEntry>(minOf(scored.size, 16))
        for (i in 0 until minOf(scored.size, 16)) {
            matched.add(scored[i].first)
        }

        return SpotlightResult(
            mathExpression = if (mathResult != null) trimmed else null,
            mathResult = mathResult,
            suggestedApps = emptyList(),
            matchedApps = matched
        )
    }

    internal fun matchScore(
        label: String,
        pkg: String,
        query: String,
        isT9Query: Boolean = query.length >= 2 && query.all { it in '2'..'9' }
    ): Int {
        if (label == query) return 1000
        if (label.startsWith(query)) return 750 - (label.length - query.length).coerceAtLeast(0)

        // Zero-allocation word-boundary prefix & initials scan
        var atWordStart = true
        var initialIdx = 0
        var initialsMatch = query.length >= 2
        for (i in label.indices) {
            val c = label[i]
            val isSep = c == ' ' || c == '-' || c == '_' || c == '.'
            if (isSep) {
                atWordStart = true
            } else if (atWordStart) {
                atWordStart = false
                if (i > 0 && label.startsWith(query, startIndex = i)) {
                    return 550
                }
                if (initialsMatch && initialIdx < query.length) {
                    if (c == query[initialIdx]) {
                        initialIdx++
                    } else {
                        initialsMatch = false
                    }
                }
            }
        }
        if (initialsMatch && initialIdx == query.length) return 480

        if (label.contains(query)) return 360
        if (query.length >= 3 && pkg.contains(query)) return 180

        // T9 numeric keypad prefix match (e.g. "942" -> "WhatsApp", "226" -> "Camera")
        if (isT9Query) {
            val t9Label = toT9Digits(label)
            if (t9Label.startsWith(query)) return 420
            if (t9Label.contains(query)) return 260
        }

        // Lightweight subsequence fuzzy match
        if (query.length >= 2) {
            var qi = 0
            for (i in label.indices) {
                if (label[i] == query[qi]) {
                    qi++
                    if (qi == query.length) return 120
                }
            }
        }
        return 0
    }

    fun toT9Digits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase()) {
            val d = when (ch) {
                'a', 'b', 'c' -> '2'
                'd', 'e', 'f' -> '3'
                'g', 'h', 'i' -> '4'
                'j', 'k', 'l' -> '5'
                'm', 'n', 'o' -> '6'
                'p', 'q', 'r', 's' -> '7'
                't', 'u', 'v' -> '8'
                'w', 'x', 'y', 'z' -> '9'
                else -> continue
            }
            sb.append(d)
        }
        return sb.toString()
    }

    /**
     * Safe recursive-descent arithmetic evaluator for iOS Spotlight quick math.
     * Supports +, -, *, /, x, ÷, (, ), decimals, and unary minus/plus.
     */
    fun evaluateMathExpression(raw: String): String? {
        val cleaned = raw
            .replace(" ", "")
            .replace("×", "*")
            .replace("x", "*", ignoreCase = true)
            .replace("÷", "/")
            .replace(",", ".")

        // Must contain at least one operator and only math tokens
        if (!cleaned.any { it == '+' || it == '-' || it == '*' || it == '/' }) return null
        if (!cleaned.all { it.isDigit() || it in "+-*/()." }) return null
        if (cleaned.all { it == '-' || it.isDigit() || it == '.' }) return null

        val parser = MathParser(cleaned)
        val value = parser.parse() ?: return null
        if (!value.isFinite()) return null

        return if (abs(value - floor(value)) < 1e-9 && abs(value) < 1e14) {
            value.toLong().toString()
        } else {
            val formatted = "%.6f".format(java.util.Locale.US, value)
            formatted.trimEnd('0').trimEnd('.')
        }
    }

    private class MathParser(private val input: String) {
        private var pos = 0

        fun parse(): Double? {
            return try {
                val res = parseExpression()
                if (pos == input.length) res else null
            } catch (_: Exception) {
                null
            }
        }

        private fun parseExpression(): Double {
            var left = parseTerm()
            while (pos < input.length) {
                when (input[pos]) {
                    '+' -> { pos++; left += parseTerm() }
                    '-' -> { pos++; left -= parseTerm() }
                    else -> break
                }
            }
            return left
        }

        private fun parseTerm(): Double {
            var left = parseFactor()
            while (pos < input.length) {
                when (input[pos]) {
                    '*' -> { pos++; left *= parseFactor() }
                    '/' -> {
                        pos++
                        val divisor = parseFactor()
                        if (abs(divisor) < 1e-12) throw ArithmeticException("Division by zero")
                        left /= divisor
                    }
                    else -> break
                }
            }
            return left
        }

        private fun parseFactor(): Double {
            if (pos >= input.length) throw IllegalArgumentException("Unexpected end")
            return when (input[pos]) {
                '+' -> { pos++; parseFactor() }
                '-' -> { pos++; -parseFactor() }
                '(' -> {
                    pos++
                    val value = parseExpression()
                    if (pos >= input.length || input[pos] != ')') {
                        throw IllegalArgumentException("Missing closing paren")
                    }
                    pos++
                    value
                }
                else -> parseNumber()
            }
        }

        private fun parseNumber(): Double {
            val start = pos
            var dotSeen = false
            while (pos < input.length) {
                val c = input[pos]
                if (c.isDigit()) {
                    pos++
                } else if (c == '.' && !dotSeen) {
                    dotSeen = true
                    pos++
                } else {
                    break
                }
            }
            if (start == pos) throw IllegalArgumentException("Expected number")
            return input.substring(start, pos).toDouble()
        }
    }
}
