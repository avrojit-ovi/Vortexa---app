package com.vortexa.feature.vault

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * The platform prompt, used without the androidx.biometric dependency.
 *
 * It is a convenience on top of the PIN, never a replacement: if the device has
 * no sensor, or the prompt errors, the PIN field is still right there.
 */
object BiometricUnlock {

    fun isAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasSensor(context)

    fun authenticate(
        context: Context,
        title: String,
        cancelLabel: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onFailure()
            return
        }
        prompt(context, title, cancelLabel, onSuccess, onFailure)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun prompt(
        context: Context,
        title: String,
        cancelLabel: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val biometric = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setNegativeButton(cancelLabel, executor) { _, _ -> onFailure() }
            .build()

        biometric.authenticate(
            CancellationSignal(),
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onFailure()
                }
            },
        )
    }

    private fun hasSensor(context: Context): Boolean {
        val manager = context.packageManager
        if (manager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return manager.hasSystemFeature(PackageManager.FEATURE_FACE) ||
            manager.hasSystemFeature(PackageManager.FEATURE_IRIS)
    }
}
