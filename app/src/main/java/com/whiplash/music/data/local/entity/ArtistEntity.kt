package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached metadata for an online (YouTube Music) artist. */
@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artworkUrl: String?,
    val subscriberCount: Long?,
    val cachedAtEpochMs: Long,
)
