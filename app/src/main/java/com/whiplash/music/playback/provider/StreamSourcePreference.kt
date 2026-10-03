// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.playback.provider

/**
 * Where song streams are looked up (Settings → Audio quality → Stream
 * source). Automatic uses NewPipe and, if it can't play a song, asks YouTube
 * directly; the other two use only the source picked.
 */
enum class StreamSourcePreference(val label: String) {
    AUTO("Automatic"),
    NEWPIPE("NewPipe"),
    YOUTUBE_DIRECT("YouTube direct"),
    ;

    /** [all] narrowed and ordered for this preference, by provider id. */
    fun order(all: List<PlaybackProvider>): List<PlaybackProvider> {
        val byId = all.associateBy { it.id }
        val ids = when (this) {
            AUTO -> listOf(NEWPIPE_ID, DIRECT_ID)
            NEWPIPE -> listOf(NEWPIPE_ID)
            YOUTUBE_DIRECT -> listOf(DIRECT_ID)
        }
        // Providers this list doesn't know (none today) keep their place at the end in Automatic.
        val known = ids.mapNotNull { byId[it] }
        return if (this == AUTO) known + all.filter { it.id !in ids } else known.ifEmpty { all }
    }

    companion object {
        const val NEWPIPE_ID = "newpipe"
        const val DIRECT_ID = "youtube_direct"
    }
}
