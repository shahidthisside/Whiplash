// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

/** A user-created playlist (section 38), local-first regardless of what it contains. */
data class Playlist(
    val id: Long,
    val name: String,
    val description: String?,
    val artworkUrl: String?,
    val pinned: Boolean = false,
)
