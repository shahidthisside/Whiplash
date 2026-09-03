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

/**
 * Identity key for "is this the same track" comparisons that must treat
 * [MediaSource.YOUTUBE] and [MediaSource.DOWNLOAD] as equivalent — a
 * [PlayableItem.DownloadedTrack]'s [PlayableItem.id] is always the exact
 * same YouTube video id it was downloaded from (see that class's own
 * doc), so the same song played once as a live stream and once as an
 * offline download is genuinely the same track, not two different ones.
 * [MediaSource.LOCAL] is never normalized with the other two — a
 * MediaStore row id is a completely different namespace, and a
 * coincidental numeric collision with a YouTube video id would be a
 * real (if rare) false-positive merge otherwise.
 *
 * Mirrors the equivalent SQL-level normalization in
 * [com.whiplash.music.data.local.dao.HistoryDao] and
 * [com.whiplash.music.data.local.dao.PinnedDao] (both fixed for the same
 * real, reported bug: playing/pinning a song from two different
 * surfaces — e.g. Search vs. the Downloads tab — produced duplicate
 * History/Speed dial entries for what a user experiences as one song).
 */
fun PlayableItem.speedDialIdentity(): Pair<String, MediaSource> =
    id to if (source == MediaSource.DOWNLOAD) MediaSource.YOUTUBE else source
