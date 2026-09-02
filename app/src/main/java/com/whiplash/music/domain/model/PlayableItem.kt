package com.whiplash.music.domain.model

/**
 * Unified domain model for anything playable in the app, regardless of
 * origin (CLAUDE.md section 28).
 *
 * The player and queue operate on [PlayableItem] only; source-specific
 * playback resolution (MediaStore URI vs. provider-resolved stream) happens
 * behind the [MediaSource]-aware playback layer introduced in later phases.
 * (-SA, github.com/shahidthisside)
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

    /**
     * A YouTube track saved for offline playback (section: offline
     * downloads). [fileUri] points to a real audio file in app-private
     * storage (`context.filesDir`, see [com.whiplash.music.data.download.DownloadManager]) —
     * playback reads it directly, exactly like [LocalTrack], with no
     * network stream resolution required. [artworkUri] is a local
     * file:// path to a cached copy of the artwork (not the remote URL),
     * so the Downloads tab and badges render correctly with no network
     * access at all.
     */
    data class DownloadedTrack(
        override val id: String,
        override val title: String,
        override val artist: String,
        override val album: String?,
        override val artworkUri: String?,
        override val durationMs: Long,
        val fileUri: String,
    ) : PlayableItem {
        override val source: MediaSource get() = MediaSource.DOWNLOAD
    }
}

/** Mirrors [com.whiplash.music.data.local.entity.MediaSource] at the domain layer. */
enum class MediaSource {
    LOCAL,
    YOUTUBE,
    DOWNLOAD,
}
