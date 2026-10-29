package com.vortexa.feature.video.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumePolicyTest {

    private val oneHour = 60 * 60 * 1000L

    @Test
    fun `short positions are not offered as a resume point`() {
        assertFalse(ResumePolicy.shouldResume(0, oneHour))
        assertFalse(ResumePolicy.shouldResume(9_999, oneHour))
        assertTrue(ResumePolicy.shouldResume(10_000, oneHour))
    }

    @Test
    fun `a position inside the credits never resumes`() {
        assertFalse(ResumePolicy.shouldResume(oneHour - 10_000, oneHour))
        assertFalse(ResumePolicy.shouldResume(oneHour - 1, oneHour))
        assertTrue(ResumePolicy.shouldResume(oneHour - 10_001, oneHour))
    }

    @Test
    fun `unknown duration still resumes when the position is meaningful`() {
        assertTrue(ResumePolicy.shouldResume(30_000, 0))
        assertFalse(ResumePolicy.shouldResume(1_000, 0))
    }

    @Test
    fun `persisting skips both the start and the end`() {
        assertFalse(ResumePolicy.shouldPersist(4_999, oneHour))
        assertTrue(ResumePolicy.shouldPersist(5_000, oneHour))
        assertFalse(ResumePolicy.shouldPersist(oneHour, oneHour))
    }

    @Test
    fun `clamping never returns a negative or past-the-end position`() {
        assertEquals(0L, ResumePolicy.clamped(-5, oneHour))
        assertEquals(oneHour, ResumePolicy.clamped(oneHour * 2, oneHour))
        assertEquals(123L, ResumePolicy.clamped(123, 0))
    }

    @Test
    fun `speeds cycle through the presets and wrap`() {
        assertEquals(0.75f, SpeedPresets.next(0.5f), 0.001f)
        assertEquals(1.25f, SpeedPresets.next(1f), 0.001f)
        assertEquals(0.5f, SpeedPresets.next(2f), 0.001f)
        assertEquals(0.5f, SpeedPresets.next(9f), 0.001f)
    }

    @Test
    fun `speed labels drop trailing zeros`() {
        assertEquals("1x", SpeedPresets.label(1f))
        assertEquals("0.75x", SpeedPresets.label(0.75f))
        assertEquals("1.5x", SpeedPresets.label(1.5f))
        assertEquals("2x", SpeedPresets.label(2f))
    }

    @Test
    fun `clock formats like a media player`() {
        assertEquals("0:00", PlaybackClock.format(0))
        assertEquals("0:07", PlaybackClock.format(7_400))
        assertEquals("12:34", PlaybackClock.format(754_000))
        assertEquals("1:02:03", PlaybackClock.format(3_723_000))
        assertEquals("0:00", PlaybackClock.format(-10))
    }
}
