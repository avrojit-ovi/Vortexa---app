package com.vortexa.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.vortexa.core.common.preferences.ThemeMode

/**
 * Extra brand tokens that Material 3 does not model.
 */
data class VortexaExtraColors(
    val success: Color,
    val warning: Color,
    val downloadAccent: Color,
) {
    companion object {
        val Light = VortexaExtraColors(
            success = Color(0xFF1B7F3B),
            warning = Color(0xFF9A6400),
            downloadAccent = LightPrimary,
        )
        val Dark = VortexaExtraColors(
            success = Color(0xFF6EDB8F),
            warning = Color(0xFFFFC46B),
            downloadAccent = DarkPrimary,
        )
    }
}

val LocalVortexaExtraColors = staticCompositionLocalOf { VortexaExtraColors.Light }

@Composable
fun VortexaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }

    val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current

    val colorScheme = when {
        supportsDynamic -> {
            if (dark) {
                if (themeMode == ThemeMode.AMOLED) {
                    dynamicDarkColorScheme(context).toAmoled()
                } else {
                    dynamicDarkColorScheme(context)
                }
            } else {
                dynamicLightColorScheme(context)
            }
        }

        themeMode == ThemeMode.AMOLED -> AmoledColorScheme
        dark -> DarkColorScheme
        else -> LightColorScheme
    }

    val extraColors = if (dark) VortexaExtraColors.Dark else VortexaExtraColors.Light

    CompositionLocalProvider(LocalVortexaExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VortexaTypography,
            content = content,
        )
    }
}

/** Turns a wallpaper-derived dark scheme into a true-black one. */
private fun androidx.compose.material3.ColorScheme.toAmoled() = copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF101013),
    surfaceContainerHigh = Color(0xFF17171B),
    surfaceContainerHighest = Color(0xFF1E1E23),
)
