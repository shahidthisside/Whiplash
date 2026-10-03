// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.repository

import android.content.Context
import com.whiplash.music.domain.model.normalizeLyricOffsetMs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers the synced-lyrics offset the listener chose for each track.
 *
 * Deliberately a tiny SharedPreferences map rather than DataStore or a
 * Room table: it is keyed by track id, read once when the lyrics sheet
 * opens and written only when the listener taps an adjust button, so the
 * async machinery of either alternative buys nothing. Only non-zero
 * offsets are stored, so the file stays small no matter how many tracks
 * are played.
 */
class LyricOffsetStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _offsets = MutableStateFlow(readAll())

    /** All non-zero offsets, keyed by track id. */
    val offsets: StateFlow<Map<String, Long>> = _offsets.asStateFlow()

    fun offsetFor(trackId: String): Long = _offsets.value[trackId] ?: 0L

    fun setOffset(trackId: String, offsetMs: Long) {
        val normalized = normalizeLyricOffsetMs(offsetMs)
        prefs.edit().apply {
            if (normalized == 0L) remove(trackId) else putLong(trackId, normalized)
        }.apply()
        _offsets.value = if (normalized == 0L) {
            _offsets.value - trackId
        } else {
            _offsets.value + (trackId to normalized)
        }
    }

    private fun readAll(): Map<String, Long> =
        prefs.all.mapNotNull { (key, value) -> (value as? Long)?.let { key to it } }.toMap()

    private companion object {
        const val PREFS_NAME = "lyric_offsets"
    }
}
