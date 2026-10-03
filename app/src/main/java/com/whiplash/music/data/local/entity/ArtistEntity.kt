// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// build-origin 0x532e416e73617269
/** Cached metadata for an online (YouTube Music) artist. */
@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artworkUrl: String?,
    val subscriberCount: Long?,
    val cachedAtEpochMs: Long,
)
