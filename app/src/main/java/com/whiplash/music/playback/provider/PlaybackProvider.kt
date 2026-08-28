package com.whiplash.music.playback.provider

import com.whiplash.music.data.local.entity.ProviderStatus
import com.whiplash.music.domain.model.AudioQuality
import com.whiplash.music.domain.model.PlayableItem

/**
 * Stable internal abstraction over an unofficial YouTube/YouTube Music
 * extraction backend (CLAUDE.md section 7). [PlaybackManager] depends only
 * on this interface, never on a specific extraction library, so providers
 * can be added, reordered, or removed without touching call sites.
 *
 * Implementations must throw [ProviderFailure] subtypes (never raw
 * exceptions) so [PlaybackManager] can make a correct failover decision.
 */
interface PlaybackProvider {

    /** Stable identifier used for health tracking/persistence, e.g. "newpipe". */
    val id: String

    /** Human-readable name for logs/diagnostics. */
    val displayName: String

    /** Whether this provider is able to handle the given item at all. */
    fun supports(item: PlayableItem): Boolean

    /**
     * Resolves a directly-playable stream for [songId] (a YouTube video id)
     * at the requested [quality] (section 61). Implementations should pick
     * the closest available bitrate to the request rather than failing
     * when an exact match isn't available.
     * Throws a [ProviderFailure] subtype on any failure.
     */
    suspend fun getStream(songId: String, quality: AudioQuality = AudioQuality.AUTO): ResolvedStream

    /**
     * Resolves richer metadata for [songId]. Throws a [ProviderFailure]
     * subtype on any failure.
     */
    suspend fun getPlayerInfo(songId: String): ProviderPlayerInfo

    /** Current cached health status for this provider, per section 9. */
    suspend fun providerStatus(): ProviderStatus
}
