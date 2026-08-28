package com.whiplash.music.playback.controller

import com.whiplash.music.domain.model.PlayableItem

/** UI-facing snapshot of current playback state, independent of Media3 types. */
data class PlaybackState(
    val currentItem: PlayableItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** True while a YouTube stream is being resolved via the provider layer, before Media3 has anything to play. */
    val isResolvingStream: Boolean = false,
    /** Set when stream resolution fails in a way that isn't failover-eligible (section 8: report the failure). */
    val playbackError: PlaybackError? = null,
    /** Full playback queue (section 21): current + upcoming tracks, in play order. */
    val queue: List<PlayableItem> = emptyList(),
    /** Index of [currentItem] within [queue], or -1 if nothing is queued. */
    val currentIndex: Int = -1,
    /** Active sleep timer mode, or null if none is set (section 60). */
    val sleepTimer: SleepTimerMode? = null,
    /** Milliseconds remaining until the active duration-based sleep timer fires, or null for non-duration modes / no timer. */
    val sleepTimerRemainingMs: Long? = null,
)

enum class RepeatMode { OFF, ONE, ALL }

/** Sleep timer options (section 60). Duration modes pause playback when the countdown reaches zero; the two "end of" modes pause at the next natural track/queue boundary instead of a fixed time. */
sealed interface SleepTimerMode {
    data class Duration(val totalMs: Long) : SleepTimerMode
    data object EndOfSong : SleepTimerMode
    data object EndOfQueue : SleepTimerMode
}

/** UI-facing description of a playback failure that could not be resolved by any provider. */
data class PlaybackError(val itemTitle: String, val message: String)
