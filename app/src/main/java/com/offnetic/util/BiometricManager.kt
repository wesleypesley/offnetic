package com.offnetic.util

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricManager @Inject constructor(
    private val context: Context
) {

    private val executor: Executor = ContextCompat.getMainExecutor(context)

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Offnetic",
        subtitle: String = "Authenticate to access your encrypted messages",
        description: String = "Use your biometric to unlock your identity keys",
        negativeButtonText: String = "Cancel"
    ): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setNegativeButtonText(negativeButtonText)
            .setConfirmationRequired(false)
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                deferred.complete(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                deferred.complete(false)
            }

            override fun onAuthenticationFailed() {
                deferred.complete(false)
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
        return deferred
    }

    fun canAuthenticate(): Boolean {
        val bm = androidx.biometric.BiometricManager.from(context)
        return bm.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }
}