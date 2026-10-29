package com.vortexa.feature.downloader.engine

/**
 * A daily window in which downloads are allowed to start.
 *
 * Windows that cross midnight (23:00 → 06:00) are the common case, so they are
 * first-class here instead of a special case at the call site. The start minute
 * is included and the end minute is not, which makes 23:00–06:00 exactly seven
 * hours.
 */
data class DownloadSchedule(
    val enabled: Boolean = false,
    val startMinuteOfDay: Int = DEFAULT_START,
    val endMinuteOfDay: Int = DEFAULT_END,
) {

    fun allows(minuteOfDay: Int): Boolean {
        if (!enabled) return true
        val minute = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        if (startMinuteOfDay == endMinuteOfDay) return true
        return if (startMinuteOfDay < endMinuteOfDay) {
            minute >= startMinuteOfDay && minute < endMinuteOfDay
        } else {
            minute >= startMinuteOfDay || minute < endMinuteOfDay
        }
    }

    /** "23:00 – 06:00", for the settings row. */
    fun label(): String = "${format(startMinuteOfDay)} – ${format(endMinuteOfDay)}"

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val DEFAULT_START = 23 * 60
        const val DEFAULT_END = 6 * 60

        fun format(minuteOfDay: Int): String {
            val minute = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
            return "%02d:%02d".format(minute / 60, minute % 60)
        }

        fun minuteOfDay(hour: Int, minute: Int): Int =
            (hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59))
    }
}
