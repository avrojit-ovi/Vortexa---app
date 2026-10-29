package com.vortexa.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vortexa.core.common.preferences.SettingsRepository
import com.vortexa.feature.browser.adblock.AdblockEngine
import com.vortexa.feature.browser.adblock.Kind
import com.vortexa.feature.browser.adblock.MediaSniffer
import com.vortexa.feature.browser.adblock.SniffedMedia
import com.vortexa.feature.downloader.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Feedback the browser owes the user; the screen turns these into strings. */
sealed interface BrowserMessage {
    data object DownloadStarted : BrowserMessage
    data object DownloadFailed : BrowserMessage
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val downloads: DownloadManager,
) : ViewModel() {

    private val _tabs = MutableStateFlow(BrowserTabs.initial())
    val tabs: StateFlow<BrowserTabsState> = _tabs.asStateFlow()

    private val _blocked = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blocked.asStateFlow()

    private val _sniffed = MutableStateFlow<List<SniffedMedia>>(emptyList())
    val sniffed: StateFlow<List<SniffedMedia>> = _sniffed.asStateFlow()

    private val _message = MutableStateFlow<BrowserMessage?>(null)
    val message: StateFlow<BrowserMessage?> = _message.asStateFlow()

    private val engine = AdblockEngine.fromLines(AdblockEngine.bundledRules).first

    // Read from WebView's background thread, so it must not touch Compose state.
    @Volatile
    private var blockAdsEnabled = true

    val blockAds: StateFlow<Boolean> = settings.preferences
        .map { it.blockAds }
        .onEach { blockAdsEnabled = it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    fun setBlockAds(enabled: Boolean) {
        blockAdsEnabled = enabled
        if (!enabled) _blocked.value = 0
        viewModelScope.launch { settings.setBlockAds(enabled) }
    }

    /**
     * Called from `shouldInterceptRequest`, which WebView runs off the main
     * thread: no Compose state is touched here except thread-safe flows.
     */
    fun shouldBlock(url: String): Boolean {
        if (!blockAdsEnabled) return false
        if (!engine.shouldBlock(url)) return false
        _blocked.update { it + 1 }
        return true
    }

    /** Every request is offered to the sniffer; non-media is dropped immediately. */
    fun sniff(url: String, mimeType: String? = null) {
        val media = MediaSniffer.classify(url, mimeType) ?: return
        _sniffed.update { current ->
            if (current.any { it.url == media.url }) current else (current + media).takeLast(MAX_SNIFFED)
        }
    }

    fun clearSniffed() {
        _sniffed.value = emptyList()
    }

    /** A new document: the counters describe that page, not the previous one. */
    fun onPageStarted(url: String?) {
        _blocked.value = 0
        _sniffed.value = emptyList()
        if (url != null) _tabs.update { BrowserTabs.updateActive(it, url) }
    }

    fun onPageFinished(url: String, title: String?) {
        _tabs.update { BrowserTabs.updateActive(it, url, title?.takeIf { it.isNotBlank() }) }
    }

    fun openInActive(input: String): String? {
        val url = BrowserUrl.toLoadUrl(input) ?: return null
        _tabs.update { BrowserTabs.updateActive(it, url) }
        return url
    }

    fun newTab(input: String = BrowserUrl.HOME): String {
        val url = BrowserUrl.toLoadUrl(input) ?: BrowserUrl.HOME
        _tabs.update { BrowserTabs.open(it, url) }
        return url
    }

    fun closeTab(id: Long) {
        _tabs.update { BrowserTabs.close(it, id) }
    }

    fun selectTab(id: Long) {
        _tabs.update { BrowserTabs.select(it, id) }
    }

    /** Direct files go to the download queue; playlists belong to the player. */
    fun download(media: SniffedMedia) {
        if (media.kind == Kind.HLS || media.kind == Kind.DASH) {
            _message.value = BrowserMessage.DownloadFailed
            return
        }
        viewModelScope.launch {
            val id = runCatching { downloads.enqueue(media.url) }.getOrNull()
            _message.value = if (id != null) {
                BrowserMessage.DownloadStarted
            } else {
                BrowserMessage.DownloadFailed
            }
        }
    }

    /** WebView's DownloadListener: a link with Content-Disposition, not a media element. */
    fun downloadLink(url: String) {
        viewModelScope.launch {
            val id = runCatching { downloads.enqueue(url) }.getOrNull()
            _message.value = if (id != null) {
                BrowserMessage.DownloadStarted
            } else {
                BrowserMessage.DownloadFailed
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private companion object {
        const val MAX_SNIFFED = 24
    }
}
