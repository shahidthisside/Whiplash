// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class InterpolatedPositionTest {
    @Test fun normalSpeedFollowsTheClock() {
        assertEquals(10_250L, interpolatedPositionMs(10_000L, 250L, 1f))
    }

    @Test fun fasterAndSlowerSpeedsScaleTheGap() {
        assertEquals(10_750L, interpolatedPositionMs(10_000L, 500L, 1.5f))
        assertEquals(10_250L, interpolatedPositionMs(10_000L, 500L, 0.5f))
        assertEquals(11_000L, interpolatedPositionMs(10_000L, 500L, 2f))
    }

    @Test fun clockGoingBackwardsNeverMovesLyricsBack() {
        assertEquals(10_000L, interpolatedPositionMs(10_000L, -300L, 1.5f))
    }
}
