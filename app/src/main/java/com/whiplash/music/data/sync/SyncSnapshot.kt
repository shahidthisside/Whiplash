// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.sync

/**
 * Everything cloud sync carries between devices, as plain values so it can be
 * compared and merged without touching the database. Pure Kotlin, unit-tested.
 *
 * Tracks are identified by their YouTube video id alone: a downloaded song and
 * the same song streamed are one song here, and songs from the phone's own
 * music folder (MediaStore ids, different on every device) are never synced.
 */
data class SyncSnapshot(
    /** Metadata for every track referenced below, so rows resolve on a new device. */
    val songs: Map<String, SyncSong> = emptyMap(),
    /** Video id → time it was favourited. */
    val favorites: Map<String, Long> = emptyMap(),
    /** Video id → time it was pinned to Speed dial. */
    val pinned: Map<String, Long> = emptyMap(),
    /** [historyKey] → time played. */
    val history: Map<String, Long> = emptyMap(),
    /** [playlistKey] → playlist. */
    val playlists: Map<String, SyncPlaylist> = emptyMap(),
    /** [replayKey] → Monthly Replay tally row. */
    val replay: Map<String, SyncReplay> = emptyMap(),
    /** Setting name → value as text (see BackupManager.settingsJson). */
    val settings: Map<String, String> = emptyMap(),
    /** Video id → lyrics timing correction in ms. */
    val lyricOffsets: Map<String, Long> = emptyMap(),
    /** The Whiplash profile name and photo, when the user has set them. */
    val profile: SyncProfile? = null,
) {
    /** Same content, ignoring song metadata (which only follows the references). */
    fun sameContentAs(other: SyncSnapshot): Boolean =
        favorites == other.favorites && pinned == other.pinned && history == other.history &&
            playlists == other.playlists && replay == other.replay && settings == other.settings &&
            lyricOffsets == other.lyricOffsets && profile == other.profile

    /** Every video id something in this snapshot points at. */
    fun referencedIds(): Set<String> = buildSet {
        addAll(favorites.keys)
        addAll(pinned.keys)
        history.keys.forEach { add(historyTrackId(it)) }
        playlists.values.forEach { p -> p.tracks.forEach { add(it.id) } }
    }

    companion object {
        fun historyKey(trackId: String, playedAtEpochMs: Long) = "$trackId@$playedAtEpochMs"
        fun historyTrackId(key: String) = key.substringBeforeLast('@')

        /** A playlist's identity across devices: its creation time, which never changes. */
        fun playlistKey(createdAtEpochMs: Long) = "c$createdAtEpochMs"

        fun replayKey(monthKey: String, trackId: String) = "$monthKey|$trackId"
    }
}

/**
 * The user's own profile name and photo (null = use Google's). A reset is
 * kept as a profile with both null, so it still syncs by [updatedAtEpochMs].
 */
class SyncProfile(val name: String?, val photoJpeg: ByteArray?, val updatedAtEpochMs: Long) {
    override fun equals(other: Any?): Boolean =
        other is SyncProfile && name == other.name && updatedAtEpochMs == other.updatedAtEpochMs &&
            photoJpeg.contentEquals(other.photoJpeg)

    override fun hashCode(): Int = (name?.hashCode() ?: 0) * 31 + updatedAtEpochMs.hashCode()
}

data class SyncSong(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0,
    val albumId: String? = null,
    val artistId: String? = null,
    val isExplicit: Boolean = false,
)

data class SyncTrack(val id: String, val addedAtEpochMs: Long)

data class SyncPlaylist(
    val name: String,
    val description: String? = null,
    /** Null for a cover picked from this phone's gallery, which can't travel. */
    val artworkUrl: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val pinnedAtEpochMs: Long? = null,
    val tracks: List<SyncTrack> = emptyList(),
)

data class SyncReplay(
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationMs: Long = 0,
    val plays: Int = 0,
    val listenedMs: Long = 0,
    val lastPlayedAtEpochMs: Long = 0,
)
