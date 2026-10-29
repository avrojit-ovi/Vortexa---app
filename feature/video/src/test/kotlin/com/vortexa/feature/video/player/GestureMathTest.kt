package com.vortexa.feature.video.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureMathTest {

    private val tenMinutes = 10 * 60 * 1000L

    @Test
    fun `a drag locks onto the axis it moved furthest along`() {
        assertEquals(DragAxis.HORIZONTAL, GestureMath.axis(40f, 5f))
        assertEquals(DragAxis.VERTICAL, GestureMath.axis(4f, -40f))
        assertEquals(DragAxis.NONE, GestureMath.axis(2f, 3f))
    }

    @Test
    fun `a full width swipe moves half of the video`() {
        val target = GestureMath.seekTarget(0, tenMinutes, 1f)
        assertEquals(tenMinutes / 2, target)
    }

    @Test
    fun `seeking never leaves the video`() {
        assertEquals(0L, GestureMath.seekTarget(10_000, tenMinutes, -1f))
        assertEquals(tenMinutes, GestureMath.seekTarget(tenMinutes - 1_000, tenMinutes, 1f))
    }

    @Test
    fun `a live stream does not seek`() {
        assertEquals(4_242L, GestureMath.seekTarget(4_242, 0, 0.8f))
    }

    @Test
    fun `vertical drags move brightness and volume in the same direction`() {
        // Upward drag (negative dy reported as positive fraction) brightens.
        assertEquals(0.7f, GestureMath.levelAfter(0.5f, 0.2f), 0.001f)
        assertEquals(1f, GestureMath.levelAfter(0.9f, 0.5f), 0.001f)
        assertEquals(0f, GestureMath.levelAfter(0.1f, -0.5f), 0.001f)
        // An unset (-1) brightness starts from half, not from black.
        assertEquals(0.6f, GestureMath.brightnessAfter(-1f, 0.1f), 0.001f)
    }

    @Test
    fun `volume steps stay inside the stream range`() {
        assertEquals(15, GestureMath.volumeIndexAfter(10, 15, 0.5f))
        assertEquals(0, GestureMath.volumeIndexAfter(2, 15, -0.9f))
        assertEquals(1, GestureMath.volumeIndexAfter(14, 15, -0.9f))
        assertEquals(5, GestureMath.volumeIndexAfter(5, 0, 0.5f))
    }

    @Test
    fun `seek labels are short and signed`() {
        assertEquals("+42s", GestureMath.seekDeltaLabel(42_000))
        assertEquals("-1:05", GestureMath.seekDeltaLabel(-65_000))
        assertEquals("0s", GestureMath.seekDeltaLabel(0))
    }

    @Test
    fun `the left half is brightness, the right half is volume`() {
        assertTrue(GestureMath.sideOf(10f, 1080))
        assertFalse(GestureMath.sideOf(1000f, 1080))
    }
}
