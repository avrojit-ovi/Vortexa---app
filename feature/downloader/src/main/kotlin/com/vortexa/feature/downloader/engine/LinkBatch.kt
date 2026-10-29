package com.vortexa.feature.downloader.engine

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Reads whatever the user pasted and returns the links worth queueing.
 *
 * One link per line is the documented shape, but a list copied from a chat or a
 * browser arrives separated by spaces, tabs or commas as well, so both are
 * accepted. Tokens that are not http(s) links are dropped instead of failing the
 * whole paste, duplicates collapse, and the result is capped so a careless paste
 * cannot flood the queue.
 */
object LinkBatch {

    /** A batch bigger than this is almost certainly a mistake, not an intention. */
    const val MAX_LINKS = 50

    private val SEPARATORS = charArrayOf(' ', '\t', '\n', '\r', ',', ';', '"', '\'', '<', '>')
    private val EDGE_CHARS = charArrayOf('(', ')', '[', ']', '{', '}', '.', '!', '?', '*')

    /**
     * @param limit maximum number of links returned; values below 1 return nothing.
     */
    fun parse(text: String, limit: Int = MAX_LINKS): List<String> {
        if (limit <= 0) return emptyList()
        val links = LinkedHashSet<String>()
        for (token in text.split(*SEPARATORS)) {
            if (links.size >= limit) break
            val candidate = token.trim(*EDGE_CHARS)
            if (candidate.isEmpty()) continue
            val url = candidate.toHttpUrlOrNull() ?: continue
            links += url.toString()
        }
        return links.toList()
    }
}
