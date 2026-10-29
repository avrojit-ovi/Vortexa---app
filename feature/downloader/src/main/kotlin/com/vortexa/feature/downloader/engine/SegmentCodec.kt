package com.vortexa.feature.downloader.engine

/**
 * Compact, dependency-free encoding of per-part download state.
 *
 * Format: `start-end:downloaded;start-end:downloaded;...`
 * It is stored in Room so a killed process can resume exactly where each range
 * stopped instead of guessing from the aggregate byte count.
 */
object SegmentCodec {

    fun encode(states: List<SegmentState>): String =
        states.joinToString(";") { "${it.start}-${it.end}:${it.downloaded}" }

    fun decode(raw: String?): List<SegmentState> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { part ->
            val range = part.substringBefore(':')
            val downloaded = part.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
            val start = range.substringBefore('-').toLongOrNull() ?: return@mapNotNull null
            val end = range.substringAfter('-', "").toLongOrNull() ?: return@mapNotNull null
            if (end < start || downloaded < 0) {
                null
            } else {
                SegmentState(start = start, end = end, downloaded = downloaded)
            }
        }
    }
}
