package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A song discovered on-device via MediaStore (section 25).
 *
 * [mediaStoreId] is the MediaStore _ID for the audio row and is used to
 * build/refresh the content URI; it is also used as the primary key since
 * it is stable for the lifetime of the file on this device (until the
 * MediaStore re-indexes it, at which point a rescan reconciles rows).
 *
 * No files are copied into app-private storage — [uri] is a MediaStore
 * content:// URI, not a filesystem path (section 25).
 */
@Entity(tableName = "local_songs")
data class LocalSongEntity(
    @PrimaryKey val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtist: String?,
    val durationMs: Long,
    val uri: String,
    val trackNumber: Int?,
    val year: Int?,
    val genre: String?,
    val albumId: Long?,
    val artistId: Long?,
    val sizeBytes: Long,
    val dateAddedEpochSec: Long,
    val dateModifiedEpochSec: Long,
    val mimeType: String?,
)
