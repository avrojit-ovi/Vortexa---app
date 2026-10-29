package com.vortexa.app.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.vortexa.app.R

/**
 * The five bottom-bar destinations. Secondary screens (vault, settings) are
 * pushed on top of "More" instead of taking a tab slot.
 */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    DOWNLOADS("downloads", R.string.tab_downloads, com.vortexa.core.ui.R.drawable.ic_tab_downloads),
    BROWSER("browser", R.string.tab_browser, com.vortexa.core.ui.R.drawable.ic_tab_browser),
    VIDEO("video", R.string.tab_video, com.vortexa.core.ui.R.drawable.ic_tab_video),
    MUSIC("music", R.string.tab_music, com.vortexa.core.ui.R.drawable.ic_tab_music),
    MORE("more", R.string.tab_more, com.vortexa.core.ui.R.drawable.ic_tab_more),
}

object Routes {
    const val VAULT = "vault"
    const val SETTINGS = "settings"
}
