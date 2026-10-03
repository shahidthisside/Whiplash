// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.recommend

import android.util.Log
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.innertube.InnerTubeCursor
import com.whiplash.music.innertube.InnerTubePage
import com.whiplash.music.innertube.InnerTubeRelated

/**
 * Where radio songs come from: YouTube Music's own API first (it says which
 * uploads are the official audio), NewPipe when that fails. A radio started
 * on one keeps paging on it, since their cursors aren't interchangeable.
 */
class MusicSources(
    private val innerRadio: (suspend (seedId: String, cursor: InnerTubeCursor?) -> InnerTubePage)?,
    private val innerRelated: (suspend (id: String) -> InnerTubeRelated)?,
    private val fallbackRadio: suspend (seedId: String, cursor: Any?) -> RadioPage,
    private val fallbackRelated: suspend (id: String) -> List<PlayableItem.YoutubeTrack>,
) {
    suspend fun radioPage(seedId: String, cursor: Any?): RadioPage {
        val inner = innerRadio
        if (inner != null && (cursor == null || cursor is InnerTubeCursor)) {
            val page = runCatching { inner(seedId, cursor as InnerTubeCursor?) }
                .onFailure { Log.w(TAG, "InnerTube radio failed, using NewPipe: $it") }
                .getOrNull()
            if (page != null && page.items.isNotEmpty()) {
                page.items.forEach { UploadKinds.record(it.track.id, it.kind) }
                return RadioPage(page.items.map { it.track }, page.next)
            }
            // Ran out mid-radio: the session moves on to its next seed.
            if (cursor != null) return RadioPage(emptyList(), null)
        }
        return fallbackRadio(seedId, cursor.takeUnless { it is InnerTubeCursor })
    }

    /** YT Music's "You might also like" for [id]; NewPipe's related videos when unavailable. */
    suspend fun related(id: String): List<PlayableItem.YoutubeTrack> {
        val inner = innerRelated
        if (inner != null) {
            val songs = runCatching { inner(id).songs }
                .onFailure { Log.w(TAG, "InnerTube related failed, using NewPipe: $it") }
                .getOrNull().orEmpty()
            if (songs.isNotEmpty()) {
                songs.forEach { UploadKinds.record(it.track.id, it.kind) }
                return songs.map { it.track }
            }
        }
        return fallbackRelated(id)
    }

    private companion object { const val TAG = "WhiplashRadio" }
}
