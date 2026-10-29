package com.vortexa.feature.onlinevideo

import com.vortexa.core.media.Capability
import com.vortexa.core.media.ExtractionException
import com.vortexa.core.media.Extractor
import com.vortexa.core.media.MediaQuery
import com.vortexa.core.media.MediaTrack
import com.vortexa.core.media.StreamLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Layer (a): NewPipeExtractor resolves the stream, Media3 plays it.
 *
 * Everything here runs off the main thread and every failure is translated into
 * an [ExtractionException] with a message the UI can show. When this cannot
 * produce a playable URL, the caller opens layer (b), the official embed.
 */
class YouTubeExtractor(
    private val initializer: NewPipeInitializer,
) : Extractor {

    override val id: String = ID
    override val displayName: String = "YouTube"
    override val version: String = "NewPipeExtractor 0.24.8"
    override val capabilities: Set<Capability> =
        setOf(Capability.SEARCH, Capability.RESOLVE, Capability.PLAYLISTS)

    override suspend fun search(query: MediaQuery): List<MediaTrack> = withContext(Dispatchers.IO) {
        initializer.ensureInitialized()
        try {
            val extractor = ServiceList.YouTube.getSearchExtractor(query.text)
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .take(query.limit)
                .map { item ->
                    MediaTrack(
                        id = item.url,
                        title = item.name,
                        artist = item.uploaderName,
                        durationMs = item.duration.takeIf { it > 0 }?.times(1000) ?: 0L,
                        thumbnailUrl = item.thumbnails.firstOrNull()?.url,
                        isLive = item.duration <= 0,
                        extractorId = id,
                        fallbackUrl = videoIdOf(item.url)?.let { videoId ->
                            "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0"
                        },
                    )
                }
        } catch (e: Exception) {
            throw ExtractionException(
                message = "YouTube search failed: ${e.message}",
                userMessage = "YouTube did not answer the search. The web player is still available.",
                cause = e,
            )
        }
    }

    override suspend fun resolve(track: MediaTrack): StreamLink = withContext(Dispatchers.IO) {
        initializer.ensureInitialized()
        try {
            val extractor = ServiceList.YouTube.getStreamExtractor(track.id)
            extractor.fetchPage()

            val muxed = extractor.videoStreams
                .filter { !it.isVideoOnly }
                .maxByOrNull { it.bitrate }

            if (muxed != null) {
                return@withContext linkOf(muxed)
            }

            // No muxed track: play the best audio on its own rather than failing,
            // which is what a music request actually wants.
            val audio = extractor.audioStreams.maxByOrNull { it.averageBitrate }
                ?: throw ExtractionException(
                    message = "No playable stream for ${track.id}",
                    userMessage = "This video has no direct stream; opening the web player instead.",
                )
            linkOf(audio)
        } catch (e: ExtractionException) {
            throw e
        } catch (e: Exception) {
            throw ExtractionException(
                message = "YouTube resolve failed: ${e.message}",
                userMessage = "This video cannot be extracted right now; opening the web player instead.",
                cause = e,
            )
        }
    }

    private fun linkOf(stream: Stream): StreamLink {
        val url = requireNotNull(stream.url) {
            "NewPipe returned a stream without a URL"
        }
        return StreamLink(
            url = url,
            mimeType = stream.format?.mimeType,
            isHls = stream.isUrl,
        )
    }

    companion object {
        const val ID = "youtube.newpipe"

        /** `watch?v=ID`, `youtu.be/ID`, `shorts/ID` -> ID. */
        fun videoIdOf(url: String): String? {
            val candidate = when {
                url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore('?')
                url.contains("v=") -> url.substringAfter("v=").substringBefore('&')
                url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore('?')
                url.contains("/embed/") -> url.substringAfter("/embed/").substringBefore('?')
                else -> null
            }
            return candidate?.takeIf { it.length in 5..20 }
        }
    }
}

/**
 * NewPipe keeps process-wide state, so initialisation happens exactly once.
 */
class NewPipeInitializer(
    private val downloader: NewPipeDownloader = NewPipeDownloader(),
) {
    private val initialized = AtomicBoolean(false)

    fun ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            NewPipe.init(downloader)
        }
    }
}
