package com.vortexa.core.online

import com.vortexa.core.media.Extractor

/**
 * Lets feature modules contribute a source without the core knowing them.
 *
 * The online repository injects every provider through Hilt's set multibinding,
 * so adding a service is a new module binding, not a switch inside the core.
 */
interface BuiltInSourceProvider {
    fun extractors(): List<Extractor>
}
