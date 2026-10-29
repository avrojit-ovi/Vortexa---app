package com.vortexa.feature.downloader

import com.vortexa.core.database.entity.DownloadEntity
import com.vortexa.core.database.entity.DownloadStatus
import com.vortexa.feature.downloader.engine.DownloadCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsUiStateTest {

    @Test
    fun `nothing is shown while the first database read is still in flight`() {
        val state = DownloadsUiState()

        assertFalse(state.isEmpty)
        assertFalse(state.isFilterEmpty)
    }

    @Test
    fun `an empty queue shows the empty screen`() {
        val state = DownloadsUiState(isLoading = false)

        assertTrue(state.isEmpty)
        assertFalse(state.isFilterEmpty)
    }

    @Test
    fun `a filter that hides every row is not the empty queue`() {
        // The queue has rows, but the selected category matches none of them: the
        // screen must offer a way back instead of pretending nothing was ever added.
        val state = DownloadsUiState(
            isLoading = false,
            downloads = emptyList(),
            selectedCategory = DownloadCategory.VIDEO,
            totalCount = 3,
        )

        assertFalse(state.isEmpty)
        assertTrue(state.isFilterEmpty)
    }

    @Test
    fun `a filter that still matches rows is a normal list`() {
        val state = DownloadsUiState(
            isLoading = false,
            downloads = listOf(download(1, "clip.mp4")),
            selectedCategory = DownloadCategory.VIDEO,
            totalCount = 3,
        )

        assertFalse(state.isEmpty)
        assertFalse(state.isFilterEmpty)
        assertEquals(1, state.downloads.size)
    }

    @Test
    fun `the active count follows the rows the state carries`() {
        val state = DownloadsUiState(
            isLoading = false,
            downloads = listOf(
                download(1, "a.mp4", DownloadStatus.RUNNING),
                download(2, "b.mp4", DownloadStatus.QUEUED),
                download(3, "c.mp4", DownloadStatus.COMPLETED),
            ),
            totalCount = 3,
        )

        assertEquals(1, state.activeCount)
    }

    private fun download(
        id: Long,
        fileName: String,
        status: DownloadStatus = DownloadStatus.QUEUED,
    ) = DownloadEntity(
        id = id,
        url = "https://example.com/$fileName",
        fileName = fileName,
        status = status,
    )
}
