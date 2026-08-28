package com.whiplash.music.data.local.entity

import androidx.room.Entity

/**
 * A track pinned to the Home screen's "Speed dial" grid (YouTube-Music-
 * style pin-to-speed-dial), identified by composite key (trackId, source),
 * mirroring [FavoriteEntity]'s shape exactly.
 */
@Entity(tableName = "pinned_speed_dial", primaryKeys = ["trackId", "source"])
data class PinnedEntity(
    val trackId: String,
    val source: MediaSource,
    val pinnedAtEpochMs: Long,
)
