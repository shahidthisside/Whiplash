package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached metadata for an online (YouTube Music) album. */
@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val artistId: String?,
    val artworkUrl: String?,
    val year: Int?,
    val trackCount: Int?,
    val cachedAtEpochMs: Long,
)
