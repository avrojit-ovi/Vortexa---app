package com.vortexa.feature.browser.adblock

/**
 * A small, honest subset of EasyList/uBlock syntax.
 *
 * Supported: comments (`!`), cosmetic rules (`##`, skipped), exception rules
 * (`@@`), domain anchors (`||example.com`), plain substrings, `*` wildcards and
 * the `|` start/end anchors. Options after `$` are stripped — we block by URL
 * only, which is what a WebView can enforce reliably.
 *
 * Not supported on purpose: scriptlets, `#@#` exceptions, regex rules (`/…/`)
 * and per-resource-type options. A rule we cannot honour is skipped rather than
 * half-applied, so the engine never claims coverage it does not have.
 */
class AdblockEngine private constructor(
    private val rules: List<Rule>,
) {

    data class Stats(val loadedRules: Int, val skippedLines: Int)

    val stats: Stats
        get() = currentStats

    private var currentStats = Stats(rules.size, 0)

    fun shouldBlock(url: String): Boolean {
        if (url.isBlank()) return false
        val normalized = url.lowercase()
        // Allow exceptions to win over block rules, as the lists intend.
        if (rules.any { it.exception && it.matches(normalized) }) return false
        return rules.any { !it.exception && it.matches(normalized) }
    }

    internal data class Rule(
        val exception: Boolean,
        val pattern: String,
        val domainAnchor: Boolean,
        val startAnchor: Boolean,
        val endAnchor: Boolean,
    ) {
        fun matches(url: String): Boolean {
            val needle = pattern
            if (needle.isEmpty()) return false

            if (!needle.contains('*')) {
                return when {
                    domainAnchor -> url.contains("://$needle") ||
                        url.contains("://www.$needle") ||
                        url.contains(".$needle")

                    startAnchor && endAnchor -> url == needle
                    startAnchor -> url.startsWith(needle)
                    endAnchor -> url.endsWith(needle)
                    else -> url.contains(needle)
                }
            }

            // Wildcards: escape each literal chunk, let '*' span anything.
            val body = needle.split('*').joinToString(".*") { Regex.escape(it) }
            val pattern = buildString {
                if (startAnchor) append('^')
                append(body)
                if (endAnchor) append('$')
            }
            return Regex(pattern).containsMatchIn(url)
        }
    }

    companion object {

        fun empty(): AdblockEngine = AdblockEngine(emptyList())

        fun fromLines(lines: List<String>): Pair<AdblockEngine, Stats> {
            var skipped = 0
            val rules = lines.mapNotNull { raw ->
                val line = raw.trim()
                when {
                    line.isEmpty() -> null
                    line.startsWith("!") || line.startsWith("[") -> null
                    line.contains("##") || line.contains("#@#") || line.contains("#?#") -> {
                        skipped++
                        null
                    }
                    // Only `/…/` lines that actually read as regex are refused;
                    // a plain path like `/pagead/` is a perfectly good substring
                    // rule and must not be thrown away.
                    line.startsWith("/") && line.endsWith("/") &&
                        line.length > 2 && line.any { it in REGEX_CHARS } -> {
                        skipped++
                        null
                    }
                    else -> parse(line) ?: run { skipped++; null }
                }
            }
            val engine = AdblockEngine(rules)
            val stats = Stats(rules.size, skipped)
            engine.currentStats = stats
            return engine to stats
        }

        /** A handful of the most common ad hosts, used until a list is imported. */
        val bundledRules: List<String> = listOf(
            "||doubleclick.net",
            "||googlesyndication.com",
            "||googleadservices.com",
            "||adservice.google.com",
            "||adnxs.com",
            "||criteo.com",
            "||taboola.com",
            "||outbrain.com",
            "||scorecardresearch.com",
            "||hotjar.com",
            "/pagead/",
            "/ads/",
            "/adserver/",
            "/analytics.js",
            "||facebook.com/tr",
            "@@||googlesyndication.com/pagead/js/loader.js",
        )

        private val REGEX_CHARS = "\\[](){}+^$?".toSet()

        private fun parse(line: String): Rule? {
            var body = line
            val exception = body.startsWith("@@")
            if (exception) body = body.removePrefix("@@")

            // Options (`$third-party`, `$script`) and element hiding are out of scope.
            if (body.contains('$')) body = body.substringBefore('$')
            if (body.isEmpty()) return null

            val domainAnchor = body.startsWith("||")
            if (domainAnchor) body = body.removePrefix("||")

            val startAnchor = body.startsWith("|")
            if (startAnchor) body = body.removePrefix("|")
            val endAnchor = body.endsWith("|")
            if (endAnchor) body = body.removePrefix("/").removeSuffix("|")

            val pattern = body.lowercase().removePrefix("http://").removePrefix("https://")
            if (pattern.isEmpty()) return null
            return Rule(
                exception = exception,
                pattern = pattern,
                domainAnchor = domainAnchor,
                startAnchor = startAnchor && !domainAnchor,
                endAnchor = endAnchor,
            )
        }
    }
}
