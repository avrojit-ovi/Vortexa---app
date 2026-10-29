package com.vortexa.feature.downloader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Same vectors as the Rust core tests (`rust/download-core/src/segment.rs`), so
 * the two implementations cannot drift apart.
 */
class SegmentPlannerTest {

    private val planner = KotlinSegmentPlanner()

    private fun assertCovers(segments: List<RangeSegment>, total: Long) {
        assertEquals(0L, segments.first().start)
        assertEquals(total - 1, segments.last().end)
        segments.zipWithNext().forEach { (left, right) ->
            assertEquals("segments must be contiguous", left.end + 1, right.start)
        }
        assertEquals(total, segments.sumOf { it.length })
    }

    @Test
    fun `empty file has no segments`() {
        assertTrue(planner.plan(0, 8, 512).isEmpty())
    }

    @Test
    fun `small file gets a single segment`() {
        val segments = planner.plan(100, 8, 512)
        assertEquals(1, segments.size)
        assertEquals(RangeSegment(0, 0, 99), segments[0])
    }

    @Test
    fun `even split covers every byte`() {
        val segments = planner.plan(4_000, 4, 100)
        assertEquals(4, segments.size)
        assertEquals(1_000, segments[0].length)
        assertCovers(segments, 4_000)
    }

    @Test
    fun `remainder goes to the last segment`() {
        val segments = planner.plan(1_000, 3, 100)
        assertCovers(segments, 1_000)
        assertEquals(1_000 - 333 * 2, segments[2].length)
    }

    @Test
    fun `part count is capped by the minimum segment size`() {
        assertEquals(1, planner.plan(700, 8, 512).size)
    }

    @Test
    fun `part count is clamped to 32`() {
        val segments = planner.plan(64 * 512, 999, 512)
        assertEquals(32, segments.size)
        assertCovers(segments, 64 * 512)
    }

    @Test
    fun `range header is inclusive`() {
        assertEquals("bytes=0-99", RangeSegment(0, 0, 99).rangeHeader())
        assertEquals("bytes=500-999", RangeSegment(1, 500, 999).rangeHeader())
    }

    @Test
    fun `remaining tracks progress`() {
        val segment = RangeSegment(0, 100, 199)
        assertEquals(RangeSegment(0, 100, 199), segment.remaining(0))
        assertEquals(RangeSegment(0, 140, 199), segment.remaining(40))
        assertEquals(null, segment.remaining(100))
        assertEquals(null, segment.remaining(500))
    }
}
