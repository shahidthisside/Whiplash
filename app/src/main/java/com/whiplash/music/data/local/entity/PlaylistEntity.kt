package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-created playlist. Playlists are always stored locally (local-first,
 * section 63) even when they reference online tracks — only the *reference*
 * (track id + source) is stored, per [PlaylistTrackEntity].
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
