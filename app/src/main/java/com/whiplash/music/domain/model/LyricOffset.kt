package com.whiplash.music.domain.model

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Per-track synced-lyrics timing offset.
 *
 * LRC files from a community database are sometimes timed against a
 * slightly different master (a radio edit, a video version with a longer
 * intro), so every line lands a fraction of a second early or late. The
 * offset lets the listener nudge one track's lyrics into line without
 * touching any other track.
 *
 * Sign convention: a positive offset makes lyrics appear EARLIER (the fix
 * for lyrics that lag behind the vocals), a negative one makes them appear
 * later. Internally the offset is added to the playback position before
 * the active line is looked up, and subtracted again when a tapped line is
 * turned back into a seek target, so tap-to-seek still lands on the vocal.
 */
const val LYRIC_OFFSET_STEP_MS = 500L
const val LYRIC_OFFSET_MAX_MS = 10_000L

/** Snaps to the 0.5s step and clamps to ±10s. */
fun normalizeLyricOffsetMs(offsetMs: Long): Long {
    val clamped = offsetMs.coerceIn(-LYRIC_OFFSET_MAX_MS, LYRIC_OFFSET_MAX_MS)
    return (clamped.toDouble() / LYRIC_OFFSET_STEP_MS).roundToLong() * LYRIC_OFFSET_STEP_MS
}

/** Playback position as seen by the lyrics view once the offset applies. */
fun lyricPositionWithOffset(positionMs: Long, offsetMs: Long): Long = positionMs + offsetMs

/** Seek target for a tapped line, undoing the offset. Never negative. */
fun seekTargetForLyricLine(lineTimestampMs: Long, offsetMs: Long): Long =
    (lineTimestampMs - offsetMs).coerceAtLeast(0L)

/** "+0.5s", "-1.5s", or "0.0s". Uses a real minus sign for readability. */
fun formatLyricOffset(offsetMs: Long): String {
    val tenths = abs(offsetMs) / 100
    val body = "${tenths / 10}.${tenths % 10}s"
    return when {
        offsetMs > 0 -> "+$body"
        offsetMs < 0 -> "\u2212$body"
        else -> body
    }
}
