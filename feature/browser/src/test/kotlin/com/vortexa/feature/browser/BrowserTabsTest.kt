package com.vortexa.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabsTest {

    private val home = BrowserUrl.HOME

    @Test
    fun `a fresh window has one active tab`() {
        val state = BrowserTabs.initial()
        assertEquals(1, state.tabs.size)
        assertEquals(1L, state.activeId)
        assertEquals(home, state.active.url)
    }

    @Test
    fun `a new tab opens next to the active one and takes focus`() {
        val state = BrowserTabs.open(BrowserTabs.initial(), "https://example.com")
        assertEquals(2, state.tabs.size)
        assertEquals("https://example.com", state.active.url)
        assertEquals(2L, state.tabs.last().id)
        assertEquals(3L, state.nextId)
    }

    @Test
    fun `at the cap a new tab replaces the active one instead of being dropped`() {
        var state = BrowserTabs.initial()
        repeat(MAX_TABS - 1) { index -> state = BrowserTabs.open(state, "https://site$index.example") }
        assertEquals(MAX_TABS, state.tabs.size)
        assertFalse(state.canOpenMore)

        val replaced = BrowserTabs.open(state, "https://last.example")
        assertEquals(MAX_TABS, replaced.tabs.size)
        assertEquals("https://last.example", replaced.active.url)
    }

    @Test
    fun `closing the active tab moves focus to its neighbour`() {
        var state = BrowserTabs.open(BrowserTabs.initial(), "https://b.example")
        state = BrowserTabs.open(state, "https://c.example")
        // tabs: 1 home, 2 b, 3 c(active)
        val afterClose = BrowserTabs.close(state, state.activeId)
        assertEquals(2, afterClose.tabs.size)
        assertEquals(2L, afterClose.activeId)
    }

    @Test
    fun `closing the last tab keeps the window usable`() {
        val state = BrowserTabs.initial()
        assertEquals(state, BrowserTabs.close(state, state.activeId))
    }

    @Test
    fun `closing an unknown tab changes nothing`() {
        val state = BrowserTabs.initial()
        assertEquals(state, BrowserTabs.close(state, 42L))
    }

    @Test
    fun `selecting an unknown tab is ignored`() {
        val state = BrowserTabs.initial()
        assertEquals(state, BrowserTabs.select(state, 42L))
    }

    @Test
    fun `updating the active tab keeps a title it does not overwrite`() {
        val state = BrowserTabs.updateActive(BrowserTabs.initial(), "https://a.example", "A")
        assertEquals("A", state.active.title)
        val later = BrowserTabs.updateActive(state, "https://a.example/page")
        assertEquals("A", later.active.title)
        assertEquals("https://a.example/page", later.active.url)
    }

    @Test
    fun `switching tabs does not lose the other tabs' urls`() {
        var state = BrowserTabs.open(BrowserTabs.initial(), "https://b.example")
        state = BrowserTabs.open(state, "https://c.example")
        val first = state.tabs.first().id
        val switched = BrowserTabs.select(state, first)
        assertEquals(home, switched.active.url)
        assertEquals(listOf(home, "https://b.example", "https://c.example"), switched.tabs.map { it.url })
        assertTrue(switched.tabs.size == 3)
    }
}
