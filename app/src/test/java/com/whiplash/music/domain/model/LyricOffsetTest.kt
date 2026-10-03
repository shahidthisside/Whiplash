// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricOffsetTest {

    @Test
    fun normalize_snapsToHalfSecondSteps() {
        assertEquals(500L, normalizeLyricOffsetMs(400L))
        assertEquals(0L, normalizeLyricOffsetMs(200L))
        assertEquals(-1_500L, normalizeLyricOffsetMs(-1_400L))
    }

    @Test
    fun normalize_clampsToTenSeconds() {
        assertEquals(LYRIC_OFFSET_MAX_MS, normalizeLyricOffsetMs(60_000L))
        assertEquals(-LYRIC_OFFSET_MAX_MS, normalizeLyricOffsetMs(-60_000L))
    }

    @Test
    fun positiveOffset_showsLinesEarlier_andSeekUndoesIt() {
        // With +1s, a line stamped at 10s becomes active at 9s of audio...
        assertEquals(10_000L, lyricPositionWithOffset(9_000L, 1_000L))
        // ...and tapping it seeks to 9s, where the vocal actually is.
        assertEquals(9_000L, seekTargetForLyricLine(10_000L, 1_000L))
    }

    @Test
    fun seekTarget_neverNegative() {
        assertEquals(0L, seekTargetForLyricLine(200L, 1_000L))
    }

    @Test
    fun format_showsSignAndOneDecimal() {
        assertEquals("0.0s", formatLyricOffset(0L))
        assertEquals("+0.5s", formatLyricOffset(500L))
        assertEquals("\u22121.5s", formatLyricOffset(-1_500L))
        assertEquals("+10.0s", formatLyricOffset(10_000L))
    }
}
