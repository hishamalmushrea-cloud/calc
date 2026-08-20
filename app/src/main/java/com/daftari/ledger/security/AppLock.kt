package com.daftari.ledger.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object AppLock {
    fun canBiometric(ctx: Context): Boolean {
        val m = BiometricManager.from(ctx)
        return m.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt(activity: FragmentActivity, onOk: () -> Unit, onFail: () -> Unit) {
        val exec = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, exec, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail()
            override fun onAuthenticationFailed() {}
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("فتح دفتري")
                .setNegativeButtonText("إلغاء")
                .build()
        )
    }
}
