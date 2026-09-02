package com.whiplash.music.data.local.entity

/**
 * Identifies where a piece of media actually comes from, per CLAUDE.md
 * section 27/28: "The player must identify the source: YOUTUBE, LOCAL."
 * Used across playlist entries, history, and favorites so the same tables
 * can reference either a [SongEntity] (online) or a [LocalSongEntity]
 * (on-device) row without assuming they behave identically.
 *
 * [DOWNLOAD] identifies a YouTube track saved for offline playback (a
 * [com.whiplash.music.data.local.entity.DownloadEntity] row) — distinct
 * from [LOCAL] (which is the device's own MediaStore library) since a
 * download's audio bytes live in app-private storage, not on the shared
 * MediaStore-indexed filesystem.
 */
enum class MediaSource {
    YOUTUBE,
    LOCAL,
    DOWNLOAD,
}
