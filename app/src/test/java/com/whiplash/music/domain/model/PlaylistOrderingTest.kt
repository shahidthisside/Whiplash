package com.whiplash.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistOrderingTest {

    @Test
    fun `pinned first in pin order, then the rest unchanged`() {
        val rows = listOf("a" to null, "b" to 300L, "c" to null, "d" to 100L, "e" to null)
        assertEquals(listOf("d", "b", "a", "c", "e"), orderPlaylists(rows))
    }

    @Test
    fun `nothing pinned keeps incoming order`() {
        val rows = listOf("x" to null, "y" to null)
        assertEquals(listOf("x", "y"), orderPlaylists<String>(rows))
    }

    @Test
    fun `empty list`() {
        assertEquals(emptyList<String>(), orderPlaylists<String>(emptyList()))
    }

    @Test
    fun `pin limit`() {
        assertTrue(canPinAnother(0))
        assertTrue(canPinAnother(MAX_PINNED_PLAYLISTS - 1))
        assertFalse(canPinAnother(MAX_PINNED_PLAYLISTS))
    }
}
