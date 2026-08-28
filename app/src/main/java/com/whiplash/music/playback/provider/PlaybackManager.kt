package com.whiplash.music.playback.provider

import android.util.Log
import com.whiplash.music.domain.model.AudioQuality
import com.whiplash.music.domain.model.PlayableItem

/**
 * Orchestrates automatic playback fallback across an ordered list of
 * [PlaybackProvider]s (CLAUDE.md section 8):
 *
 * ```
 * Try preferred provider -> success? -> PLAY
 *                         -> failure (failover-eligible)? -> next provider
 *                         -> failure (not eligible)? -> stop, surface error
 * ```
 *
 * [providers] order is the priority order (Provider A first, Provider B
 * second, etc). Adding a new provider later only means appending it to
 * this list — no call-site changes (section 7: "architecture must make
 * future providers easy to add").
 */
class PlaybackManager(private val providers: List<PlaybackProvider>) {

    init {
        require(providers.isNotEmpty()) { "PlaybackManager requires at least one provider" }
    }

    /**
     * Resolves a playable stream for [item], trying providers in priority
     * order. A provider currently in health cooldown is skipped proactively
     * (zero added latency, per the user's "no delay" requirement) rather
     * than attempted and left to fail. Only [ProviderFailure.isFailoverEligible]
     * failures advance to the next provider; a non-eligible failure (e.g.
     * deleted/private content) stops immediately since every provider would
     * fail identically.
     */
    suspend fun resolveStream(item: PlayableItem, quality: AudioQuality = AudioQuality.AUTO): FallbackResult<ResolvedStream> {
        val candidates = providers.filter { it.supports(item) }
        if (candidates.isEmpty()) {
            return FallbackResult.Failure(
                ProviderFailure.UnknownPlaybackFailure("No provider supports ${item.source} items"),
                attempts = emptyList(),
            )
        }

        val attempts = mutableListOf<ProviderAttempt>()

        for (provider in candidates) {
            if (isProactivelySkippable(provider)) {
                attempts += ProviderAttempt(provider.id, skipped = true, failure = null)
                Log.i(TAG, "Skipping ${provider.id} (in cooldown), trying next provider")
                continue
            }

            try {
                val stream = provider.getStream(item.id, quality)
                attempts += ProviderAttempt(provider.id, skipped = false, failure = null)
                return FallbackResult.Success(stream, attempts)
            } catch (failure: ProviderFailure) {
                attempts += ProviderAttempt(provider.id, skipped = false, failure = failure)
                if (!failure.isFailoverEligible) {
                    Log.w(TAG, "${provider.id} failed with non-eligible failure, stopping: ${failure.message}")
                    return FallbackResult.Failure(failure, attempts)
                }
                Log.w(TAG, "${provider.id} failed (failover-eligible), trying next provider: ${failure.message}")
            }
        }

        // Every candidate either was skipped or failed with an
        // eligible-for-failover error; report the last real failure seen
        // (or a generic one if every candidate was skipped).
        val lastFailure = attempts.lastOrNull { it.failure != null }?.failure
            ?: ProviderFailure.UnknownPlaybackFailure("All providers unavailable for ${item.id}")
        return FallbackResult.Failure(lastFailure, attempts)
    }

    /** Same fallback algorithm, for metadata resolution. */
    suspend fun resolvePlayerInfo(item: PlayableItem): FallbackResult<ProviderPlayerInfo> {
        val candidates = providers.filter { it.supports(item) }
        if (candidates.isEmpty()) {
            return FallbackResult.Failure(
                ProviderFailure.UnknownPlaybackFailure("No provider supports ${item.source} items"),
                attempts = emptyList(),
            )
        }

        val attempts = mutableListOf<ProviderAttempt>()

        for (provider in candidates) {
            if (isProactivelySkippable(provider)) {
                attempts += ProviderAttempt(provider.id, skipped = true, failure = null)
                continue
            }

            try {
                val info = provider.getPlayerInfo(item.id)
                attempts += ProviderAttempt(provider.id, skipped = false, failure = null)
                return FallbackResult.Success(info, attempts)
            } catch (failure: ProviderFailure) {
                attempts += ProviderAttempt(provider.id, skipped = false, failure = failure)
                if (!failure.isFailoverEligible) {
                    return FallbackResult.Failure(failure, attempts)
                }
            }
        }

        val lastFailure = attempts.lastOrNull { it.failure != null }?.failure
            ?: ProviderFailure.UnknownPlaybackFailure("All providers unavailable for ${item.id}")
        return FallbackResult.Failure(lastFailure, attempts)
    }

    private suspend fun isProactivelySkippable(provider: PlaybackProvider): Boolean {
        // providerStatus() itself checks cooldown expiry (section 9 periodic
        // recovery), so a provider that has cooled down is naturally let
        // back into rotation here without special-casing.
        return provider.providerStatus() == com.whiplash.music.data.local.entity.ProviderStatus.TEMPORARILY_UNAVAILABLE
    }

    private companion object {
        const val TAG = "PlaybackManager"
    }
}

/** Outcome of a fallback attempt sequence, including which providers were tried/skipped. */
sealed class FallbackResult<out T> {
    data class Success<T>(val value: T, val attempts: List<ProviderAttempt>) : FallbackResult<T>()
    data class Failure(val failure: ProviderFailure, val attempts: List<ProviderAttempt>) : FallbackResult<Nothing>()
}

/** Record of a single provider being tried (or proactively skipped) during fallback. */
data class ProviderAttempt(
    val providerId: String,
    val skipped: Boolean,
    val failure: ProviderFailure?,
)
