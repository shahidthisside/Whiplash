package com.whiplash.music.data.local.entity

import androidx.room.Entity

/** A liked/favorited track, identified by composite key (trackId, source). */
@Entity(tableName = "favorites", primaryKeys = ["trackId", "source"])
data class FavoriteEntity(
    val trackId: String,
    val source: MediaSource,
    val addedAtEpochMs: Long,
)
