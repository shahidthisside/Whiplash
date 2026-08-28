package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single playback history entry. Kept append-only with a timestamp;
 * "recently played" queries read the most recent rows per track.
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val source: MediaSource,
    val playedAtEpochMs: Long,
)
