// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.home

import com.whiplash.music.ui.home.QuickPicksRefreshPolicy.Decision
import com.whiplash.music.ui.home.QuickPicksRefreshPolicy.Inputs
import com.whiplash.music.ui.home.QuickPicksRefreshPolicy.Network
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class QuickPicksRefreshPolicyTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val hour = 3_600_000L
    private val noon = ZonedDateTime.of(2026, 10, 3, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun inputs(
        ageMs: Long,
        now: Long = noon,
        finished: Int = 0,
        before: List<String> = listOf("Pritam", "A.R. Rahman", "Arijit Singh"),
        current: List<String> = listOf("Pritam", "A.R. Rahman", "Arijit Singh"),
        network: Network = Network.UNMETERED,
        hasList: Boolean = true,
        builtAt: Long? = now - ageMs,
    ) = Inputs(now, builtAt, hasList, finished, before, current, network, zone)

    private fun decide(i: Inputs, forced: Boolean = false) = QuickPicksRefreshPolicy.decide(i, forced)

    @Test fun recentListIsKept() {
        assertEquals(Decision.KEEP, decide(inputs(ageMs = 10 * 60_000L)))
        assertEquals(Decision.KEEP, decide(inputs(ageMs = 2 * hour + 59 * 60_000L)))
    }

    @Test fun oldListIsRebuilt() {
        assertEquals(Decision.REFRESH, decide(inputs(ageMs = 3 * hour)))
    }

    @Test fun noSavedListOrNoBuildInfoRebuilds() {
        assertEquals(Decision.REFRESH, decide(inputs(ageMs = 0, builtAt = null)))
        assertEquals(Decision.REFRESH, decide(inputs(ageMs = 0, hasList = false)))
    }

    @Test fun enoughNewFinishesRebuild() {
        assertEquals(Decision.KEEP, decide(inputs(ageMs = hour, finished = 4)))
        assertEquals(Decision.REFRESH, decide(inputs(ageMs = hour, finished = 5)))
    }

    @Test fun newTopArtistRebuilds() {
        assertEquals(Decision.REFRESH, decide(inputs(ageMs = hour, current = listOf("Pritam", "Diljit Dosanjh"))))
        // A reshuffle of the same artists isn't new taste; case doesn't matter either.
        assertEquals(Decision.KEEP, decide(inputs(ageMs = hour, current = listOf("arijit singh", "Pritam", "A.R. Rahman"))))
        // Only the current top three are checked.
        assertEquals(Decision.KEEP, decide(inputs(ageMs = hour, current = listOf("Pritam", "A.R. Rahman", "Arijit Singh", "Newcomer"))))
    }

    @Test fun firstLaunchOfNewDayRebuilds() {
        val justAfterMidnight = ZonedDateTime.of(2026, 10, 4, 0, 30, 0, 0, zone).toInstant().toEpochMilli()
        // Built an hour before, but yesterday.
        assertEquals(Decision.REFRESH, decide(inputs(ageMs = hour, now = justAfterMidnight)))
    }

    @Test fun mobileDataWaitsLonger() {
        assertEquals(Decision.KEEP, decide(inputs(ageMs = 4 * hour, network = Network.METERED)))
        assertEquals(Decision.REFRESH, decide(inputs(ageMs = 6 * hour, network = Network.METERED)))
        // Taste moving still counts on mobile data.
        assertEquals(Decision.REFRESH, decide(inputs(ageMs = hour, finished = 5, network = Network.METERED)))
    }

    @Test fun offlineNeverTriesButRemembers() {
        assertEquals(Decision.KEEP, decide(inputs(ageMs = hour, network = Network.OFFLINE)))
        assertEquals(Decision.WAIT_FOR_NETWORK, decide(inputs(ageMs = 5 * hour, network = Network.OFFLINE)))
        assertEquals(Decision.WAIT_FOR_NETWORK, decide(inputs(ageMs = 0, builtAt = null, network = Network.OFFLINE)))
    }

    @Test fun manualRefreshAlwaysRebuilds() {
        assertEquals(Decision.REFRESH, decide(inputs(ageMs = 60_000L), forced = true))
        assertEquals(Decision.REFRESH, decide(inputs(ageMs = 60_000L, network = Network.METERED), forced = true))
    }

    @Test fun clockSetBackRebuilds() {
        assertEquals(Decision.REFRESH, decide(inputs(ageMs = -hour)))
    }
}
