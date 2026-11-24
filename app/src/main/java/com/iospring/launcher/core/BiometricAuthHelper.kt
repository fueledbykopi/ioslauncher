package com.iospring.launcher.core

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

/**
 * Zero-bloat native Android BiometricPrompt helper for Hidden Apps & App Lock.
 * Supports Fingerprint, Face Unlock, and Device PIN/Pattern/Password on OPPO Android 15.
 */
object BiometricAuthHelper {

    fun isDeviceSecured(context: Context): Boolean {
        val km = context.getSystemService(KeyguardManager::class.java)
        return km?.isDeviceSecure == true
    }

    fun authenticate(
        activity: Activity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: (reason: String) -> Unit
    ) {
        if (!isDeviceSecured(activity)) {
            // If user has no screen lock set on device, allow access cleanly
            onSuccess()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val builder = BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setDeviceCredentialAllowed(true)
                }

                val prompt = builder.build()
                val cancelSignal = CancellationSignal()
                val executor = ContextCompat.getMainExecutor(activity)

                prompt.authenticate(
                    cancelSignal,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            onFailure(errString?.toString() ?: "Authentication canceled")
                        }

                        override fun onAuthenticationFailed() {
                            // User can retry automatically inside system biometric sheet
                        }
                    }
                )
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "Biometric unavailable")
            }
        } else {
            // Android 8.0 / 8.1 fallback
            onSuccess()
        }
    }
}
