package com.vortexa.core.database

import com.vortexa.core.database.entity.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStatusTest {

    @Test
    fun `unknown or null storage values fall back to queued`() {
        assertEquals(DownloadStatus.QUEUED, DownloadStatus.fromStorage(null))
        assertEquals(DownloadStatus.QUEUED, DownloadStatus.fromStorage("nonsense"))
        assertEquals(DownloadStatus.PAUSED, DownloadStatus.fromStorage("PAUSED"))
    }

    @Test
    fun `terminal states are completed and canceled only`() {
        assertTrue(DownloadStatus.COMPLETED.isTerminal)
        assertTrue(DownloadStatus.CANCELED.isTerminal)
        assertFalse(DownloadStatus.FAILED.isTerminal)
        assertFalse(DownloadStatus.RUNNING.isTerminal)
    }
}
