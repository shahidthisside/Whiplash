// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Written for Whiplash by its author, Shahid Ansari; copies are infringing.
/**
 * Cached metadata for an online (YouTube/YouTube Music) track.
 *
 * This table stores metadata only — never a stream URL (section 11: "Never
 * persist temporary YouTube stream URLs as permanent metadata") and never
 * cookies/tokens (section 35/36). Streams are always re-resolved through the
 * provider layer at playback time.
 *
 * [id] is the stable YouTube video/track id, which is safe to persist since
 * it identifies content, not a transient resource location.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long,
    val albumId: String?,
    val artistId: String?,
    val isExplicit: Boolean = false,
    val cachedAtEpochMs: Long,
)
