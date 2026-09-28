package com.whiplash.music.data.sync

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google sign-in for sync, via Play services' AuthorizationClient. It asks
 * for the hidden app folder in Drive (drive.appdata, a non-sensitive scope)
 * plus basic profile. No password or Google credential ever reaches the app:
 * it only receives a short-lived access token, which is never stored.
 */
class GoogleDriveAuth(context: Context) {

    private val appContext = context.applicationContext
    private val client by lazy { Identity.getAuthorizationClient(appContext) }

    sealed interface Outcome {
        data class Granted(val token: String, val email: String?, val name: String?, val photoUrl: String?) : Outcome
        /** Google needs to show its account picker or consent screen first. */
        data class NeedsUi(val intent: PendingIntent) : Outcome
    }

    /** Play services present and working (missing on some Huawei and de-Googled phones). */
    fun isAvailable(): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    /** Requests a token for [email], or lets the user pick an account when null. */
    suspend fun authorize(email: String?): Outcome {
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(SCOPES)
            .apply { if (email != null) setAccount(Account(email, ACCOUNT_TYPE)) }
            .build()
        return client.authorize(request).await().toOutcome()
    }

    /** The result of the Google screen launched for [Outcome.NeedsUi]. */
    fun resultFromIntent(data: Intent?): Outcome = client.getAuthorizationResultFromIntent(data).toOutcome()

    /** Drops a cached token so the next [authorize] fetches a fresh one. */
    suspend fun clearToken(token: String) {
        runCatching { client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    }

    /** Withdraws Whiplash's access for [email], so the next sign-in asks which account again. */
    suspend fun revoke(email: String) {
        runCatching {
            client.revokeAccess(
                RevokeAccessRequest.builder().setAccount(Account(email, ACCOUNT_TYPE)).setScopes(SCOPES).build(),
            ).await()
        }
    }

    @Suppress("DEPRECATION") // toGoogleSignInAccount is the only way this API exposes profile fields.
    private fun AuthorizationResult.toOutcome(): Outcome {
        if (hasResolution()) {
            return Outcome.NeedsUi(pendingIntent ?: throw IllegalStateException("No sign-in screen available"))
        }
        val token = accessToken ?: throw IllegalStateException("Google returned no access token")
        val account = runCatching { toGoogleSignInAccount() }.getOrNull()
        return Outcome.Granted(token, account?.email, account?.displayName, account?.photoUrl?.toString())
    }

    companion object {
        private const val ACCOUNT_TYPE = "com.google"
        val SCOPES = listOf(
            Scope("https://www.googleapis.com/auth/drive.appdata"),
            Scope("email"),
            Scope("profile"),
        )

        /** A short, user-facing reason for a failed sign-in. */
        fun describe(error: Throwable): String {
            val code = (error as? ApiException)?.statusCode
            return when (code) {
                CommonStatusCodes.CANCELED -> "Sign-in cancelled"
                CommonStatusCodes.NETWORK_ERROR -> "No internet connection"
                // Package name or signing key not registered in Google Cloud.
                CommonStatusCodes.DEVELOPER_ERROR -> "Google sign-in isn't set up for this app yet"
                else -> "Couldn't sign in"
            }
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
