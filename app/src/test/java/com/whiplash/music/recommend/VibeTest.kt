package com.whiplash.music.recommend

import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VibeTest {
    private fun t(id: String, title: String, artist: String, dur: Long = 200_000L) =
        PlayableItem.YoutubeTrack(id = id, title = title, artist = artist, album = null, artworkUri = null, durationMs = dur)

    @Test fun tagsFromTitleAndArtist() {
        val sad = VibeTagger.tag("Tanha Dil | Sad Song", "Unknown")
        assertTrue(Mood.SAD in sad.moods)
        assertTrue(sad.energy!! < 0.4f)
        val drill = VibeTagger.tag("8 Asle", "Sukha")
        assertTrue("drill" in drill.genres)
        assertTrue(drill.energy!! > 0.7f)
        assertEquals(1990, VibeTagger.tag("Tu Cheez Badi Hai Mast (1994)", "x").decade)
        assertEquals(1980, VibeTagger.tag("80s Hits", "x").decade)
        val slowed = VibeTagger.tag("Kesariya (Slowed + Reverb)", "Arijit Singh")
        assertEquals(SongVersion.SLOWED, slowed.version)
    }

    @Test fun sessionPrefersItsOwnFeel() {
        val s = SessionVibe()
        s.add(VibeTagger.tag("Channa Mereya", "Arijit Singh"), 3.0)
        val sad = s.similarity(VibeTagger.tag("Tujhe Bhula Diya", "Vishal Mishra"))
        val party = s.similarity(VibeTagger.tag("Party All Night", "Yo Yo Honey Singh"))
        assertTrue("sad=$sad party=$party", sad > party + 0.2)
        assertFalse(s.accepts(SongVersion.SLOWED))
        assertTrue(s.accepts(SongVersion.ORIGINAL))
    }

    @Test fun skipsNeverEraseTheSeed() = runBlocking {
        val s = SessionVibe()
        s.add(VibeTagger.tag("Channa Mereya", "Arijit Singh"), 3.0, anchor = true)
        repeat(20) { s.add(VibeTagger.tag("Tum Hi Ho", "Arijit Singh"), -0.4) }
        assertTrue(s.topMoods().isNotEmpty())
        val seed = t("seed", "Channa Mereya", "Arijit Singh")
        val e = RadioEngine({ _, _ -> RadioPage(emptyList(), null) }, { emptyList() }, { true }, NoFeedback())
        val session = e.sessionFor(seed)
        e.nextBatch(session, listOf(seed), 1)
        repeat(20) { e.onSkipped(session, t("x$it", "Some Song $it", "Arijit Singh")) }
        assertEquals("hi", session.profile.dominant())
    }

    @Test fun recentlyPlayedOnlyFillsIn() = runBlocking {
        val seed = t("seed", "Channa Mereya", "Arijit Singh")
        val page = listOf(t("r1", "Tum Hi Ho", "Arijit Singh"), t("f1", "Kabira", "Pritam"))
        val fb = object : RadioFeedback {
            override suspend fun rejectedTrackIds() = emptySet<String>()
            override suspend fun blockedArtistKeys() = emptySet<String>()
            override suspend fun recentlyPlayedIds() = setOf("r1")
            override suspend fun artistLanguages(artistKeys: Collection<String>) = emptyMap<String, String>()
        }
        val e = RadioEngine({ _, _ -> RadioPage(page, null) }, { emptyList() }, { true }, fb)
        val out = e.nextBatch(e.sessionFor(seed), listOf(seed), 10).map { it.id }
        assertEquals(listOf("f1", "r1"), out)
    }

    @Test fun batchIsFastWithALongQueue() = runBlocking {
        // Regression: ranking once compiled regexes per comparison and froze the UI.
        val seed = t("seed", "Channa Mereya (From \"Ae Dil Hai Mushkil\")", "Arijit Singh")
        val queue = listOf(seed) + (1..300).map { t("q$it", "Queued Song $it | Film $it | Actor $it | Arijit Singh", "T-Series", 180_000L + it * 1_000L) }
        val page = (1..60).map { t("c$it", "Candidate $it (Official Video) | Singer $it", "Label $it", 200_000L + it * 2_000L) }
        val e = RadioEngine({ _, _ -> RadioPage(page, null) }, { emptyList() }, { true }, NoFeedback())
        e.nextBatch(e.sessionFor(seed), queue, 10) // warm up
        val e2 = RadioEngine({ _, _ -> RadioPage(page, null) }, { emptyList() }, { true }, NoFeedback())
        val started = System.nanoTime()
        e2.nextBatch(e2.sessionFor(seed), queue, 10)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("took ${ms}ms", ms < 400)
    }

    @Test fun neverRunsDryWhenEverythingIsSkipped() = runBlocking {
        val seed = t("seed", "Channa Mereya", "Arijit Singh")
        // Each radio has 3 songs by the same 2 artists; every served song gets skipped.
        var n = 0
        val e = RadioEngine(
            fetchRadio = { id, cursor -> if (cursor != null) RadioPage(emptyList(), null) else RadioPage((1..3).map { t("$id-${n++}", "Song $n from $id", if (n % 2 == 0) "A" else "B", 150_000L + n * 9_000L) }, null) },
            fetchRelated = { emptyList() }, isMusic = { true }, feedback = NoFeedback(),
        )
        val s = e.sessionFor(seed)
        val queue = mutableListOf<PlayableItem>(seed)
        repeat(6) {
            val batch = e.nextBatch(s, queue, 3)
            assertTrue("batch ${it + 1} empty", batch.isNotEmpty())
            queue += batch
            batch.dropLast(1).forEach { b -> e.onSkipped(s, b) }
        }
    }

    private class NoFeedback : RadioFeedback {
        override suspend fun rejectedTrackIds() = emptySet<String>()
        override suspend fun blockedArtistKeys() = emptySet<String>()
        override suspend fun recentlyPlayedIds() = emptySet<String>()
        override suspend fun artistLanguages(artistKeys: Collection<String>) = emptyMap<String, String>()
    }

    @Test fun engineRanksByVibeAndDropsVariantsAndDuplicates() = runBlocking {
        val seed = t("seed", "Channa Mereya", "Arijit Singh")
        val page = listOf(
            t("p1", "Party All Night", "Yo Yo Honey Singh"),
            t("s1", "Tujhe Bhula Diya", "Vishal Mishra"),
            t("dup", "Channa Mereya (Lyrics)", "T-Series"),
            t("slow", "Agar Tum Saath Ho (Slowed + Reverb)", "Lofi Beats"),
            t("s2", "Tum Hi Ho", "Arijit Singh"),
            t("s2b", "Tum Hi Ho (Official Video) | Arijit Singh", "T-Series"),
            t("p2", "Dhol Party Dance", "DJ"),
        )
        val e = RadioEngine(
            fetchRadio = { _, _ -> RadioPage(page, null) },
            fetchRelated = { emptyList() },
            isMusic = { true },
            feedback = NoFeedback(),
        )
        val out = e.nextBatch(e.sessionFor(seed), listOf(seed), 10).map { it.id }
        assertFalse("dup" in out)
        assertFalse("slow" in out)
        assertEquals(1, out.count { it == "s2" || it == "s2b" })
        // Sad/romantic ones come before the party tracks.
        assertTrue(out.toString(), out.indexOf("s1") >= 0 && out.indexOf("s1") < out.indexOf("p2"))
    }
}
