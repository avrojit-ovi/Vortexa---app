package com.vortexa.feature.downloader.engine

/** One contiguous byte range of the remote file. Both ends are inclusive. */
data class RangeSegment(
    val index: Int,
    val start: Long,
    val end: Long,
) {
    val length: Long get() = end - start + 1

    fun rangeHeader(): String = "bytes=$start-$end"

    /** What is left to fetch when [downloaded] bytes of this range are on disk. */
    fun remaining(downloaded: Long): RangeSegment? {
        if (downloaded >= length) return null
        return copy(start = start + downloaded)
    }
}

/**
 * How a file is cut into ranges. The JVM implementation mirrors the Rust core
 * (`rust/download-core/src/segment.rs`); both are covered by the same test
 * vectors so they cannot drift apart unnoticed.
 */
interface SegmentPlanner {
    fun plan(totalBytes: Long, partCount: Int, minSegmentBytes: Long): List<RangeSegment>
}

class KotlinSegmentPlanner : SegmentPlanner {

    override fun plan(totalBytes: Long, partCount: Int, minSegmentBytes: Long): List<RangeSegment> {
        if (totalBytes <= 0L) return emptyList()

        val minimum = minSegmentBytes.coerceAtLeast(1L)
        val affordable = (totalBytes / minimum).coerceAtLeast(1L).toInt()
        val parts = partCount.coerceIn(1, MAX_PARTS).coerceAtMost(affordable).coerceAtLeast(1)

        val base = totalBytes / parts
        val segments = ArrayList<RangeSegment>(parts)
        var start = 0L
        for (index in 0 until parts) {
            val isLast = index == parts - 1
            val end = if (isLast) totalBytes - 1 else start + base - 1
            segments += RangeSegment(index = index, start = start, end = end)
            start = end + 1
        }
        return segments
    }

    companion object {
        const val MAX_PARTS = 32
        const val DEFAULT_MIN_SEGMENT_BYTES = 512L * 1024L
    }
}
