package com.daftari.ledger.backup

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface DriveTokenResult {
    data class Granted(val accessToken: String) : DriveTokenResult
    data object UserInteractionRequired : DriveTokenResult
}

object GoogleDriveAuthorization {
    suspend fun token(context: Context): DriveTokenResult = suspendCancellableCoroutine { continuation ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GOOGLE_DRIVE_APPDATA_SCOPE), Scope(GOOGLE_OPENID_SCOPE), Scope(GOOGLE_EMAIL_SCOPE)))
            .build()
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { result ->
                if (!continuation.isActive) return@addOnSuccessListener
                if (result.hasResolution()) continuation.resume(DriveTokenResult.UserInteractionRequired)
                else {
                    val token = result.accessToken
                    if (token.isNullOrBlank()) continuation.resumeWithException(IllegalStateException("Google authorization returned no token"))
                    else continuation.resume(DriveTokenResult.Granted(token))
                }
            }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }
}
