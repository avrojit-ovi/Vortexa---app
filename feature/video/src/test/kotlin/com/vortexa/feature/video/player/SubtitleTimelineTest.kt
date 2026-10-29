package com.vortexa.feature.video.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleTimelineTest {

    @Test
    fun `without a delay the newest cue is on screen immediately`() {
        val timeline = SubtitleTimeline()
        timeline.push(listOf("first"), atMillis = 1_000)
        timeline.push(listOf("second"), atMillis = 3_000)
        assertEquals(listOf("second"), timeline.visible(nowMillis = 3_000, delayMillis = 0))
    }

    @Test
    fun `a positive delay holds the cue back until its shifted time`() {
        val timeline = SubtitleTimeline()
        timeline.push(listOf("hello"), atMillis = 1_000)

        assertEquals(emptyList<String>(), timeline.visible(nowMillis = 1_000, delayMillis = 2_000))
        assertEquals(emptyList<String>(), timeline.visible(nowMillis = 2_999, delayMillis = 2_000))
        assertEquals(listOf("hello"), timeline.visible(nowMillis = 3_000, delayMillis = 2_000))
    }

    @Test
    fun `clearing the cue does not flicker the delayed one back in`() {
        val timeline = SubtitleTimeline()
        timeline.push(listOf("hello"), atMillis = 1_000)
        timeline.push(emptyList(), atMillis = 2_000)

        assertEquals(listOf("hello"), timeline.visible(nowMillis = 2_000, delayMillis = 500))
        assertEquals(emptyList<String>(), timeline.visible(nowMillis = 2_500, delayMillis = 500))
    }

    @Test
    fun `the timeline keeps its memory bounded`() {
        val timeline = SubtitleTimeline(maxEmissions = 4)
        repeat(20) { index ->
            timeline.push(listOf("cue$index"), atMillis = index.toLong())
        }
        // The oldest cues were dropped; the recent ones still resolve.
        assertEquals(listOf("cue19"), timeline.visible(nowMillis = 19, delayMillis = 0))
    }

    @Test
    fun `clear wipes every pending cue`() {
        val timeline = SubtitleTimeline()
        timeline.push(listOf("hello"), atMillis = 1_000)
        timeline.clear()
        assertEquals(emptyList<String>(), timeline.visible(nowMillis = 9_000, delayMillis = 0))
    }

    @Test
    fun `delay is clamped to the supported range and labelled to one decimal`() {
        assertEquals(SubtitleDelay.MIN_SECONDS, SubtitleDelay.clamp(-4f))
        assertEquals(SubtitleDelay.MAX_SECONDS, SubtitleDelay.clamp(99f))
        assertEquals(1.5f, SubtitleDelay.step(1f, 0.5f))
        assertEquals(0f, SubtitleDelay.step(0f, -0.5f))
        assertEquals("0.0s", SubtitleDelay.label(0f))
        assertEquals("+0.5s", SubtitleDelay.label(0.5f))
        assertEquals("+10.0s", SubtitleDelay.label(12f))
    }

    @Test
    fun `subtitle sizes map back from their step`() {
        assertEquals(SubtitleSize.SMALL, SubtitleSize.fromStep(0))
        assertEquals(SubtitleSize.LARGE, SubtitleSize.fromStep(2))
        assertEquals(SubtitleSize.MEDIUM, SubtitleSize.fromStep(9))
    }
}
