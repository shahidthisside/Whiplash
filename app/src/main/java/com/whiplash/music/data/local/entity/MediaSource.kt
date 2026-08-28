package com.whiplash.music.data.local.entity

/**
 * Identifies where a piece of media actually comes from, per CLAUDE.md
 * section 27/28: "The player must identify the source: YOUTUBE, LOCAL."
 * Used across playlist entries, history, and favorites so the same tables
 * can reference either a [SongEntity] (online) or a [LocalSongEntity]
 * (on-device) row without assuming they behave identically.
 */
enum class MediaSource {
    YOUTUBE,
    LOCAL,
}
