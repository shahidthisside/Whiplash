// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.recommend

/** One past play, as the learning code needs it (a view of a play_events row). */
data class PastPlay(
    val trackId: String,
    val title: String,
    val artist: String,
    val artistKey: String,
    val language: String?,
    val origin: String,
    val radioSeedId: String?,
    val startedAt: Long,
    val completed: Boolean,
    val skipped: Boolean,
) {
    val fromAutoplay: Boolean get() = origin.startsWith("AUTOPLAY")

    /** The radio source an autoplay play came from ("AUTOPLAY:KEPT" → KEPT). */
    val source: String? get() = origin.substringAfter(':', "").ifEmpty { null }
}
