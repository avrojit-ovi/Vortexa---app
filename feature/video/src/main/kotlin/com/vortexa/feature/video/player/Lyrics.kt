package com.vortexa.feature.video.player

import java.util.Locale

/** One timed line of an `.lrc` file. */
data class LyricLine(val atMillis: Long, val text: String)

/**
 * A small, honest LRC parser: timed lines, several timestamps on one line,
 * `[offset:…]` in milliseconds, and metadata tags ignored rather than rendered.
 *
 * Synced lyrics are the only kinds that make sense here; a plain text file is
 * reported as "no lyrics" instead of being shown out of time.
 */
object LrcParser {

    private val TIME = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val OFFSET = Regex("\\[offset:\\s*([+-]?\\d+)]", RegexOption.IGNORE_CASE)

    fun parse(content: String): List<LyricLine> {
        val offset = OFFSET.find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<LyricLine>()

        content.lineSequence().forEach { raw ->
            val stamps = TIME.findAll(raw).toList()
            if (stamps.isEmpty()) return@forEach
            val text = TIME.replace(raw, "").trim()
            if (text.isEmpty()) return@forEach
            stamps.forEach { stamp ->
                lines += LyricLine(atMillis = stamp.toMillis() + offset, text = text)
            }
        }

        return lines.sortedBy { it.atMillis }
    }

    private fun MatchResult.toMillis(): Long {
        val minutes = groupValues[1].toLongOrNull() ?: 0L
        val seconds = groupValues[2].toLongOrNull() ?: 0L
        val fractionRaw = groupValues[3]
        // "5" means 500 ms, "05" 50 ms, "500" 500 ms — the same rule players use.
        val fraction = when (fractionRaw.length) {
            0 -> 0L
            1 -> fractionRaw.toLong() * 100
            2 -> fractionRaw.toLong() * 10
            else -> fractionRaw.take(3).toLong()
        }
        return minutes * 60_000 + seconds * 1_000 + fraction
    }
}

/** Finds a `.lrc` file named after the track, next to it. */
object LyricsFinder {

    fun find(audioPath: String, candidates: List<String>): String? {
        val audioName = audioPath.substringAfterLast('/').substringAfterLast('\\')
        val base = audioName.substringBeforeLast('.', audioName).lowercase(Locale.US)
        if (base.isEmpty()) return null

        return candidates.firstOrNull { candidate ->
            val name = candidate.substringAfterLast('/').substringAfterLast('\\')
            if (!name.endsWith(".lrc", ignoreCase = true)) return@firstOrNull false
            name.substringBeforeLast('.').lowercase(Locale.US) == base
        }
    }
}

/** The line that belongs on screen at [positionMs]; the last one wins on ties. */
object LyricsPosition {

    fun lineAt(lines: List<LyricLine>, positionMs: Long): LyricLine? {
        var current: LyricLine? = null
        for (line in lines) {
            if (line.atMillis <= positionMs) current = line else break
        }
        return current
    }
}
