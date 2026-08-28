package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A device-local artist, derived from MediaStore's artist grouping.
 * [artistId] mirrors MediaStore's `Audio.Artists._ID`.
 */
@Entity(tableName = "local_artists")
data class LocalArtistEntity(
    @PrimaryKey val artistId: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
)
