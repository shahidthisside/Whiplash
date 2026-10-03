// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsHighlightTest {

    private val line = LyricLine(
        10_000, "a held c",
        listOf(
            LyricWord(10_000, 10_300, "a "),
            LyricWord(10_300, 12_300, "held "),
            LyricWord(12_300, 12_600, "c"),
        ),
    )

    @Test
    fun `held word detection`() {
        assertFalse(isHeldWord(line.words[0]))
        assertTrue(isHeldWord(line.words[1]))
        assertFalse(isHeldWord(LyricWord(0, null, "x")))
    }

    @Test
    fun `words before are sung, after are empty`() {
        val h = wordHighlights(line, 11_300)
        assertEquals(1f, h[0].fill, 0.001f)
        assertEquals(0.5f, h[1].fill, 0.001f)
        assertEquals(0f, h[2].fill, 0.001f)
        assertEquals(1f, h[1].bloom, 0.001f)
        assertEquals(0f, h[0].bloom, 0.001f)
    }

    @Test
    fun `short word never blooms, held word fades out`() {
        assertEquals(0f, wordHighlights(line, 10_150)[0].bloom, 0.001f)
        assertTrue(wordHighlights(line, 12_250)[1].bloom < 0.2f)
        assertEquals(0f, wordHighlights(line, 12_400)[1].bloom, 0.001f)
    }

    @Test
    fun `before the line nothing is lit`() {
        assertTrue(wordHighlights(line, 9_000).all { it.fill == 0f && it.bloom == 0f })
    }

    @Test
    fun `bloom curve shape`() {
        assertEquals(0f, bloomCurve(0f), 0.001f)
        assertEquals(1f, bloomCurve(0.5f), 0.001f)
        assertEquals(0f, bloomCurve(1f), 0.001f)
    }

    @Test
    fun `line emphasis`() {
        assertEquals(LineEmphasis(1f, 0f), lineEmphasis(0))
        assertTrue(lineEmphasis(1).alpha > lineEmphasis(-1).alpha)
        assertTrue(lineEmphasis(1).alpha > lineEmphasis(4).alpha)
        assertEquals(3f, lineEmphasis(9).blurSteps, 0.001f)
        assertTrue(lineEmphasis(-20).alpha >= 0.3f)
    }
}
