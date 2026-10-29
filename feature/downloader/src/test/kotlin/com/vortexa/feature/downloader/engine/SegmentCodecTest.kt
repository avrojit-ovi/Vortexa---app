package com.vortexa.feature.downloader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentCodecTest {

    @Test
    fun `round trips segment state`() {
        val states = listOf(
            SegmentState(0, 99_999, 12_345),
            SegmentState(100_000, 199_999, 0),
            SegmentState(200_000, 299_999, 99_999),
        )
        assertEquals(states, SegmentCodec.decode(SegmentCodec.encode(states)))
    }

    @Test
    fun `empty input decodes to empty list`() {
        assertTrue(SegmentCodec.decode(null).isEmpty())
        assertTrue(SegmentCodec.decode("").isEmpty())
    }

    @Test
    fun `corrupt entries are dropped instead of throwing`() {
        val decoded = SegmentCodec.decode("0-99:10;garbage;5-4:0;100-199:50;200-299:oops")
        assertEquals(2, decoded.size)
        assertEquals(SegmentState(0, 99, 10), decoded[0])
        assertEquals(SegmentState(100, 199, 50), decoded[1])
    }
}
