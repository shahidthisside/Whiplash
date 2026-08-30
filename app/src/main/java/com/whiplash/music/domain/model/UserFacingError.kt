package com.whiplash.music.domain.model

import com.whiplash.music.playback.provider.ProviderFailure
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps any failure (a [ProviderFailure] or a raw network exception that
 * hasn't gone through that mapping yet) to a short, honest, non-technical
 * message a regular user can actually act on — never a raw exception
 * string like `Unable to resolve host "music.youtube.com": No address
 * associated with hostname`, which is meaningless to anyone who isn't
 * reading a stack trace.
 *
 * Real bug this fixes: [MainActivity]'s playback-error Toast already had
 * this exact mapping for its one case, but three other real error-surfacing
 * paths (Search's error state, Album detail, Artist detail) each caught a
 * raw [Exception] and displayed `e.message` directly — so a genuine
 * connectivity problem showed the underlying `UnknownHostException` text
 * verbatim instead of a plain "No internet connection" a user would
 * recognize from every other app. This single shared function is now the
 * one place that decides the user-facing wording, so all four surfaces
 * (and any future one) stay consistent automatically.
 */
fun Throwable.toUserFacingMessage(fallback: String = "Something went wrong"): String {
    val isNetworkIssue = when (this) {
        is ProviderFailure.NetworkFailure -> true
        is UnknownHostException,
        is ConnectException,
        is SocketTimeoutException,
        is NoRouteToHostException,
        -> true
        else -> false
    }
    if (isNetworkIssue) return "No internet connection. Check your connection and try again."

    return when (this) {
        is ProviderFailure.ContentUnavailable -> "This content isn't available."
        is ProviderFailure.AuthenticationRequired -> "This content requires sign-in and isn't supported."
        is ProviderFailure.RateLimited -> "Too many requests right now. Please try again in a moment."
        is ProviderFailure.StreamExpired,
        is ProviderFailure.ProviderParserFailure,
        is ProviderFailure.UnknownPlaybackFailure,
        -> fallback
        else -> fallback
    }
}
