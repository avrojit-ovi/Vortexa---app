package com.vortexa.core.common.format

import java.util.Locale
import kotlin.math.abs

/**
 * Human readable formatters shared by every feature.
 *
 * They are deliberately pure functions: no Android dependencies, so they are
 * covered by plain JVM unit tests.
 */
object Formatters {

    private val BYTE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    /** 0 B, 999 B, 1.0 KB, 1.4 MB, 2.0 GB ... */
    fun bytes(value: Long): String {
        if (value < 0) return "-" + bytes(-value)
        if (value < 1000) return "$value B"

        var size = value.toDouble()
        var unit = 0
        while (size >= 1000.0 && unit < BYTE_UNITS.lastIndex) {
            size /= 1000.0
            unit++
        }
        val decimals = if (size < 10.0) 1 else 0
        return String.format(Locale.US, "%.${decimals}f %s", size, BYTE_UNITS[unit])
    }

    /** Bytes per second, e.g. "1.4 MB/s". */
    fun speed(bytesPerSecond: Long): String = bytes(bytesPerSecond) + "/s"

    /** 45 -> "45s", 125 -> "2m 5s", 7300 -> "2h 1m", 0 -> "--". */
    fun duration(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "--"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /** 0.0 .. 1.0 -> "42%" (clamped, never throws on bad input). */
    fun percent(fraction: Float): String {
        if (fraction.isNaN()) return "0%"
        val clamped = fraction.coerceIn(0f, 1f)
        return "${(clamped * 100).toInt()}%"
    }

    /** Trims a path-ish string for single line display in the middle: /a/b/.../z.mp4 */
    fun middleEllipsis(value: String, maxChars: Int = 48): String {
        if (maxChars < 8 || value.length <= maxChars) return value
        val head = (maxChars - 3) / 2
        val tail = maxChars - 3 - head
        val safeHead = abs(head).coerceAtMost(value.length)
        val safeTail = abs(tail).coerceAtMost(value.length - safeHead)
        return value.substring(0, safeHead) + "..." + value.substring(value.length - safeTail)
    }
}
