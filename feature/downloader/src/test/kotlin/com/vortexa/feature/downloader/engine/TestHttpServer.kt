package com.vortexa.feature.downloader.engine

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Minimal HTTP/1.1 server for engine tests: real sockets, real Range handling,
 * no mocking. It deliberately understands only what the engine needs.
 */
class TestHttpServer(
    private val content: ByteArray,
    private val supportsRanges: Boolean = true,
    private val etag: String? = "\"v1\"",
    private val lastModified: String? = "Wed, 21 Oct 2026 07:28:00 GMT",
    private val contentDisposition: String? = null,
    private val failuresBeforeSuccess: Int = 0,
    private val contentType: String = "application/octet-stream",
) : AutoCloseable {

    data class RecordedRequest(val method: String, val path: String, val range: String?)

    private val serverSocket = ServerSocket(0, 64, InetAddress.getLoopbackAddress())
    private val executor = Executors.newCachedThreadPool()
    private val failuresLeft = AtomicInteger(failuresBeforeSuccess)

    val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())

    val url: HttpUrl get() = "http://127.0.0.1:${serverSocket.localPort}/file.bin".toHttpUrl()

    fun start(): TestHttpServer {
        executor.submit {
            while (!serverSocket.isClosed) {
                val socket = runCatching { serverSocket.accept() }.getOrNull() ?: break
                executor.submit { socket.use(::handle) }
            }
        }
        return this
    }

    override fun close() {
        runCatching { serverSocket.close() }
        executor.shutdownNow()
    }

    private fun handle(socket: Socket) {
        val input = socket.getInputStream()
        val reader = input.bufferedReader(Charsets.ISO_8859_1)
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0]
        val path = parts[1]

        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
            }
        }

        val rangeHeader = headers["range"]
        requests += RecordedRequest(method, path, rangeHeader)

        val output = socket.getOutputStream()
        val isProbe = rangeHeader == "bytes=0-0"

        if (!isProbe && method == "GET" && failuresLeft.getAndDecrement() > 0) {
            writeResponse(output, 500, "Internal Server Error", emptyMap(), ByteArray(0))
            return
        }

        if (method == "HEAD") {
            writeResponse(output, 200, "OK", baseHeaders(content.size), ByteArray(0))
            return
        }

        val range = if (supportsRanges) rangeHeader?.let(::parseRange) else null
        if (range != null) {
            if (range.first >= content.size) {
                writeResponse(
                    output,
                    416,
                    "Range Not Satisfiable",
                    mapOf("Content-Range" to "bytes */${content.size}"),
                    ByteArray(0),
                )
                return
            }
            val last = range.last.coerceAtMost(content.size - 1L).toInt()
            val slice = content.copyOfRange(range.first.toInt(), last + 1)
            writeResponse(
                output,
                206,
                "Partial Content",
                baseHeaders(slice.size) + mapOf("Content-Range" to "bytes ${range.first}-$last/${content.size}"),
                slice,
            )
            return
        }

        writeResponse(output, 200, "OK", baseHeaders(content.size), content)
    }

    private fun baseHeaders(length: Int): Map<String, String> = buildMap {
        put("Content-Length", length.toString())
        put("Content-Type", contentType)
        put("Accept-Ranges", if (supportsRanges) "bytes" else "none")
        etag?.let { put("ETag", it) }
        lastModified?.let { put("Last-Modified", it) }
        contentDisposition?.let { put("Content-Disposition", it) }
    }

    private fun writeResponse(
        output: java.io.OutputStream,
        code: Int,
        reason: String,
        headers: Map<String, String>,
        body: ByteArray,
    ) {
        val head = ByteArrayOutputStream()
        head.write("HTTP/1.1 $code $reason\r\n".toByteArray(Charsets.ISO_8859_1))
        headers.forEach { (name, value) ->
            head.write("$name: $value\r\n".toByteArray(Charsets.ISO_8859_1))
        }
        head.write("Connection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        output.write(head.toByteArray())
        if (body.isNotEmpty()) output.write(body)
        output.flush()
    }

    /** `bytes=10-19` -> 10L..19L, or null when the header is absent/broken. */
    private fun parseRange(header: String): LongRange? {
        val match = Regex("""bytes=(\d+)-(\d*)""").find(header) ?: return null
        val first = match.groupValues[1].toLongOrNull() ?: return null
        val last = match.groupValues[2].toLongOrNull() ?: (content.size - 1L)
        if (last < first) return null
        return first..last
    }
}
