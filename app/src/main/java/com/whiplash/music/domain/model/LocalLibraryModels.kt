// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

/** Domain-layer representation of a device-local album. */
data class LocalAlbum(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val year: Int?,
)

/** Domain-layer representation of a device-local artist. */
data class LocalArtist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
)
