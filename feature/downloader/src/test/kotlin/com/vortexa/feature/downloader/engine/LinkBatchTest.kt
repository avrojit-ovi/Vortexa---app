package com.vortexa.feature.downloader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkBatchTest {

    @Test
    fun `one link per line becomes one link per entry`() {
        val links = LinkBatch.parse(
            """
            https://example.com/one.zip
            https://example.com/two.pdf

            https://example.com/three.mp4
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "https://example.com/one.zip",
                "https://example.com/two.pdf",
                "https://example.com/three.mp4",
            ),
            links,
        )
    }

    @Test
    fun `spaces and commas separate a pasted list too`() {
        val links = LinkBatch.parse(
            "https://example.com/a.mp3, https://example.com/b.flac https://example.com/c.ogg",
        )

        assertEquals(3, links.size)
        assertEquals("https://example.com/a.mp3", links[0])
    }

    @Test
    fun `duplicates collapse in the order they were pasted`() {
        val links = LinkBatch.parse(
            """
            https://example.com/a.zip
            https://example.com/b.zip
            https://example.com/a.zip
            """.trimIndent(),
        )

        assertEquals(listOf("https://example.com/a.zip", "https://example.com/b.zip"), links)
    }

    @Test
    fun `words and broken links are skipped, not fatal`() {
        val links = LinkBatch.parse(
            "download these: https://example.com/good.zip and this not-a-link, example.com/no-scheme",
        )

        assertEquals(listOf("https://example.com/good.zip"), links)
    }

    @Test
    fun `punctuation around a copied link is stripped`() {
        val links = LinkBatch.parse("(https://example.com/report.pdf).")

        assertEquals(listOf("https://example.com/report.pdf"), links)
    }

    @Test
    fun `only http and https are accepted`() {
        val links = LinkBatch.parse("ftp://example.com/a.mp3 file:///sdcard/b.txt https://example.com/c.mp3")

        assertEquals(listOf("https://example.com/c.mp3"), links)
    }

    @Test
    fun `the batch is capped to protect the queue`() {
        val text = (1..80).joinToString("\n") { "https://example.com/file$it.zip" }

        assertEquals(LinkBatch.MAX_LINKS, LinkBatch.parse(text).size)
        assertEquals(3, LinkBatch.parse(text, limit = 3).size)
    }

    @Test
    fun `blank input and a zero limit return nothing`() {
        assertTrue(LinkBatch.parse("").isEmpty())
        assertTrue(LinkBatch.parse("   \n\t  ").isEmpty())
        assertTrue(LinkBatch.parse("https://example.com/a.zip", limit = 0).isEmpty())
    }
}
