package com.vortexa.feature.video.player

import android.content.Context
import androidx.core.content.edit

/**
 * Last watched position per absolute file path.
 *
 * Kept in plain preferences rather than Room: it is a cache, and losing it must
 * never cost the user a download record.
 */
class PlaybackPositions(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun lastPosition(path: String): Long = prefs.getLong(key(path), 0L)

    fun save(path: String, positionMs: Long) {
        prefs.edit { putLong(key(path), positionMs.coerceAtLeast(0L)) }
    }

    fun clear(path: String) {
        prefs.edit { remove(key(path)) }
    }

    private fun key(path: String): String = "pos:$path"

    private companion object {
        const val PREFERENCES_NAME = "playback_positions"
    }
}
