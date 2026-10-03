// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

import kotlin.math.roundToInt

// Whiplash internals. Owner: github.com/shahidthisside
/**
 * Allowed ranges for the user-adjustable crossfade and playback speed.
 *
 * Kept in one place so the settings store, the Settings screen and the
 * full player's speed sheet can never disagree about what is valid.
 * Every value the previous chip pickers offered (crossfade 0/3/6/10s,
 * speed 0.75x..2.0x) lies inside these ranges and on these steps, so
 * existing saved preferences and backups carry over unchanged.
 */
object PlaybackTuning {
    const val CROSSFADE_MAX_MS = 12_000
    const val CROSSFADE_STEP_MS = 1_000

    const val SPEED_MIN = 0.5f
    const val SPEED_MAX = 2.0f
    const val SPEED_STEP = 0.05f

    /** One-tap shortcuts shown next to the speed slider. */
    val SPEED_PRESETS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    /** Clamps to 0..[CROSSFADE_MAX_MS] and snaps to whole [CROSSFADE_STEP_MS] steps. */
    fun normalizeCrossfadeMs(ms: Int): Int {
        val clamped = ms.coerceIn(0, CROSSFADE_MAX_MS)
        return (clamped.toFloat() / CROSSFADE_STEP_MS).roundToInt() * CROSSFADE_STEP_MS
    }

    /**
     * Clamps to [SPEED_MIN]..[SPEED_MAX] and snaps to the nearest
     * [SPEED_STEP], returning a value rounded to two decimals. A slider
     * reports values like 0.7500001f; snapping means the stored value
     * compares equal to the matching preset and displays cleanly.
     */
    fun normalizeSpeed(speed: Float): Float {
        if (speed.isNaN()) return 1.0f
        val clamped = speed.coerceIn(SPEED_MIN, SPEED_MAX)
        val steps = (clamped / SPEED_STEP).roundToInt()
        return (steps * SPEED_STEP * 100f).roundToInt() / 100f
    }

    /** "1x", "1.25x", "0.5x" — trailing zeros dropped. */
    fun formatSpeed(speed: Float): String {
        val hundredths = (speed * 100f).roundToInt()
        val whole = hundredths / 100
        val frac = hundredths % 100
        val text = when {
            frac == 0 -> "$whole"
            frac % 10 == 0 -> "$whole.${frac / 10}"
            else -> "$whole.${frac.toString().padStart(2, '0')}"
        }
        return "${text}x"
    }
}
