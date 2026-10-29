package com.vortexa.feature.music

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.vortexa.core.ui.component.FeaturePlaceholder

/**
 * Scaffolded destination. It deliberately shows an honest "phase N" badge until
 * the feature is implemented, instead of a mock UI that pretends to work.
 */
@Composable
fun MusicScreen(modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        icon = painterResource(com.vortexa.core.ui.R.drawable.ic_tab_music),
        title = stringResource(R.string.feature_title),
        description = stringResource(R.string.feature_description),
        phaseLabel = stringResource(R.string.feature_phase),
        modifier = modifier,
    )
}
