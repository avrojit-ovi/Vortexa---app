package com.vortexa.core.media

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorRegistryTest {

    private class FakeSource(
        override val id: String,
        override val displayName: String = id,
        override val version: String = "1",
        override val capabilities: Set<Capability> = setOf(Capability.SEARCH, Capability.RESOLVE),
        private val result: () -> List<MediaTrack>,
    ) : Extractor {
        override suspend fun search(query: MediaQuery): List<MediaTrack> = result()
        override suspend fun resolve(track: MediaTrack): StreamLink =
            StreamLink("https://cdn.example/${track.id}.m4a")
    }

    @Test
    fun `results carry the id of the source that produced them`() = runTest {
        val registry = ExtractorRegistry()
        registry.register(FakeSource("a") { listOf(MediaTrack(id = "1", title = "One")) })
        registry.register(FakeSource("b") { listOf(MediaTrack(id = "2", title = "Two")) })

        val outcome = registry.search(MediaQuery("x"))

        assertEquals(2, outcome.items.size)
        assertEquals(setOf("a", "b"), outcome.items.map { it.extractorId }.toSet())
        assertTrue(outcome.failures.isEmpty())
    }

    @Test
    fun `a throwing source becomes a failure, not an exception`() = runTest {
        val registry = ExtractorRegistry()
        registry.register(FakeSource("good") { listOf(MediaTrack(id = "1", title = "One")) })
        registry.register(FakeSource("broken") { throw IllegalStateException("boom") })

        val outcome = registry.search(MediaQuery("x"))

        assertEquals(1, outcome.items.size)
        assertEquals("broken", outcome.failures.single().extractorId)
        assertEquals(ExtractorError.UNKNOWN, outcome.failures.single().reason)
    }

    @Test
    fun `network failures are labelled as network failures`() = runTest {
        val registry = ExtractorRegistry()
        registry.register(FakeSource("offline") { throw IOException("no route") })

        val failure = registry.search(MediaQuery("x")).failures.single()

        assertEquals(ExtractorError.NETWORK, failure.reason)
        assertTrue(failure.message.contains("offline"))
    }

    @Test
    fun `plugin messages are surfaced verbatim`() = runTest {
        val registry = ExtractorRegistry()
        registry.register(
            FakeSource("plugin") { throw ExtractionException("boom", "Source is unavailable right now") },
        )

        val failure = registry.search(MediaQuery("x")).failures.single()

        assertEquals(ExtractorError.RESOLVE_FAILED, failure.reason)
        assertEquals("Source is unavailable right now", failure.message)
    }

    @Test
    fun `a blank query never reaches the sources`() = runTest {
        var calls = 0
        val registry = ExtractorRegistry()
        registry.register(FakeSource("a") { calls++; emptyList() })

        assertTrue(registry.search(MediaQuery("   ")).items.isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun `duplicate source ids are rejected`() = runTest {
        val registry = ExtractorRegistry()
        assertTrue(registry.register(FakeSource("a") { emptyList() }))
        assertTrue(!registry.register(FakeSource("a") { emptyList() }))
        assertEquals(1, registry.extractors.size)
    }

    @Test
    fun `resolving through an unknown source fails softly`() = runTest {
        val registry = ExtractorRegistry()
        val result = registry.resolve(MediaTrack(id = "1", title = "One", extractorId = "ghost"))
        assertTrue(result.isFailure)
    }

    @Test
    fun `resolve keeps the link the source returned`() = runTest {
        val registry = ExtractorRegistry()
        registry.register(FakeSource("a") { emptyList() })

        val link = registry.resolve(MediaTrack(id = "7", title = "Seven", extractorId = "a")).getOrThrow()

        assertEquals("https://cdn.example/7.m4a", link.url)
    }
}
