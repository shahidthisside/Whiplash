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

    // ── 3.2 bandit ─────────────────────────────────────────────────────
    @Test fun banditLearnsWhichSourceWorks() {
        val b = SourceBandit(Random(7))
        repeat(40) { b.reward(RadioSource.RELATED, kept = true); b.reward(RadioSource.KEPT, kept = false) }
        val picks = (1..200).map { b.choose(setOf(RadioSource.KEPT, RadioSource.RELATED)) }
        assertTrue(picks.count { it == RadioSource.RELATED } > 180)
        assertNull(b.choose(emptySet()))
    }

    @Test fun banditStillExploresAndPersists() {
        val b = SourceBandit(Random(3))
        repeat(5) { b.reward(RadioSource.KEPT, kept = true) }
        val picks = (1..300).map { b.choose(setOf(RadioSource.KEPT, RadioSource.SAMPLED)) }
        assertTrue(picks.count { it == RadioSource.SAMPLED } in 5..150)
        val copy = SourceBandit().apply { load(JSONObject(b.toJson().toString())) }
        assertEquals(b.mean(RadioSource.KEPT), copy.mean(RadioSource.KEPT), 1e-9)
    }

    // ── 3.3 ranker ─────────────────────────────────────────────────────
    @Test fun rankerLearnsAFeatureThatPredictsKeeps() {
        val r = Ranker()
        val rnd = Random(1)
        fun x(audio: Boolean) = DoubleArray(Features.COUNT) { 0.5 }.also { it[Features.AUDIO] = if (audio) 1.0 else 0.0; it[Features.RANK] = rnd.nextDouble() }
        val before = r.weights()[Features.AUDIO]
        repeat(600) { r.update(x(true), kept = true); r.update(x(false), kept = false) }
        assertTrue(r.weights()[Features.AUDIO] > before + 0.5)
        assertTrue(r.predict(x(true)) > r.predict(x(false)) + 0.2)
        assertEquals(0.6, r.trust(), 1e-9)
        val copy = Ranker().apply { load(JSONObject(r.toJson().toString())) }
        assertEquals(r.predict(x(true)), copy.predict(x(true).also { it[Features.RANK] = 0.3 }.let { x -> x }), 0.5)
        assertEquals(r.examples, copy.examples)
    }

    @Test fun rankerStartsAtHandWeightsAndIgnoresBadState() {
        val r = Ranker()
        assertEquals(0.0, r.trust(), 1e-9)
        r.load(JSONObject("{\"w\":[1,2],\"b\":0,\"n\":5}"))
        assertEquals(0, r.examples)
        assertEquals(Features.HAND.toList(), r.weights().toList())
    }

    // ── 3.4 time of day ────────────────────────────────────────────────
    @Test fun learnsCalmNightsAndLoudMornings() {
        val nights = (0 until 8).map { play("Unknown", it * day + 1 * hour, title = "Tanha Dil | Sad Song") }
        val mornings = (0 until 8).map { play("Unknown", it * day + 8 * hour, title = "Party Night Dance Song") }
        val e = EnergyByTime.from(nights + mornings, utc)
        val night = e.preferred(DayPart.NIGHT)!!
        val morning = e.preferred(DayPart.MORNING)!!
        assertTrue("night=$night morning=$morning", morning > night + 0.2)
        assertTrue(e.fit(night.toFloat(), 2 * hour, utc) > e.fit(morning.toFloat(), 2 * hour, utc))
        assertEquals(0.5, e.fit(0.9f, 14 * hour, utc), 1e-9) // afternoon: no data
    }

    // ── 3.5 metrics ────────────────────────────────────────────────────
    @Test fun metricsPerSourceAndDrift() {
        val plays = listOf(
            play("Seed", 0, lang = "hi", id = "s"),
            play("A", 1, origin = "AUTOPLAY:SEED", seed = "s", lang = "hi"),
            play("B", 2, origin = "AUTOPLAY:SEED", seed = "s", lang = "pa"), // same family: not drift
            play("C", 3, origin = "AUTOPLAY:RELATED", seed = "s", lang = "en", completed = false, skipped = true),
            play("D", 4, origin = "AUTOPLAY", seed = "s", lang = null, completed = false),
        )
        val m = RadioMetrics.from(plays)
        assertEquals(4, m.plays)
        assertEquals(0.25, m.skipRate, 1e-9)
        assertEquals(0.5, m.completionRate, 1e-9)
        assertEquals(1.0 / 3, m.languageDrift, 1e-9)
        assertEquals(2 to 1.0, m.bySource["SEED"])
        assertEquals(1 to 0.0, m.bySource["RELATED"])
        assertEquals(1 to 1.0, m.bySource["?"])
    }

    // ── wiring ─────────────────────────────────────────────────────────
    private class MemStore : LearnedStore {
        var json: String? = null
        override var generation = 0
        override fun load() = json
        override fun save(json: String) { this.json = json }
    }

    private class Feedback(val plays: List<PastPlay>) : RadioFeedback {
        override suspend fun rejectedTrackIds() = emptySet<String>()
        override suspend fun blockedArtistKeys() = emptySet<String>()
        override suspend fun recentlyPlayedIds() = emptySet<String>()
        override suspend fun artistLanguages(artistKeys: Collection<String>) = emptyMap<String, String>()
        override suspend fun history() = plays
    }

    @Test fun coListenedArtistMovesUpTheBatch() = runBlocking {
        val seed = t("seed", "Some Song", "Seed Artist")
        // The listener always plays Friend Artist right after Seed Artist.
        val plays = (0 until 8).flatMap { listOf(play("Seed Artist", it * day), play("Friend Artist", it * day + 60_000)) }
        val page = listOf(t("x1", "Track One", "Stranger One"), t("x2", "Track Two", "Stranger Two"), t("f", "Track Three", "Friend Artist"))
        val e = RadioEngine({ _, c -> RadioPage(if (c == null) page else emptyList(), null) }, { emptyList() }, { true }, Feedback(plays), random = Random(1))
        val batch = e.nextBatch(e.sessionFor(seed), listOf(seed), 3)
        assertEquals("f", batch.first().id)
        assertEquals(RadioSource.SEED, e.sourceOf("f"))
    }

    @Test fun learningIsSavedAndForgotten() = runBlocking {
        val store = MemStore()
        val seed = t("seed", "Some Song", "Seed Artist")
        val page = (1..6).map { t("p$it", "Track $it", "Artist $it", 180_000L + it * 7_000L) }
        val e = RadioEngine({ _, c -> RadioPage(if (c == null) page else emptyList(), null) }, { emptyList() }, { true }, Feedback(emptyList()), store = store)
        val s = e.sessionFor(seed)
        val batch = e.nextBatch(s, listOf(seed), 4)
        batch.forEach { e.onKept(s, it) }
        e.nextBatch(s, listOf(seed) + batch, 2)
        val saved = JSONObject(store.json!!)
        assertEquals(4, saved.getJSONObject("ranker").getInt("n"))
        assertNotNull(saved.getJSONObject("coListen").optJSONObject("links"))
        // Clearing history bumps the generation: a fresh engine state, no examples.
        store.generation++
        store.json = null
        val s2 = e.sessionFor(t("other", "Other", "Other Artist"))
        e.nextBatch(s2, emptyList(), 1)
        assertEquals(0, JSONObject(store.json!!).getJSONObject("ranker").getInt("n"))
    }
}
