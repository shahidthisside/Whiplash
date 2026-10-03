// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class ReplayTest {

    private fun stat(id: String, artist: String, plays: Int, listenedMs: Long = plays * 180_000L, last: Long = 0L) =
        ReplayTrackStat(id, MediaSource.YOUTUBE, "Song $id", artist, "art-$id", 180_000L, plays, listenedMs, last)

    @Test
    fun `songs rank by plays then listened time then recency`() {
        val s = buildReplaySummary(
            "2026-09",
            listOf(
                stat("a", "A", plays = 3),
                stat("b", "B", plays = 5),
                stat("c", "C", plays = 3, listenedMs = 900_000L),
                stat("d", "D", plays = 3, last = 99L),
            ),
        )
        assertEquals(listOf("b", "c", "d", "a"), s.topTracks.map { it.trackId })
        assertEquals(14, s.totalPlays)
        assertEquals(4, s.songCount)
    }

    @Test
    fun `artists group by primary name case-insensitively`() {
        val s = buildReplaySummary(
            "2026-09",
            listOf(
                stat("a", "Dua Lipa", plays = 2),
                stat("b", "dua lipa, DaBaby", plays = 3),
                stat("c", "Queen", plays = 3),
                stat("d", "Queen - Topic", plays = 1),
                stat("e", "Adele", plays = 3),
            ),
        )
        assertEquals(listOf("Dua Lipa", "Queen", "Adele"), s.topArtists.map { it.name })
        assertEquals(5, s.topArtists[0].plays)
        assertEquals(2, s.topArtists[0].songCount)
        // Picture comes from the artist's most-played song.
        assertEquals("art-b", s.topArtists[0].artworkUrl)
        assertEquals(3, s.artistCount)
    }

    @Test
    fun `lists are capped and minutes are real listened time`() {
        val stats = (1..8).map { stat("$it", "Artist $it", plays = it, listenedMs = 60_000L) }
        val s = buildReplaySummary("2026-09", stats)
        assertEquals(REPLAY_TOP_COUNT, s.topTracks.size)
        assertEquals(REPLAY_TOP_COUNT, s.topArtists.size)
        assertEquals(8L, s.listenedMinutes)
    }

    @Test
    fun `rows with time but no counted play are kept out of song count`() {
        val s = buildReplaySummary("2026-09", listOf(stat("a", "A", plays = 0, listenedMs = 120_000L), stat("b", "B", plays = 0, listenedMs = 0L)))
        assertEquals(0, s.totalPlays)
        assertEquals(0, s.songCount)
        assertEquals(2L, s.listenedMinutes)
        assertTrue(s.isEmpty)
    }

    @Test
    fun `month key and label`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val justAfterMidnight = ZonedDateTime.of(2026, 10, 1, 0, 5, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("2026-10", replayMonthKey(justAfterMidnight, zone))
        assertEquals("2026-09", replayMonthKey(justAfterMidnight, ZoneId.of("UTC")))
        assertEquals("September 2026", replayMonthLabel("2026-09", Locale.ENGLISH))
        assertEquals("bad", replayMonthLabel("bad", Locale.ENGLISH))
    }

    @Test
    fun `default month prefers current once it has enough plays`() {
        assertEquals("2026-09", defaultReplayMonth("2026-09", 5, listOf("2026-09", "2026-08")))
        assertEquals("2026-08", defaultReplayMonth("2026-09", 2, listOf("2026-09", "2026-08", "2026-07")))
        assertEquals("2026-09", defaultReplayMonth("2026-09", 2, listOf("2026-09")))
        assertNull(defaultReplayMonth("2026-09", 0, emptyList()))
    }
}
