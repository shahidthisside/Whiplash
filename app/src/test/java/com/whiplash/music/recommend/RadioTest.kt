package com.whiplash.music.recommend

import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun t(id: String, title: String, artist: String, dur: Long = 200_000L) =
    PlayableItem.YoutubeTrack(id = id, title = title, artist = artist, album = null, artworkUri = null, durationMs = dur)

class LanguageDetectorTest {
    @Test fun nativeScripts() {
        assertEquals("pa", LanguageDetector.detect("ਤੇਰੇ ਬਿਨਾ")?.code)
        assertEquals("hi", LanguageDetector.detect("तुम ही हो")?.code)
        assertEquals("ta", LanguageDetector.detect("வாத்தி கம்மிங்")?.code)
        assertEquals("ko", LanguageDetector.detect("봄날")?.code)
        assertEquals("ja", LanguageDetector.detect("夜に駆ける")?.code)
        assertEquals("ur", LanguageDetector.detect("دل دیاں گلاں")?.code)
        assertTrue(LanguageDetector.detect("ਤੇਰੇ ਬਿਨਾ")!!.confident)
    }

    @Test fun explicitLabelsBeatEverything() {
        assertEquals("pa", LanguageDetector.detect("Do You Know | Latest Punjabi Song", "Diljit Dosanjh")?.code)
        assertEquals("hr", LanguageDetector.detect("Russian Bandana (Music Video)", "VYRL Haryanvi")?.code)
        assertEquals("hi", LanguageDetector.detect("Top 80s Bollywood Hits")?.code)
    }

    @Test fun romanisedMarkersAreWeak() {
        val pa = LanguageDetector.detect("Tenu Kinna Pyar Karda", "Unknown")
        assertEquals("pa", pa?.code)
        assertFalse(pa!!.confident)
        assertEquals("hi", LanguageDetector.detect("Tujhe Kitna Chahne Lage Hum", "x")?.code)
        assertEquals("en", LanguageDetector.detect("Love Me Like You Do", "Ellie Goulding")?.code)
    }

    @Test fun noEvidenceIsNull() {
        assertNull(LanguageDetector.detect("Wavy", "Someone New"))
    }
}

class RadioRulesTest {
    @Test fun artistKeyNormalises() {
        assertEquals(RadioRules.artistKey("Diljit Dosanjh"), RadioRules.artistKey("DILJIT DOSANJH - Topic"))
        assertEquals("diljitdosanjh", RadioRules.artistKey("DiljitDosanjhVEVO"))
    }

    @Test fun retro() {
        assertTrue(RadioRules.isRetro("80's Hindi Evergreen Songs"))
        assertTrue(RadioRules.isRetro("Old Is Gold Jukebox"))
        assertTrue(RadioRules.isRetro("Ek Do Teen (1988)"))
        assertFalse(RadioRules.isRetro("Born to Shine"))
        assertFalse(RadioRules.isRetro("295 (Official Audio)"))
    }

    @Test fun artistCapSpreadsArtists() {
        val batch = listOf(t("1", "a", "A"), t("2", "b", "A"), t("3", "c", "A"), t("4", "d", "B"), t("5", "e", "A"), t("6", "f", "C"))
        val r = RadioRules.applyArtistCap(batch, tailArtistKeys = emptyList())
        val keys = r.placed.map { it.artist }
        for (i in 2 until keys.size) assertFalse("3 in a row: $keys", keys[i] == keys[i - 1] && keys[i] == keys[i - 2])
        keys.windowed(RadioRules.ARTIST_WINDOW).forEach { w -> assertTrue("window $w", w.count { it == "A" } <= RadioRules.MAX_ARTIST_PER_WINDOW) }
        assertEquals(6, r.placed.size + r.deferred.size)
    }

    @Test fun artistCapCountsQueueTail() {
        val r = RadioRules.applyArtistCap(listOf(t("1", "a", "A"), t("2", "b", "B")), tailArtistKeys = listOf("a", "a"))
        assertEquals("B", r.placed.first().artist)
    }

    @Test fun profileDominanceAndMixing() {
        val p = LanguageProfile()
        p.add("pa", 3.0)
        assertEquals("pa", p.dominant())
        assertTrue(p.allows("pa", true))
        assertFalse(p.allows("hi", true))
        assertTrue(p.allows(null, false))
        p.add("hi", 2.0) // the listener also chose Hindi → mixed session
        assertTrue(p.allows("hi", true))
    }
}

class RadioEngineTest {
    private class FakeFeedback(
        val rejected: Set<String> = emptySet(),
        val blocked: Set<String> = emptySet(),
    ) : RadioFeedback {
        override suspend fun rejectedTrackIds() = rejected
        override suspend fun blockedArtistKeys() = blocked
        override suspend fun recentlyPlayedIds() = emptySet<String>()
        override suspend fun artistLanguages(artistKeys: Collection<String>) = emptyMap<String, String>()
    }

    private val seed = t("seed", "Born to Shine | Punjabi Song", "Diljit Dosanjh")

    private fun engine(pages: Map<String, List<List<PlayableItem.YoutubeTrack>>>, feedback: RadioFeedback = FakeFeedback(), calls: MutableList<String> = mutableListOf()) =
        RadioEngine(
            fetchRadio = { id, cursor ->
                calls += "$id@${cursor ?: 0}"
                val list = pages[id].orEmpty()
                val i = (cursor as? Int) ?: 0
                RadioPage(list.getOrElse(i) { emptyList() }, if (i + 1 < list.size) i + 1 else null)
            },
            fetchRelated = { emptyList() },
            isMusic = { true },
            feedback = feedback,
        )

    @Test fun staysAnchoredToSeedAcrossBatches() = runBlocking {
        val calls = mutableListOf<String>()
        val p1 = (1..12).map { t("a$it", "Song $it end", "Artist ${it % 6}", 150_000L + it * 7_000L) }
        val p2 = (13..24).map { t("a$it", "Song $it end", "Artist ${it % 6}", 150_000L + it * 7_000L) }
        val e = engine(mapOf("seed" to listOf(p1, p2)), calls = calls)
        val s = e.sessionFor(seed)
        val queue = mutableListOf<PlayableItem>(seed)
        repeat(2) { queue += e.nextBatch(s, queue, 10) }
        assertEquals(21, queue.size)
        // Every fetch is the seed's radio (paged), never a re-seed from an autoplay pick.
        assertTrue(calls.toString(), calls.all { it.startsWith("seed@") })
        assertEquals(queue.size, queue.map { it.id }.toSet().size)
    }

    @Test fun dropsOtherLanguageRetroAndRejected() = runBlocking {
        val page = listOf(
            t("ok1", "Lover", "Diljit Dosanjh"),
            t("hindi", "Tum Hi Ho | Bollywood Song", "Arijit"),
            t("retro", "80s Punjabi Hits", "Old"),
            t("skipme", "Wavy", "Karan Aujla"),
            t("ok2", "Cheques", "Shubh"),
            t("blockedArtist", "Some", "Spammy Channel"),
        )
        val e = engine(mapOf("seed" to listOf(page)), FakeFeedback(rejected = setOf("skipme"), blocked = setOf("spammychannel")))
        val out = e.nextBatch(e.sessionFor(seed), listOf(seed), 10).map { it.id }
        assertEquals(listOf("ok1", "ok2"), out)
    }

    @Test fun continuesFromKeptSongWhenSeedRadioEnds() = runBlocking {
        val calls = mutableListOf<String>()
        val kept = t("k", "Kept Song", "X")
        val e = engine(mapOf("seed" to listOf(listOf(kept)), "k" to listOf(listOf(t("n1", "New One", "Y")))), calls = calls)
        val s = e.sessionFor(seed)
        val first = e.nextBatch(s, listOf(seed), 10)
        assertEquals(listOf("k"), first.map { it.id })
        e.onKept(s, kept)
        val second = e.nextBatch(s, listOf(seed) + first, 10)
        assertEquals(listOf("n1"), second.map { it.id })
        assertTrue(calls.toString(), calls.contains("k@0"))
    }

    @Test fun sessionSkipsSteerAwayFromArtist() = runBlocking {
        val page = listOf(t("1", "One", "A"), t("2", "Two", "A"), t("3", "Three", "B"))
        val e = engine(mapOf("seed" to listOf(page)))
        val s = e.sessionFor(seed)
        e.onSkipped(s, t("x", "x", "A")); e.onSkipped(s, t("y", "y", "A"))
        assertEquals(listOf("3"), e.nextBatch(s, listOf(seed), 10).map { it.id })
    }
}
