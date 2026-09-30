package com.whiplash.music.recommend

import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import java.util.TimeZone

class LearningTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun t(id: String, title: String, artist: String, dur: Long = 200_000L) =
        PlayableItem.YoutubeTrack(id = id, title = title, artist = artist, album = null, artworkUri = null, durationMs = dur)

    private fun play(
        artist: String, at: Long, completed: Boolean = true, skipped: Boolean = false, title: String = "Song",
        origin: String = "USER", seed: String? = null, lang: String? = null, id: String = "$artist$at",
    ) = PastPlay(id, title, artist, RadioRules.artistKey(artist), lang, origin, seed, at, completed, skipped)

    // ── 3.1 co-listening ───────────────────────────────────────────────
    @Test fun artistsPlayedTogetherBecomeClose() {
        val plays = (0 until 6).flatMap { s ->
            val start = s * day
            listOf(play("Karan Aujla", start), play("Shubh", start + 4 * 60_000), play("AP Dhillon", start + 8 * 60_000))
        } + play("Taylor Swift", 50 * day)
        val c = CoListen().apply { rebuildHistory(plays) }
        val aujla = RadioRules.artistKey("Karan Aujla")
        assertTrue(c.similarity(aujla, RadioRules.artistKey("Shubh")) > 0.3)
        assertEquals(0.0, c.similarity(aujla, RadioRules.artistKey("Taylor Swift")), 1e-9)
    }

    @Test fun skippedPlaysAndSeparateSittingsDontLink() {
        val plays = (0 until 6).flatMap { s ->
            val start = s * day
            // Same sitting but skipped, and a play hours later.
            listOf(play("A One", start), play("B Two", start + 60_000, completed = false, skipped = true), play("C Three", start + 5 * hour))
        }
        val c = CoListen().apply { rebuildHistory(plays) }
        assertEquals(0.0, c.similarity("aone", "btwo"), 1e-9)
        assertEquals(0.0, c.similarity("aone", "cthree"), 1e-9)
    }

    @Test fun radioPagesLinkArtistsAndSurviveSaving() {
        val c = CoListen()
        repeat(3) { c.addPage(listOf("arijitsingh", "pritam", "vishalmishra")) }
        assertTrue(c.similarity("arijitsingh", "pritam") > 0.3)
        val copy = CoListen().apply { loadPages(JSONObject(c.toJson().toString())) }
        assertEquals(c.similarity("arijitsingh", "pritam"), copy.similarity("arijitsingh", "pritam"), 1e-9)
    }
}
