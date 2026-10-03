// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// (c) Shahid A. 2026, Whiplash. Do not copy.
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
