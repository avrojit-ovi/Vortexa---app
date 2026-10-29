package com.vortexa.feature.video.player

/**
 * A guard against small hands, not a security boundary: four digits, stored in
 * plain preferences, and it only hides the player controls.
 */
object KidsLock {

    const val PIN_LENGTH = 4

    fun isValidPin(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    /** An empty stored PIN means "no PIN chosen yet", which never matches. */
    fun matches(stored: String, entered: String): Boolean =
        stored.isNotEmpty() && stored == entered
}
