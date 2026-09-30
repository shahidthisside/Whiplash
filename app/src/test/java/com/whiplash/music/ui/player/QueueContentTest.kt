package com.whiplash.music.ui.player

import com.whiplash.music.domain.model.PlayableItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueContentTest {

    private fun track(id: String, durationMs: Long = 200_000L) =
        PlayableItem.YoutubeTrack(id = id, title = "Song $id", artist = "Artist", album = null, artworkUri = null, durationMs = durationMs)

    @Test
    fun sameSongTwiceGetsDistinctKeys() {
        val entries = buildQueueEntries(listOf(track("a"), track("b"), track("a")), emptySet())
        assertEquals(3, entries.map { it.key }.toSet().size)
        assertEquals("YOUTUBE:a#2", entries[2].key)
    }

    @Test
    fun keysFollowTheSongNotThePosition() {
        val before = buildQueueEntries(listOf(track("a"), track("b"), track("c")), emptySet())
        val after = buildQueueEntries(listOf(track("c"), track("a"), track("b")), emptySet())
        assertEquals(before.first { it.item.id == "c" }.key, after[0].key)
    }

    @Test
    fun autoplayFlagComesFromIds() {
        val entries = buildQueueEntries(listOf(track("a"), track("b")), setOf("b"))
        assertFalse(entries[0].fromAutoplay)
        assertTrue(entries[1].fromAutoplay)
    }

    @Test
    fun summaryCountsSongsAndTime() {
        assertEquals("Nothing up next", upNextSummary(emptyList()))
        val three = buildQueueEntries(List(3) { track("t$it", 240_000L) }, emptySet())
        assertEquals("3 songs up next · 12 min", upNextSummary(three))
        val long = buildQueueEntries(List(20) { track("l$it", 240_000L) }, emptySet())
        assertEquals("20 songs up next · 1 h 20 min", upNextSummary(long))
        val unknown = buildQueueEntries(listOf(track("u", 0L)), emptySet())
        assertEquals("1 song up next", upNextSummary(unknown))
    }

    @Test
    fun trackTimeFormatting() {
        assertEquals("3:05", formatTrackTime(185_000L))
        assertNull(formatTrackTime(0L))
    }
}
