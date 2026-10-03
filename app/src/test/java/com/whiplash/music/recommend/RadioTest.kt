// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
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
        val pa = LanguageDetector.detect("Tenu Kinna Pyar Karda Menu", "Unknown")
        assertEquals("pa", pa?.code)
        assertFalse(pa!!.confident)
        assertEquals("hi", LanguageDetector.detect("Tujhe Kitna Chahne Lage Hum", "x")?.code)
        assertEquals("en", LanguageDetector.detect("Love Me Like You Do", "Nobody Known")?.code)
    }

    @Test fun artistBeatsMisleadingTitleWords() {
        // Hindi film songs with English or Punjabi-word titles.
        assertEquals("hi", LanguageDetector.detect("Excuses Tonight Baby", "Arijit Singh")?.code)
        assertEquals("hi", LanguageDetector.detect("Tenu Leke Main Jawanga", "Sonu Nigam")?.code)
        // Punjabi songs with Hindi-looking titles.
        assertEquals("pa", LanguageDetector.detect("Tere Bina Kya Hai Zindagi", "Karan Aujla")?.code)
        // Credited artist in a label upload.
        assertEquals("pa", LanguageDetector.detect("Dawood | PBX 1 | Sidhu Moose Wala", "T-Series")?.code)
        // Bilingual artists give no artist evidence.
        assertNull(LanguageDetector.detect("Naina", "Diljit Dosanjh"))
        assertEquals(LanguageGuess.ARTIST, LanguageDetector.detect("Excuses", "AP Dhillon")?.strength)
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

    @Test fun creditKeysSplitCoCredits() {
        assertEquals(listOf("pritam", "arijitsingh"), RadioRules.creditKeys("Pritam & Arijit Singh"))
        assertEquals(listOf("karanaujla", "ikky"), RadioRules.creditKeys("Karan Aujla x Ikky"))
        assertEquals(listOf("badshah", "yoyohoneysingh"), RadioRules.creditKeys("Badshah feat. Yo Yo Honey Singh"))
        // "and" is not a separator: band names.
        assertEquals(listOf("simonandgarfunkel"), RadioRules.creditKeys("Simon and Garfunkel"))
        assertEquals(listOf("diljitdosanjh"), RadioRules.creditKeys("Diljit Dosanjh - Topic"))
    }

    @Test fun allowanceNeedsRepeatedEvidence() {
        assertEquals(RadioRules.DEFAULT_ALLOWANCE, RadioRules.allowanceFor(0, 0))
        assertEquals(RadioRules.DEFAULT_ALLOWANCE, RadioRules.allowanceFor(1, 0)) // one finish isn't enough
        assertEquals(RadioRules.FAVOURED_ALLOWANCE, RadioRules.allowanceFor(2, 0))
        assertEquals(RadioRules.FAVOURED_ALLOWANCE, RadioRules.allowanceFor(3, 1))
        assertEquals(RadioRules.DEFAULT_ALLOWANCE, RadioRules.allowanceFor(2, 2))
        assertEquals(RadioRules.DISLIKED_ALLOWANCE, RadioRules.allowanceFor(0, 1))
        assertEquals(RadioRules.DISLIKED_ALLOWANCE, RadioRules.allowanceFor(1, 2))
        // Hard ceiling: never more than 2 in a row, even for a favourite.
        assertEquals(2, RadioRules.FAVOURED_ALLOWANCE.maxRun)
    }

    @Test fun favouredArtistGetsMoreButNeverThreeInARow() {
        val batch = (1..8).map { t("a$it", "A song $it", "A") } + listOf(t("b1", "B", "B"), t("c1", "C", "C"), t("d1", "D", "D"))
        val normal = RadioRules.applyArtistCap(batch, emptyList()).placed.map { it.artist }
        val favoured = RadioRules.applyArtistCap(batch, emptyList()) { if (it == "a") RadioRules.FAVOURED_ALLOWANCE else RadioRules.DEFAULT_ALLOWANCE }.placed.map { it.artist }
        assertTrue("$normal vs $favoured", favoured.count { it == "A" } > normal.count { it == "A" })
        for (i in 2 until favoured.size) assertFalse("3 in a row: $favoured", favoured[i] == "A" && favoured[i - 1] == "A" && favoured[i - 2] == "A")
        favoured.windowed(RadioRules.ARTIST_WINDOW).forEach { w -> assertTrue("window $w", w.count { it == "A" } <= 4) }
    }

    @Test fun dislikedArtistNeverBackToBack() {
        val batch = listOf(t("a1", "1", "A"), t("a2", "2", "A"), t("b1", "3", "B"), t("a3", "4", "A"), t("c1", "5", "C"))
        val out = RadioRules.applyArtistCap(batch, emptyList()) { if (it == "a") RadioRules.DISLIKED_ALLOWANCE else RadioRules.DEFAULT_ALLOWANCE }.placed.map { it.artist }
        for (i in 1 until out.size) assertFalse("back to back: $out", out[i] == "A" && out[i - 1] == "A")
    }

    @Test fun coCreditedSongsCountForEachArtist() {
        // Arijit already twice in a row (once co-credited): a third Arijit song must wait.
        val tail = listOf(RadioRules.creditKeys("Arijit Singh"), RadioRules.creditKeys("Pritam & Arijit Singh"))
        val out = RadioRules.applyArtistCapByCredits(listOf(t("x", "X", "Arijit Singh & Shreya Ghoshal"), t("y", "Y", "Atif Aslam")), tail)
        assertEquals("Atif Aslam", out.placed.first().artist)
    }

    @Test fun artistCapCountsQueueTail() {
        val r = RadioRules.applyArtistCap(listOf(t("1", "a", "A"), t("2", "b", "B")), tailArtistKeys = listOf("a", "a"))
        assertEquals("B", r.placed.first().artist)
    }

    @Test fun profileOnlyExcludesOnReliableEvidence() {
        val p = LanguageProfile()
        p.add("pa", 3.0)
        assertEquals("pa", p.dominant())
        assertTrue(p.allows("pa", LanguageGuess.STRONG))
        assertTrue(p.allows(null, 0))
        // Weak or artist-level Hindi evidence never excludes; it only scores lower.
        assertTrue(p.allows("hi", LanguageGuess.WEAK))
        assertTrue(p.allows("hi", LanguageGuess.ARTIST))
        assertTrue(p.penalty("hi", LanguageGuess.WEAK) < p.penalty("hi", LanguageGuess.STRONG))
        // Another language family with reliable evidence is excluded.
        assertFalse(p.allows("ko", LanguageGuess.STRONG))
        // A firmly Punjabi session excludes reliably-Hindi songs too.
        p.add("pa", 2.0)
        assertFalse(p.allows("hi", LanguageGuess.STRONG))
        // The listener chose Hindi as well → mixed session, Hindi welcome.
        p.add("hi", 2.0)
        assertTrue(p.allows("hi", LanguageGuess.STRONG))
        assertEquals(0.0, p.penalty("hi", LanguageGuess.STRONG), 0.0)
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
        // The seed's radio is used up (both pages) before anything else is tried.
        assertEquals(listOf("seed@0", "seed@1"), calls.take(2))
        assertEquals(queue.size, queue.map { it.id }.toSet().size)
    }

    @Test fun engineLoosensAfterFinishesAndTightensAfterSkips() = runBlocking {
        val loved = (1..12).map { t("l$it", "Loved $it", "Loved Artist") }
        val others = (1..12).map { t("o$it", "Other $it", "Other $it") }
        val page = loved.zip(others).flatMap { listOf(it.first, it.second) }
        val e = engine(mapOf("seed" to listOf(page)))
        val s = e.sessionFor(seed)
        val first = e.nextBatch(s, listOf(seed), 6)
        val before = first.count { it.artist == "Loved Artist" }
        // The listener finishes two of their songs.
        val finished = (first.filter { it.artist == "Loved Artist" } + loved).distinct().take(2)
        finished.forEach { e.onKept(s, it) }
        assertEquals(RadioRules.FAVOURED_ALLOWANCE, s.allowance("lovedartist"))
        val second = e.nextBatch(s, (listOf(seed) + first + finished).distinct(), 10)
        val keys = second.map { it.artist }
        for (i in 2 until keys.size) assertFalse("3 in a row: $keys", keys[i] == keys[i - 1] && keys[i] == keys[i - 2])
        assertTrue("before=$before after=$keys", keys.count { it == "Loved Artist" } >= 4)
        // Skipping them now tightens it again.
        second.filter { it.artist == "Loved Artist" }.forEach { e.onSkipped(s, it) }
        assertEquals(RadioRules.DISLIKED_ALLOWANCE, s.allowance("lovedartist"))
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
        // B first; A only as a fallback so the radio doesn't run dry.
        assertEquals("3", e.nextBatch(s, listOf(seed), 10).map { it.id }.first())
    }
}
