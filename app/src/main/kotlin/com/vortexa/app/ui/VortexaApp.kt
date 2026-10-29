package com.vortexa.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vortexa.app.MainActivity
import com.vortexa.app.R
import com.vortexa.app.navigation.Routes
import com.vortexa.app.navigation.TopLevelDestination
import com.vortexa.feature.browser.BrowserScreen
import com.vortexa.feature.downloader.DownloadsScreen
import com.vortexa.feature.music.MusicScreen
import com.vortexa.feature.settings.SettingsScreen
import com.vortexa.feature.vault.VaultScreen
import com.vortexa.feature.video.VideoScreen
import com.vortexa.feature.video.player.PlayerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VortexaApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // The player owns the whole window: no app bar, no tabs, no window insets.
    val immersive = currentRoute?.startsWith(Routes.PLAYER_PREFIX) == true

    Scaffold(
        modifier = modifier,
        topBar = {
            if (!immersive) {
                TopAppBar(
                    title = { Text(text = titleFor(currentRoute)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        bottomBar = {
            if (!immersive) {
                VortexaBottomBar(navController = navController, currentRoute = currentRoute)
            }
        },
        contentWindowInsets = if (immersive) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.DOWNLOADS.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(220),
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(220),
                )
            },
        ) {
            composable(TopLevelDestination.DOWNLOADS.route) { DownloadsScreen() }
            composable(TopLevelDestination.BROWSER.route) { BrowserScreen() }
            composable(TopLevelDestination.VIDEO.route) {
                VideoScreen(
                    onOpen = { path, title ->
                        navController.navigate(Routes.player(path, title))
                    },
                )
            }
            composable(TopLevelDestination.MUSIC.route) {
                MusicScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }
            composable(TopLevelDestination.MORE.route) {
                MoreScreen(
                    onOpenVault = { navController.navigate(Routes.VAULT) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.VAULT) { VaultScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(
                    navArgument("path") { type = NavType.StringType },
                    navArgument("title") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val context = LocalContext.current
                PlayerScreen(
                    path = entry.arguments?.getString("path").orEmpty(),
                    title = entry.arguments?.getString("title").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onEnterPip = { (context as? MainActivity)?.enterPip() },
                )
            }
        }
    }
}

@Composable
private fun VortexaBottomBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = null,
                    )
                },
                label = { Text(text = stringResource(destination.labelRes)) },
            )
        }
    }
}

@Composable
private fun titleFor(route: String?): String = when (route) {
    Routes.VAULT -> stringResource(R.string.destination_vault)
    Routes.SETTINGS -> stringResource(R.string.destination_settings)
    else -> {
        val destination = TopLevelDestination.entries.firstOrNull { it.route == route }
        if (destination != null) stringResource(destination.labelRes) else stringResource(R.string.app_name)
    }
}
