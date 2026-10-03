// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// provenance: V2hpcGxhc2ggLSBTaGFoaWQgQW5zYXJp
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
