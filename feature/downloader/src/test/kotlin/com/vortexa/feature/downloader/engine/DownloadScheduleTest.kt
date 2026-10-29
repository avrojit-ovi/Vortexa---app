package com.vortexa.feature.downloader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadScheduleTest {

    private fun minute(hour: Int, minute: Int) = DownloadSchedule.minuteOfDay(hour, minute)

    @Test
    fun `a disabled schedule allows everything`() {
        assertTrue(DownloadSchedule(enabled = false).allows(minute(12, 0)))
    }

    @Test
    fun `a daytime window includes the start and excludes the end`() {
        val schedule = DownloadSchedule(
            enabled = true,
            startMinuteOfDay = minute(9, 0),
            endMinuteOfDay = minute(17, 0),
        )
        assertTrue(schedule.allows(minute(9, 0)))
        assertTrue(schedule.allows(minute(12, 30)))
        assertFalse(schedule.allows(minute(17, 0)))
        assertFalse(schedule.allows(minute(8, 59)))
    }

    @Test
    fun `a window across midnight works`() {
        val schedule = DownloadSchedule(
            enabled = true,
            startMinuteOfDay = minute(23, 0),
            endMinuteOfDay = minute(6, 0),
        )
        assertTrue(schedule.allows(minute(23, 0)))
        assertTrue(schedule.allows(minute(23, 59)))
        assertTrue(schedule.allows(minute(0, 0)))
        assertTrue(schedule.allows(minute(5, 59)))
        assertFalse(schedule.allows(minute(6, 0)))
        assertFalse(schedule.allows(minute(12, 0)))
    }

    @Test
    fun `an empty window means all day`() {
        val schedule = DownloadSchedule(
            enabled = true,
            startMinuteOfDay = minute(8, 0),
            endMinuteOfDay = minute(8, 0),
        )
        assertTrue(schedule.allows(minute(3, 0)))
        assertTrue(schedule.allows(minute(8, 0)))
    }

    @Test
    fun `times format and clamp`() {
        assertEquals(minute(0, 0), DownloadSchedule.minuteOfDay(-5, -5))
        assertEquals(minute(23, 59), DownloadSchedule.minuteOfDay(99, 99))
        assertEquals("23:00 – 06:00", DownloadSchedule().label())
        assertEquals("09:05", DownloadSchedule.format(minute(9, 5)))
    }
}
