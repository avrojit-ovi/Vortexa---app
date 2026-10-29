package com.vortexa.core.common.file

/**
 * Turns anything that arrives from the network (Content-Disposition, URL path,
 * magnet display name, ...) into a file name that is safe to write on disk.
 *
 * Security notes:
 *  - path traversal ("..", "/", "\\") is stripped, never resolved;
 *  - control characters and the NUL byte are removed;
 *  - Windows reserved device names are prefixed;
 *  - the extension is preserved when the name has to be truncated.
 */
object FileNameSanitizer {

    private const val MAX_LENGTH = 200
    private const val FALLBACK = "download"

    private val ILLEGAL = Regex("""[\\/:*?"<>|\u0000-\u001F\u007F]""")
    private val RESERVED = Regex(
        """^(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\..*)?$""",
    )
    private val MULTI_DOTS = Regex("""\.{2,}""")

    fun sanitize(rawName: String?, fallback: String = FALLBACK): String {
        val baseName = rawName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.let { ILLEGAL.replace(it, "") }
            ?.replace(MULTI_DOTS, ".")
            ?.trim()
            ?.trimEnd('.', ' ')
            ?.let { if (it == "." || it == "..") "" else it }
            .orEmpty()

        val safe = baseName.ifBlank { fallback.ifBlank { FALLBACK } }
        val reservedSafe = if (RESERVED.matches(safe)) "_$safe" else safe
        return truncatePreservingExtension(reservedSafe)
    }

    private fun truncatePreservingExtension(name: String): String {
        if (name.length <= MAX_LENGTH) return name
        val dot = name.lastIndexOf('.')
        val extension = if (dot > 0 && name.length - dot <= 12) name.substring(dot) else ""
        val keep = (MAX_LENGTH - extension.length).coerceAtLeast(1)
        return name.substring(0, keep) + extension
    }
}
