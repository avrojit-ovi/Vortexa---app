package com.vortexa.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortexa.app.ui.VortexaApp
import com.vortexa.core.ui.theme.VortexaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* progress is also visible in-app */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askForNotificationPermission()
        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            VortexaTheme(
                themeMode = preferences.themeMode,
                dynamicColor = preferences.dynamicColor,
            ) {
                VortexaApp()
            }
        }
    }

    /**
     * Enters picture-in-picture for the player. Silently does nothing on a
     * device or window that cannot host it, so the player button never crashes.
     */
    fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        runCatching { enterPictureInPictureMode(params) }
    }

    /** Android 13+ hides foreground-service notifications until this is granted. */
    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
