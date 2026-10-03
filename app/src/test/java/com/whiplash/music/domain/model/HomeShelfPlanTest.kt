// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeShelfPlanTest {

    @Test
    fun `primary artist name`() {
        assertEquals("The Weeknd", primaryArtistName("The Weeknd, Daft Punk"))
        assertEquals("Dua Lipa", primaryArtistName("Dua Lipa feat. DaBaby"))
        assertEquals("Adele", primaryArtistName("Adele - Topic"))
        assertEquals("", primaryArtistName("  "))
    }

    @Test
    fun `artists ranked by plays then recency, case-insensitive`() {
        val played = listOf("Adele", "The Weeknd", "the weeknd, Daft Punk", "Dua Lipa", "Adele", "Ed Sheeran", "")
        assertEquals(listOf("Adele", "The Weeknd", "Dua Lipa"), rankArtists(played, 3))
    }

    @Test
    fun `plan has two shelves per artist then starters`() {
        val plan = planHomeShelves(listOf("Adele", "Dua Lipa"))
        assertEquals(4 + STARTER_SHELVES.size, plan.size)
        assertEquals(ShelfSpec(ShelfKind.ALBUMS, "Albums by Adele", "Adele"), plan[0])
        assertEquals(ShelfKind.PLAYLISTS, plan[1].kind)
        assertEquals(STARTER_SHELVES, plan.takeLast(STARTER_SHELVES.size))
    }

    @Test
    fun `no history gives starter shelves only`() {
        assertEquals(STARTER_SHELVES, planHomeShelves(emptyList()))
    }
}
