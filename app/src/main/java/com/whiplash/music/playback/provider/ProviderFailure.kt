package com.whiplash.music.playback.provider

/**
 * Error taxonomy for playback provider failures (CLAUDE.md section 8).
 *
 * The [isFailoverEligible] flag on each subtype is the single source of
 * truth [com.whiplash.music.playback.provider.PlaybackManager] uses to
 * decide whether to try the next provider or stop and surface the error.
 * Content that is genuinely unavailable (deleted/private/region-locked)
 * will fail identically on every provider, so retrying elsewhere would
 * only add latency without a chance of success.
 */
sealed class ProviderFailure(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Whether trying another provider is reasonably likely to succeed. */
    abstract val isFailoverEligible: Boolean

    /** The video/track is deleted, private, or otherwise permanently gone. */
    class ContentUnavailable(
        message: String = "Content is unavailable",
        cause: Throwable? = null,
    ) : ProviderFailure(message, cause) {
        override val isFailoverEligible: Boolean = false
    }

    /** The provider requires sign-in/age-verification/etc to resolve this content. */
    class AuthenticationRequired(
        message: String = "Authentication required",
        cause: Throwable? = null,
    ) : ProviderFailure(message, cause) {
        override val isFailoverEligible: Boolean = false
    }

    /** Connectivity problem reaching the provider's upstream (timeouts, DNS, etc). */
    class NetworkFailure(
        message: String = "Network failure",
        cause: Throwable? = null,
    ) : ProviderFailure(message, cause) {
        override val isFailoverEligible: Boolean = true
    }

    /** A previously resolved stream URL is no longer valid; a fresh resolve may succeed. */
    class StreamExpired(
        message: String = "Stream expired",
        cause: Throwable? = null,
    ) : ProviderFailure(message, cause) {
        override val isFailoverEligible: Boolean = true
    }

    /** The provider is being throttled/rate-limited by its upstream. */
    class RateLimited(
        message: String = "Rate limited",
        cause: Throwable? = null,
    ) : ProviderFailure(message, cause) {
        override val isFailoverEligible: Boolean = true
    }

    /** The provider's extraction/parsing logic failed (e.g. upstream response shape changed). */
    class ProviderParserFailure(
        message: String = "Provider parser failure",
        cause: Throwable? = null,
    ) : ProviderFailure(message, cause) {
        override val isFailoverEligible: Boolean = true
    }

    /** Catch-all for provider-specific failures that don't fit a more specific category. */
    class UnknownPlaybackFailure(
        message: String = "Unknown playback failure",
        cause: Throwable? = null,
    ) : ProviderFailure(message, cause) {
        override val isFailoverEligible: Boolean = true
    }
}
