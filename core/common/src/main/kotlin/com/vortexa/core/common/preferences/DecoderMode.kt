package com.vortexa.core.common.preferences

/**
 * Which codec the player should reach for.
 *
 * SOFTWARE is the escape hatch for files a device's hardware decoder handles
 * badly (or not at all); it costs battery, so AUTO — hardware first, software
 * as the fallback — stays the default.
 */
enum class DecoderMode {
    AUTO,
    SOFTWARE,
    ;

    companion object {
        fun fromStorage(raw: String?): DecoderMode =
            entries.firstOrNull { it.name == raw } ?: AUTO
    }
}
