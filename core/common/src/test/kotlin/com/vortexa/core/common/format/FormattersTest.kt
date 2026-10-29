package com.vortexa.core.common.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun `bytes uses decimal units with one decimal below ten`() {
        assertEquals("0 B", Formatters.bytes(0))
        assertEquals("999 B", Formatters.bytes(999))
        assertEquals("1.0 KB", Formatters.bytes(1_000))
        assertEquals("1.4 MB", Formatters.bytes(1_440_000))
        assertEquals("15 MB", Formatters.bytes(15_000_000))
        assertEquals("2.0 GB", Formatters.bytes(2_000_000_000))
    }

    @Test
    fun `bytes handles negative and huge values`() {
        assertEquals("-1.0 KB", Formatters.bytes(-1_000))
        assertEquals("1.0 PB", Formatters.bytes(1_000_000_000_000_000))
    }

    @Test
    fun `speed appends per second`() {
        assertEquals("1.4 MB/s", Formatters.speed(1_440_000))
    }

    @Test
    fun `duration formats hours minutes seconds`() {
        assertEquals("--", Formatters.duration(0))
        assertEquals("45s", Formatters.duration(45))
        assertEquals("2m 5s", Formatters.duration(125))
        assertEquals("2h 1m", Formatters.duration(7_300))
    }

    @Test
    fun `percent clamps and survives NaN`() {
        assertEquals("0%", Formatters.percent(Float.NaN))
        assertEquals("0%", Formatters.percent(-1f))
        assertEquals("42%", Formatters.percent(0.42f))
        assertEquals("100%", Formatters.percent(2f))
    }

    @Test
    fun `middle ellipsis keeps both ends`() {
        val long = "/storage/emulated/0/Download/very/long/path/to/a/movie/file.mp4"
        val result = Formatters.middleEllipsis(long, maxChars = 24)
        assertEquals(24, result.length)
        assertTrue(result, result.contains("..."))
        assertTrue(result, result.startsWith("/storage"))
        assertTrue(result, result.endsWith("file.mp4"))
    }
}
