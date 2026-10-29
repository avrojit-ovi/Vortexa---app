package com.vortexa.feature.browser

import java.net.URLEncoder

/** Hard cap: every tab keeps a live WebView's worth of state. */
const val MAX_TABS = 8

data class BrowserTab(
    val id: Long,
    val url: String,
    val title: String = "",
)

data class BrowserTabsState(
    val tabs: List<BrowserTab>,
    val activeId: Long,
    val nextId: Long,
) {
    val active: BrowserTab get() = tabs.firstOrNull { it.id == activeId } ?: tabs.first()
    val canOpenMore: Boolean get() = tabs.size < MAX_TABS
}

/**
 * Tab bookkeeping as pure functions, so the awkward cases (closing the active
 * tab, closing the last tab, the tab cap) are covered by tests instead of by
 * poking at the UI.
 */
object BrowserTabs {

    fun initial(url: String = BrowserUrl.HOME): BrowserTabsState =
        BrowserTabsState(
            tabs = listOf(BrowserTab(id = 1L, url = url)),
            activeId = 1L,
            nextId = 2L,
        )

    /** Opens [url] next to the active tab. At the cap, it replaces the active tab. */
    fun open(state: BrowserTabsState, url: String): BrowserTabsState {
        if (!state.canOpenMore) {
            return state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == state.activeId) tab.copy(url = url, title = "") else tab
                },
            )
        }
        val tab = BrowserTab(id = state.nextId, url = url)
        val index = state.tabs.indexOfFirst { it.id == state.activeId }
        val tabs = state.tabs.toMutableList().apply { add(index + 1, tab) }
        return BrowserTabsState(tabs = tabs, activeId = tab.id, nextId = state.nextId + 1)
    }

    /** Never leaves the window tab-less; the last tab simply stays. */
    fun close(state: BrowserTabsState, id: Long): BrowserTabsState {
        if (state.tabs.size <= 1) return state
        val index = state.tabs.indexOfFirst { it.id == id }
        if (index < 0) return state
        val tabs = state.tabs.filterNot { it.id == id }
        val activeId = if (state.activeId == id) {
            tabs[minOf(index, tabs.lastIndex)].id
        } else {
            state.activeId
        }
        return state.copy(tabs = tabs, activeId = activeId)
    }

    fun select(state: BrowserTabsState, id: Long): BrowserTabsState =
        if (state.tabs.any { it.id == id }) state.copy(activeId = id) else state

    fun update(state: BrowserTabsState, id: Long, url: String, title: String? = null): BrowserTabsState =
        state.copy(
            tabs = state.tabs.map { tab ->
                if (tab.id != id) tab else tab.copy(url = url, title = title ?: tab.title)
            },
        )

    fun updateActive(state: BrowserTabsState, url: String, title: String? = null): BrowserTabsState =
        update(state, state.activeId, url, title)
}

/**
 * Turns whatever the user typed into something the WebView can load.
 *
 * A bare host gets https, words become a search, and non-web schemes are
 * refused instead of being handed to the WebView — `intent:` and `file:` URLs
 * belong to the system, not to this browser.
 */
object BrowserUrl {

    const val HOME = "https://duckduckgo.com/"
    private const val SEARCH = "https://duckduckgo.com/?q="

    fun toLoadUrl(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("https://", ignoreCase = true)
        ) {
            return text
        }
        if (text.contains("://")) return null
        if (text.startsWith("localhost", ignoreCase = true)) return "http://$text"
        // `javascript:`, `mailto:`, `intent:` — a scheme, not a host, and nothing
        // this browser is allowed to run.
        val colon = text.indexOf(':')
        if (colon > 0 && !text.substring(0, colon).contains('.')) return null
        if (text.contains(' ') || !text.contains('.')) return SEARCH + encode(text)
        return "https://$text"
    }

    /** What the address bar shows: no scheme, no trailing slash. */
    fun display(url: String): String =
        url.removePrefix("https://").removePrefix("http://").removeSuffix("/")

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
}
