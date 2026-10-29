package com.vortexa.feature.downloader.engine

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadEngineTest {

    private lateinit var tempDir: File

    private fun payload(size: Int): ByteArray = ByteArray(size) { index -> (index % 251).toByte() }

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("vortexa-dl").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun engine() = DownloadEngine(
        client = OkHttpClient.Builder().retryOnConnectionFailure(false).build(),
    )

    @Test
    fun `probe reports size, range support and validators`() = runTest {
        TestHttpServer(content = payload(5_000)).start().use { server ->
            val metadata = engine().probe(server.url)

            assertEquals(5_000L, metadata.totalBytes)
            assertTrue(metadata.supportsRanges)
            assertEquals("\"v1\"", metadata.etag)
            assertEquals("Wed, 21 Oct 2026 07:28:00 GMT", metadata.lastModified)
            assertEquals("file.bin", metadata.suggestedFileName)
            assertEquals("application/octet-stream", metadata.contentType)
        }
    }

    @Test
    fun `probe keeps the server content type for category grouping`() = runTest {
        TestHttpServer(content = payload(1_000), contentType = "video/mp4; codecs=avc1")
            .start()
            .use { server ->
                // The manager strips the parameters before it stores the value.
                assertEquals("video/mp4; codecs=avc1", engine().probe(server.url).contentType)
            }
    }

    @Test
    fun `probe falls back to content length when ranges are unsupported`() = runTest {
        TestHttpServer(content = payload(2_000), supportsRanges = false).start().use { server ->
            val metadata = engine().probe(server.url)

            assertEquals(2_000L, metadata.totalBytes)
            assertEquals(false, metadata.supportsRanges)
            assertEquals(false, metadata.isResumable)
        }
    }

    @Test
    fun `segmented download writes the exact bytes in parallel ranges`() = runTest {
        val content = payload(400_000)
        TestHttpServer(content = content).start().use { server ->
            val target = File(tempDir, "movie.mp4")
            val events = engine().download(
                DownloadSpec(
                    url = server.url,
                    file = target,
                    partCount = 4,
                    minPartBytes = 1_000,
                ),
            ).toList()

            assertArrayEquals(content, target.readBytes())
            assertTrue(events.any { it is DownloadEvent.Metadata })
            assertEquals(DownloadEvent.Completed, events.last())

            val ranges = server.requests.mapNotNull { it.range }.filter { it != "bytes=0-0" }
            assertEquals("expected one request per planned segment", 4, ranges.size)
            assertEquals(4, ranges.toSet().size)
        }
    }

    @Test
    fun `downloads in one request when the server ignores ranges`() = runTest {
        val content = payload(120_000)
        TestHttpServer(content = content, supportsRanges = false).start().use { server ->
            val target = File(tempDir, "single.bin")
            val events = engine().download(
                DownloadSpec(url = server.url, file = target, partCount = 8, minPartBytes = 1_000),
            ).toList()

            assertArrayEquals(content, target.readBytes())
            assertEquals(DownloadEvent.Completed, events.last())
            val bodyRequests = server.requests.filter { it.range != "bytes=0-0" && it.method == "GET" }
            assertEquals("only one full-body request is expected", 1, bodyRequests.size)
        }
    }

    @Test
    fun `resume only requests the missing bytes`() = runTest {
        val content = payload(200_000)
        val alreadyDownloaded = 80_000
        val target = File(tempDir, "resume.bin")
        target.writeBytes(content.copyOfRange(0, alreadyDownloaded))

        TestHttpServer(content = content).start().use { server ->
            val events = engine().download(
                DownloadSpec(
                    url = server.url,
                    file = target,
                    resumeStates = listOf(SegmentState(start = 0, end = content.size - 1L, downloaded = alreadyDownloaded.toLong())),
                ),
            ).toList()

            assertArrayEquals(content, target.readBytes())
            assertEquals(DownloadEvent.Completed, events.last())
            val ranges = server.requests.mapNotNull { it.range }.filter { it != "bytes=0-0" }
            assertEquals(listOf("bytes=$alreadyDownloaded-${content.size - 1}"), ranges)
        }
    }

    @Test
    fun `retries a failed range before giving up`() = runTest {
        val content = payload(50_000)
        TestHttpServer(content = content, failuresBeforeSuccess = 1).start().use { server ->
            val target = File(tempDir, "retry.bin")
            val events = engine().download(
                DownloadSpec(
                    url = server.url,
                    file = target,
                    partCount = 1,
                    minPartBytes = 1,
                    maxRetries = 2,
                    retryDelayMillis = 10,
                ),
            ).toList()

            assertArrayEquals(content, target.readBytes())
            assertEquals(DownloadEvent.Completed, events.last())
            val attempts = server.requests.count { it.range != "bytes=0-0" && it.method == "GET" }
            assertEquals("one failed attempt plus one success", 2, attempts)
        }
    }

    @Test
    fun `reports failure and keeps the partial file when retries run out`() = runTest {
        val content = payload(50_000)
        TestHttpServer(content = content, failuresBeforeSuccess = 10).start().use { server ->
            val target = File(tempDir, "fail.bin")
            val events = engine().download(
                DownloadSpec(
                    url = server.url,
                    file = target,
                    partCount = 1,
                    minPartBytes = 1,
                    maxRetries = 1,
                    retryDelayMillis = 10,
                ),
            ).toList()

            assertTrue(events.last() is DownloadEvent.Failed)
            assertTrue("the partial file must survive for a later resume", target.exists())
        }
    }

    @Test
    fun `parses totals and file names from response headers`() {
        assertEquals(1_234L, DownloadEngine.parseContentRangeTotal("bytes 0-99/1234"))
        assertEquals(-1L, DownloadEngine.parseContentRangeTotal(null))
        assertEquals(-1L, DownloadEngine.parseContentRangeTotal("bytes 0-99/*"))

        assertEquals("movie.mkv", DownloadEngine.parseFileName("attachment; filename=\"movie.mkv\""))
        assertEquals("naïve.mp3", DownloadEngine.parseFileName("attachment; filename*=UTF-8''naïve.mp3"))
        assertEquals(null, DownloadEngine.parseFileName(null))
    }
}
