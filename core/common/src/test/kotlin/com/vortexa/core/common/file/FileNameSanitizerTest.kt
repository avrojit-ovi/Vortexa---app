package com.vortexa.core.common.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameSanitizerTest {

    @Test
    fun `strips path traversal and separators`() {
        assertEquals("passwd", FileNameSanitizer.sanitize("../../etc/passwd"))
        assertEquals("file.mp4", FileNameSanitizer.sanitize("..\\..\\windows\\file.mp4"))
        assertEquals("evil.sh", FileNameSanitizer.sanitize("/tmp/evil.sh"))
    }

    @Test
    fun `removes illegal characters and control bytes`() {
        assertEquals("MyMove2024.mkv", FileNameSanitizer.sanitize("My:Move|2024\u0000.mkv"))
        assertEquals("ab.mp3", FileNameSanitizer.sanitize("a<b>.mp3"))
    }

    @Test
    fun `falls back when nothing is usable`() {
        assertEquals("download", FileNameSanitizer.sanitize(null))
        assertEquals("download", FileNameSanitizer.sanitize(""))
        assertEquals("download", FileNameSanitizer.sanitize("  ..  "))
        assertEquals("video", FileNameSanitizer.sanitize("..", fallback = "video"))
    }

    @Test
    fun `escapes windows reserved device names`() {
        assertEquals("_CON", FileNameSanitizer.sanitize("CON"))
        assertEquals("_lpt1.txt", FileNameSanitizer.sanitize("lpt1.txt"))
    }

    @Test
    fun `truncates but keeps the extension`() {
        val longName = "a".repeat(500) + ".mkv"
        val result = FileNameSanitizer.sanitize(longName)
        assertEquals(200, result.length)
        assertTrue(result, result.endsWith(".mkv"))
        assertFalse(result, result.contains('/'))
    }
}
