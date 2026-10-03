// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
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
class PlaybackManager(
    private val providers: List<PlaybackProvider>,
    /** 5.2: pinned formats and still-valid URLs. Null disables both (plain resolve every time). */
    private val streamChoices: StreamChoiceStore? = null,
    /** Which of [providers] to use for a lookup, in order (Settings → Stream source). All of them by default. */
    private val chooseProviders: suspend (List<PlaybackProvider>) -> List<PlaybackProvider> = { it },
    /**
     * Called before a stream is returned whose format might not match the
     * song's cached bytes: [knownChange] true when it differs from the pinned
     * format (quality setting changed, or YouTube stopped offering it), false
     * when there was no pin yet (bytes cached before pinning existed). The
     * app drops that song's partial cached audio here so bytes of two
     * different files never mix.
     */
    private val onFormatChanged: (videoId: String, knownChange: Boolean) -> Unit = { _, _ -> },
) {

    /** The source that served the last stream lookup (provider id), for Settings to show. */
    val lastStreamSource: kotlinx.coroutines.flow.StateFlow<String?> get() = _lastStreamSource
    private val _lastStreamSource = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    private suspend fun ordered(): List<PlaybackProvider> =
        runCatching { chooseProviders(providers) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: providers

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
    suspend fun resolveStream(
        item: PlayableItem,
        quality: AudioQuality = AudioQuality.AUTO,
        /**
         * Keep whatever format is pinned even if it was chosen under another
         * quality setting. Used when refetching a URL for a song that may be
         * reading from the disk cache right now, where changing format isn't
         * safe.
         */
        keepAnyPinnedFormat: Boolean = false,
        /**
         * False for downloads: they save their own separate file at their own
         * quality, so they must neither reuse nor change the streaming choice.
         */
        useStreamChoices: Boolean = true,
    ): FallbackResult<ResolvedStream> {
        val store = streamChoices.takeIf { useStreamChoices }
        store?.freshUrl(item.id, quality, item.durationMs)?.let { reused ->
            Log.i(TAG, "Reusing still-valid stream for ${item.id} (itag ${reused.itag})")
            return FallbackResult.Success(reused, attempts = emptyList())
        }
        val pin = store?.pin(item.id)
        val preferredItag = if (keepAnyPinnedFormat) pin?.itag else store?.pinnedItag(item.id, quality)
        val candidates = ordered().filter { it.supports(item) }
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
                val stream = provider.getStream(item.id, quality, preferredItag)
                attempts += ProviderAttempt(provider.id, skipped = false, failure = null)
                _lastStreamSource.value = provider.id
                if (store != null) {
                    if (pin == null) {
                        runCatching { onFormatChanged(item.id, false) }
                    } else if (stream.itag != null && stream.itag != pin.itag) {
                        Log.i(TAG, "${item.id}: format ${pin.itag} -> ${stream.itag}, dropping its cached audio")
                        runCatching { onFormatChanged(item.id, true) }
                    }
                    store.remember(item.id, quality, stream)
                }
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

    /** The URL resolved for [videoId] failed in the player (expired/403): don't reuse it. */
    fun invalidateStream(videoId: String) {
        streamChoices?.invalidateUrl(videoId)
    }

    /** Same fallback algorithm, for metadata resolution. */
    suspend fun resolvePlayerInfo(item: PlayableItem): FallbackResult<ProviderPlayerInfo> {
        val candidates = ordered().filter { it.supports(item) }
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
