package com.whiplash.music.domain.model

/**
 * Unified domain model for anything playable in the app, regardless of
 * origin (CLAUDE.md section 28).
 *
 * The player and queue operate on [PlayableItem] only; source-specific
 * playback resolution (MediaStore URI vs. provider-resolved stream) happens
 * behind the [MediaSource]-aware playback layer introduced in later phases.
 */
sealed interface PlayableItem {
    val id: String
    val title: String
    val artist: String
    val album: String?
    val artworkUri: String?
    val durationMs: Long
    val source: MediaSource

    data class LocalTrack(
        override val id: String,
        override val title: String,
        override val artist: String,
        override val album: String?,
        override val artworkUri: String?,
        override val durationMs: Long,
        val mediaStoreUri: String,
    ) : PlayableItem {
        override val source: MediaSource get() = MediaSource.LOCAL
    }

    data class YoutubeTrack(
        override val id: String,
        override val title: String,
        override val artist: String,
        override val album: String?,
        override val artworkUri: String?,
        override val durationMs: Long,
    ) : PlayableItem {
        override val source: MediaSource get() = MediaSource.YOUTUBE
    }
}

/** Mirrors [com.whiplash.music.data.local.entity.MediaSource] at the domain layer. */
enum class MediaSource {
    LOCAL,
    YOUTUBE,
}
