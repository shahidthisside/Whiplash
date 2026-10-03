// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.local.entity

import androidx.room.Entity

/** A liked/favorited track, identified by composite key (trackId, source). */
@Entity(tableName = "favorites", primaryKeys = ["trackId", "source"])
data class FavoriteEntity(
    val trackId: String,
    val source: MediaSource,
    val addedAtEpochMs: Long,
)
