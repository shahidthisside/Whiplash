package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Lifecycle state of an offline download, persisted so in-flight/failed downloads survive process death. */
enum class DownloadStatus {
    DOWNLOADING,
    COMPLETED,
    FAILED,
}

/**
 * A YouTube track saved for offline playback (YouTube-Music-style
 * downloads). Unlike [SongEntity] (metadata-only cache, section 11: never
 * persist stream URLs), this table stores real, permanent references to
 * audio bytes on disk ([filePath], app-private storage) — that is the
 * entire point of a download, so section 11's "never persist a stream URL"
 * guidance does not apply here (this is never a `googlevideo.com` URL).
 *
 * [id] is the stable YouTube video id (same id space as [SongEntity]).
 * [artworkPath] is a local file:// copy of the artwork (downloaded once,
 * alongside the audio) so the Downloads tab and checkmark badges never
 * need network access to render.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkPath: String?,
    val durationMs: Long,
    val filePath: String,
    val fileSizeBytes: Long,
    val status: DownloadStatus,
    val downloadedAtEpochMs: Long,
)
