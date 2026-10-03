// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.lyrics

import com.whiplash.music.domain.model.LyricsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** A source of real lyrics. Implementations must never fabricate text. */
interface LyricsProvider {
    /** Stable id, stored in settings and the cache. */
    val id: String

    /** Name shown to the user. */
    val displayName: String

    /** Whether this source can return time-synced lyrics. */
    val supportsSync: Boolean

    suspend fun getLyrics(title: String, artist: String, durationMs: Long): LyricsResult
}

/** Which provider(s) the user wants lyrics from. [AUTO] tries each provider in order. */
enum class LyricsSourcePreference(val label: String) {
    AUTO("Automatic"),
    LRCLIB("LRCLIB"),
    LYRICS_OVH("lyrics.ovh"),
}

/** Health of one provider, derived from its recent lookups. */
data class ProviderHealth(
    val state: State = State.UNKNOWN,
    val consecutiveFailures: Int = 0,
    val cooldownUntilMs: Long = 0L,
) {
    enum class State { UNKNOWN, WORKING, FAILING }
}

/** A lookup result plus which provider produced it (null when none succeeded). */
data class LyricsLookup(val result: LyricsResult, val providerId: String?)

/**
 * Runs lookups against [providers] in order and tracks each provider's health.
 *
 * - With [LyricsSourcePreference.AUTO], the first provider to find lyrics wins.
 *   Synced lyrics are preferred: a plain result is kept while later providers are
 *   tried only if they can sync.
 * - A provider that errors [FAILURE_THRESHOLD] times in a row is skipped in AUTO
 *   for [COOLDOWN_MS]. If every provider is cooling down, all are tried anyway.
 * - When a specific provider is chosen, only that provider is asked, whatever its health.
 * - If nothing is found: any error → [LyricsResult.Error] (so it is not cached and
 *   gets retried); otherwise [LyricsResult.Unavailable].
 */
class LyricsProviderChain(
    val providers: List<LyricsProvider>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _health = MutableStateFlow(providers.associate { it.id to ProviderHealth() })
    val health: StateFlow<Map<String, ProviderHealth>> = _health

    suspend fun lookup(
        preference: LyricsSourcePreference,
        title: String,
        artist: String,
        durationMs: Long,
    ): LyricsLookup {
        val candidates = candidatesFor(preference)
        var plain: LyricsLookup? = null
        var sawError: LyricsResult.Error? = null

        for (provider in candidates) {
            // Nothing later can improve on a plain result unless it can sync.
            if (plain != null && !provider.supportsSync) continue
            val result = runCatching { provider.getLyrics(title, artist, durationMs) }
                .getOrElse { LyricsResult.Error(it.message ?: "Lyrics lookup failed") }
            record(provider.id, result)
            when (result) {
                is LyricsResult.Synced -> return LyricsLookup(result, provider.id)
                is LyricsResult.Plain -> if (plain == null) plain = LyricsLookup(result, provider.id)
                is LyricsResult.Error -> if (sawError == null) sawError = result
                LyricsResult.Unavailable -> Unit
            }
        }
        plain?.let { return it }
        return LyricsLookup(sawError ?: LyricsResult.Unavailable, null)
    }

    private fun candidatesFor(preference: LyricsSourcePreference): List<LyricsProvider> {
        if (preference != LyricsSourcePreference.AUTO) {
            val chosen = providers.firstOrNull { it.id == preference.name }
            return listOfNotNull(chosen ?: providers.firstOrNull())
        }
        val now = clock()
        val healthy = providers.filter { (_health.value[it.id]?.cooldownUntilMs ?: 0L) <= now }
        return healthy.ifEmpty { providers }
    }

    private fun record(id: String, result: LyricsResult) {
        _health.update { map ->
            val old = map[id] ?: ProviderHealth()
            val new = if (result is LyricsResult.Error) {
                val failures = old.consecutiveFailures + 1
                ProviderHealth(
                    state = ProviderHealth.State.FAILING,
                    consecutiveFailures = failures,
                    cooldownUntilMs = if (failures >= FAILURE_THRESHOLD) clock() + COOLDOWN_MS else 0L,
                )
            } else {
                ProviderHealth(state = ProviderHealth.State.WORKING)
            }
            map + (id to new)
        }
    }

    companion object {
        const val FAILURE_THRESHOLD = 3
        const val COOLDOWN_MS = 5L * 60 * 1000
    }
}
