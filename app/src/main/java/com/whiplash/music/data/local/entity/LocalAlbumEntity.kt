package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A device-local album, derived from MediaStore's album grouping.
 * [albumId] mirrors MediaStore's `Audio.Albums._ID`.
 */
@Entity(tableName = "local_albums")
data class LocalAlbumEntity(
    @PrimaryKey val albumId: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val year: Int?,
)
