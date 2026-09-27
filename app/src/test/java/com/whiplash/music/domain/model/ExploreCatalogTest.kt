package com.whiplash.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreCatalogTest {

    @Test
    fun `genre ids are unique and queries are filled`() {
        assertEquals(EXPLORE_GENRES.size, EXPLORE_GENRES.map { it.id }.toSet().size)
        EXPLORE_GENRES.forEach {
            assertTrue(it.id, it.playlistQuery.isNotBlank() && it.songQuery.isNotBlank())
            assertTrue(it.id, it.colorIndex in 0..11)
        }
    }

    @Test
    fun `lookup by id`() {
        assertEquals("Chill", exploreGenre("chill")?.title)
        assertNull(exploreGenre("nope"))
    }

    @Test
    fun `fixed shelves are distinct`() {
        assertTrue(EXPLORE_NEW_RELEASES.key != EXPLORE_CHARTS.key)
    }
}
