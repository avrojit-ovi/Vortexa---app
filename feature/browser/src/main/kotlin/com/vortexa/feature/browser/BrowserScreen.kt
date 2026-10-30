@file:OptIn(ExperimentalMaterial3Api::class)

package com.vortexa.feature.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortexa.core.database.entity.BrowserEntryEntity
import com.vortexa.feature.browser.adblock.Kind
import com.vortexa.feature.browser.adblock.SniffedMedia
import java.io.ByteArrayInputStream

/**
 * A real browser: several tabs, one live WebView, and the ad blocker and media
 * sniffer wired into the request path.
 *
 * Tabs keep their own history through `WebView.saveState` — one WebView instead
 * of one per tab, because a background WebView still holds a decoder and a
 * renderer, and this runs on phones.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    onOpenEmbed: (url: String, title: String) -> Unit = { _, _ -> },
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val tabsState by viewModel.tabs.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedCount.collectAsStateWithLifecycle()
    val sniffed by viewModel.sniffed.collectAsStateWithLifecycle()
    val blockAds by viewModel.blockAds.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val incognito by viewModel.incognito.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var showLibrary by remember { mutableStateOf<LibraryTab?>(null) }

    var webView by remember { mutableStateOf<WebView?>(null) }
    val savedStates = remember { mutableMapOf<Long, Bundle>() }
    var previousTabId by remember { mutableLongStateOf(tabsState.activeId) }

    var address by rememberSaveable { mutableStateOf(BrowserUrl.display(tabsState.active.url)) }
    var urlFocused by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var showMedia by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    fun syncHistory(view: WebView) {
        canGoBack = view.canGoBack()
        canGoForward = view.canGoForward()
    }

    fun load(text: String) {
        val url = viewModel.openInActive(text) ?: return
        urlFocused = false
        address = BrowserUrl.display(url)
        webView?.loadUrl(url)
    }

    LaunchedEffect(tabsState.active.url, urlFocused) {
        if (!urlFocused) address = BrowserUrl.display(tabsState.active.url)
    }

    val startedText = stringResource(R.string.browser_download_started)
    val failedText = stringResource(R.string.browser_download_failed)
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        val text = if (current == BrowserMessage.DownloadStarted) startedText else failedText
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    // Tab switch: park the outgoing tab's history, restore the incoming one.
    LaunchedEffect(tabsState.activeId, webView) {
        val view = webView ?: return@LaunchedEffect
        val target = tabsState.activeId
        if (target == previousTabId) return@LaunchedEffect
        if (tabsState.tabs.none { it.id == target }) return@LaunchedEffect

        val park = Bundle()
        view.saveState(park)
        savedStates[previousTabId] = park
        previousTabId = target
        savedStates.keys.retainAll(tabsState.tabs.map { it.id }.toSet())

        progress = 0
        val restore = savedStates[target]
        if (restore != null) {
            view.restoreState(restore)
        } else {
            view.loadUrl(tabsState.active.url)
        }
        syncHistory(view)
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let { view ->
                view.stopLoading()
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
            webView = null
        }
    }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    val client = remember(viewModel) {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val url = request?.url?.toString().orEmpty()
                if (!url.startsWith("http")) return true
                return viewModel.shouldBlock(url)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url?.toString().orEmpty()
                if (!url.startsWith("http")) return null
                if (viewModel.shouldBlock(url)) return blockedResponse()
                viewModel.sniff(url)
                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loading = true
                viewModel.onPageStarted(url)
                if (url != null && !urlFocused) address = BrowserUrl.display(url)
                view?.let { syncHistory(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loading = false
                if (url != null) viewModel.onPageFinished(url, view?.title)
                view?.let { syncHistory(it) }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                view?.let { syncHistory(it) }
            }
        }
    }

    val chrome = remember(viewModel) {
        object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = newProgress
                if (newProgress >= 100) loading = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AddressBar(
            address = address,
            onAddressChange = { address = it },
            onFocusChange = { urlFocused = it },
            onGo = { load(address) },
            onBack = { webView?.goBack() },
            onForward = { webView?.goForward() },
            onReload = { webView?.reload() },
            onStop = { webView?.stopLoading().also { loading = false } },
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            loading = loading,
            blockAds = blockAds,
            blocked = blocked,
            onToggleBlockAds = { viewModel.setBlockAds(!blockAds) },
            tabCount = tabsState.tabs.size,
            onTabs = { showTabs = true },
            incognito = incognito,
            bookmarked = bookmarks.any { it.url == tabsState.active.url },
            onToggleBookmark = {
                viewModel.toggleBookmark(tabsState.active.url, tabsState.active.title)
            },
            onShowBookmarks = { showLibrary = LibraryTab.BOOKMARKS },
            onShowHistory = { showLibrary = LibraryTab.HISTORY },
            onToggleIncognito = { viewModel.setIncognito(!incognito) },
        )

        if (loading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.setSupportMultipleWindows(false)
                        settings.userAgentString = settings.userAgentString + " Vortexa/1.0"
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                        webViewClient = client
                        webChromeClient = chrome
                        setDownloadListener { url, _, _, mimeType, _ ->
                            if (url != null) {
                                viewModel.sniff(url, mimeType)
                                viewModel.downloadLink(url)
                            }
                        }
                        loadUrl(tabsState.active.url)
                        webView = this
                    }
                },
            )

            if (sniffed.isNotEmpty()) {
                MediaBar(
                    count = sniffed.size,
                    onOpen = { showMedia = true },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (showTabs) {
        ModalBottomSheet(onDismissRequest = { showTabs = false }) {
            TabsSheet(
                state = tabsState,
                onSelect = { id ->
                    viewModel.selectTab(id)
                    showTabs = false
                },
                onClose = viewModel::closeTab,
                onNewTab = {
                    val url = viewModel.newTab()
                    showTabs = false
                    address = BrowserUrl.display(url)
                    webView?.loadUrl(url)
                },
            )
        }
    }

    showLibrary?.let { tab ->
        ModalBottomSheet(onDismissRequest = { showLibrary = null }) {
            LibrarySheet(
                tab = tab,
                bookmarks = bookmarks,
                history = history,
                onTabChange = { showLibrary = it },
                onOpen = { url ->
                    showLibrary = null
                    address = BrowserUrl.display(url)
                    viewModel.openInActive(url)
                    webView?.loadUrl(url)
                },
                onRemoveBookmark = viewModel::removeBookmark,
                onRemoveHistory = viewModel::removeHistory,
                onClearHistory = viewModel::clearHistory,
            )
        }
    }

    if (showMedia) {
        ModalBottomSheet(onDismissRequest = { showMedia = false }) {
            MediaSheet(
                media = sniffed,
                onDownload = { item ->
                    viewModel.download(item)
                    showMedia = false
                },
                onOpen = { item ->
                    showMedia = false
                    onOpenEmbed(item.url, item.suggestedName)
                },
            )
        }
    }
}

@Composable
private fun AddressBar(
    address: String,
    onAddressChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onGo: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    loading: Boolean,
    blockAds: Boolean,
    blocked: Int,
    onToggleBlockAds: () -> Unit,
    tabCount: Int,
    onTabs: () -> Unit,
    incognito: Boolean,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onShowBookmarks: () -> Unit,
    onShowHistory: () -> Unit,
    onToggleIncognito: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.browser_menu)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(
                painter = painterResource(R.drawable.ic_browser_back),
                contentDescription = stringResource(R.string.browser_back),
            )
        }

        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocusChange(it.isFocused) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = { Text(text = stringResource(R.string.browser_url_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            trailingIcon = {
                IconButton(
                    onClick = if (loading) onStop else onReload,
                    enabled = loading || address.isNotBlank(),
                ) {
                    Icon(
                        painter = painterResource(
                            if (loading) R.drawable.ic_browser_close else R.drawable.ic_browser_refresh,
                        ),
                        contentDescription = stringResource(
                            if (loading) R.string.browser_stop else R.string.browser_reload,
                        ),
                    )
                }
            },
        )

        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(
                painter = painterResource(R.drawable.ic_browser_forward),
                contentDescription = stringResource(R.string.browser_forward),
            )
        }

        BadgedBox(
            badge = {
                if (blocked > 0) {
                    Badge { Text(text = blocked.toString()) }
                }
            },
        ) {
            IconButton(onClick = onToggleBlockAds) {
                Icon(
                    painter = painterResource(R.drawable.ic_browser_shield),
                    contentDescription = stringResource(
                        if (blockAds) R.string.browser_block_ads_on else R.string.browser_block_ads_off,
                    ),
                    tint = if (blockAds) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        IconButton(onClick = onTabs) {
            Icon(
                painter = painterResource(R.drawable.ic_browser_tabs),
                contentDescription = pluralStringResource(
                    R.plurals.browser_tab_count,
                    tabCount,
                    tabCount,
                ),
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Text(
                    text = "\u22EE",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics {
                        contentDescription = menuLabel
                    },
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (bookmarked) R.string.browser_remove_bookmark else R.string.browser_add_bookmark,
                            ),
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onToggleBookmark()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_bookmarks)) },
                    onClick = {
                        menuOpen = false
                        onShowBookmarks()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_history)) },
                    onClick = {
                        menuOpen = false
                        onShowHistory()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (incognito) R.string.browser_incognito_off else R.string.browser_incognito_on,
                            ),
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onToggleIncognito()
                    },
                )
            }
        }
    }
}

private enum class LibraryTab { BOOKMARKS, HISTORY }

@Composable
private fun LibrarySheet(
    tab: LibraryTab,
    bookmarks: List<BrowserEntryEntity>,
    history: List<BrowserEntryEntity>,
    onTabChange: (LibraryTab) -> Unit,
    onOpen: (String) -> Unit,
    onRemoveBookmark: (Long) -> Unit,
    onRemoveHistory: (Long) -> Unit,
    onClearHistory: () -> Unit,
) {
    val items = if (tab == LibraryTab.BOOKMARKS) bookmarks else history
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onTabChange(LibraryTab.BOOKMARKS) }) {
                Text(
                    text = stringResource(R.string.browser_bookmarks),
                    color = if (tab == LibraryTab.BOOKMARKS) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            TextButton(onClick = { onTabChange(LibraryTab.HISTORY) }) {
                Text(
                    text = stringResource(R.string.browser_history),
                    color = if (tab == LibraryTab.HISTORY) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            if (tab == LibraryTab.HISTORY && history.isNotEmpty()) {
                TextButton(onClick = onClearHistory) {
                    Text(text = stringResource(R.string.browser_clear_history))
                }
            }
        }

        if (items.isEmpty()) {
            Text(
                text = stringResource(
                    if (tab == LibraryTab.BOOKMARKS) R.string.browser_no_bookmarks else R.string.browser_no_history,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items, key = { it.id }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(entry.url) }
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title.ifBlank { BrowserUrl.display(entry.url) },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = BrowserUrl.display(entry.url),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (tab == LibraryTab.BOOKMARKS) onRemoveBookmark(entry.id) else onRemoveHistory(entry.id)
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_browser_close),
                            contentDescription = stringResource(R.string.browser_remove),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MediaBar(
    count: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(12.dp)
            .clickable(onClick = onOpen),
        tonalElevation = 3.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_browser_download),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = pluralStringResource(R.plurals.browser_media_found, count, count),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun TabsSheet(
    state: BrowserTabsState,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewTab: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.browser_tabs),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onNewTab) {
                Text(text = stringResource(R.string.browser_new_tab))
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.tabs, key = { it.id }) { tab ->
                val active = tab.id == state.activeId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(tab.id) }
                        .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tab.title.ifBlank { BrowserUrl.display(tab.url) },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            text = BrowserUrl.display(tab.url),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onClose(tab.id) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_browser_close),
                            contentDescription = stringResource(R.string.browser_close_tab),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MediaSheet(
    media: List<SniffedMedia>,
    onDownload: (SniffedMedia) -> Unit,
    onOpen: (SniffedMedia) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.browser_media_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = stringResource(R.string.browser_media_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(media, key = { it.url }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.suggestedName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = kindLabel(item.kind),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.kind == Kind.HLS || item.kind == Kind.DASH) {
                        TextButton(onClick = { onOpen(item) }) {
                            Text(text = stringResource(R.string.browser_open))
                        }
                    } else {
                        TextButton(onClick = { onDownload(item) }) {
                            Text(text = stringResource(R.string.browser_download))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun kindLabel(kind: Kind): String = when (kind) {
    Kind.VIDEO -> stringResource(R.string.browser_kind_video)
    Kind.AUDIO -> stringResource(R.string.browser_kind_audio)
    Kind.HLS -> stringResource(R.string.browser_kind_hls)
    Kind.DASH -> stringResource(R.string.browser_kind_dash)
    Kind.FILE -> stringResource(R.string.browser_kind_file)
}

/** An empty 200 keeps the page's own error handling quiet. */
private fun blockedResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
