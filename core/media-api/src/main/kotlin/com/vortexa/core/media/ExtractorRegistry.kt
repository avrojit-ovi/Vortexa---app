package com.vortexa.core.media

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** One source that failed, and a message that is safe to show. */
data class ExtractorFailure(
    val extractorId: String,
    val displayName: String,
    val reason: ExtractorError,
    val message: String,
)

/** Partial results are the normal case: one source may be down. */
data class SearchOutcome(
    val items: List<MediaTrack>,
    val failures: List<ExtractorFailure> = emptyList(),
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * Holds the active sources and fans a search out to all of them.
 *
 * A source is never allowed to take the app down with it: every call is
 * isolated, a broken plugin contributes a [ExtractorFailure] and nothing else,
 * and results are tagged with the source that produced them.
 */
class ExtractorRegistry {

    private val sources = CopyOnWriteArrayList<Extractor>()

    val extractors: List<Extractor> get() = sources.toList()

    fun register(extractor: Extractor): Boolean {
        if (sources.any { it.id == extractor.id }) return false
        sources.add(extractor)
        return true
    }

    fun unregister(id: String): Boolean = sources.removeAll { it.id == id }

    fun clear() = sources.clear()

    fun find(id: String): Extractor? = sources.firstOrNull { it.id == id }

    suspend fun search(query: MediaQuery): SearchOutcome {
        val text = query.text.trim()
        if (text.isEmpty()) return SearchOutcome(emptyList())

        val active = sources.filter { Capability.SEARCH in it.capabilities }
        if (active.isEmpty()) return SearchOutcome(emptyList())

        val attempts = coroutineScope {
            active.map { source ->
                async { source to runCatching { source.search(query.copy(text = text)) } }
            }.awaitAll()
        }

        val items = mutableListOf<MediaTrack>()
        val failures = mutableListOf<ExtractorFailure>()

        attempts.forEach { (source, result) ->
            result.onSuccess { found ->
                items += found.take(query.limit).map { it.copy(extractorId = source.id) }
            }.onFailure { error ->
                failures += ExtractorFailure(
                    extractorId = source.id,
                    displayName = source.displayName,
                    reason = error.toError(),
                    message = error.toUserMessage(source),
                )
            }
        }

        return SearchOutcome(items = items.take(query.limit * active.size), failures = failures)
    }

    suspend fun resolve(track: MediaTrack): Result<StreamLink> {
        val source = find(track.extractorId)
            ?: return Result.failure(ExtractionException("Unknown source ${track.extractorId}"))
        return runCatching { source.resolve(track) }
    }

    private fun Throwable.toError(): ExtractorError = when (this) {
        is ExtractionException -> ExtractorError.RESOLVE_FAILED
        is java.io.IOException -> ExtractorError.NETWORK
        else -> ExtractorError.UNKNOWN
    }

    private fun Throwable.toUserMessage(source: Extractor): String = when (this) {
        is ExtractionException -> userMessage
        is java.io.IOException -> "Network error in ${source.displayName}"
        else -> "${source.displayName} could not answer this request"
    }
}
