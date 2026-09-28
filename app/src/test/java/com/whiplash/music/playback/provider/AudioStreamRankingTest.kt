package com.whiplash.music.playback.provider

import com.whiplash.music.playback.provider.AudioStreamRanking.Candidate
import com.whiplash.music.playback.provider.AudioStreamRanking.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioStreamRankingTest {

    private fun c(name: String, kind: Kind, progressive: Boolean = true, hasUrl: Boolean = true) =
        Candidate(name, kind, progressive, hasUrl)

    private fun pool(vararg cs: Candidate<String>) = AudioStreamRanking.preferredPool(cs.toList())

    @Test
    fun originalBeatsDubbedAndDescribed() {
        assertEquals(
            listOf("orig-opus", "orig-aac"),
            pool(c("dub-es", Kind.DUBBED), c("orig-opus", Kind.ORIGINAL), c("desc", Kind.DESCRIPTIVE), c("orig-aac", Kind.ORIGINAL)),
        )
    }

    @Test
    fun ordinaryVideoWithOnlyUnlabelledAudioIsKept() {
        // The common case: a video (lyrics / music video) with just its own audio.
        assertEquals(listOf("a", "b"), pool(c("a", Kind.UNLABELLED), c("b", Kind.UNLABELLED)))
    }

    @Test
    fun onlyDubbedStillPlays() {
        assertEquals(listOf("dub"), pool(c("dub", Kind.DUBBED), c("desc", Kind.DESCRIPTIVE)))
    }

    @Test
    fun onlyDescribedStillPlays() {
        assertEquals(listOf("desc"), pool(c("desc", Kind.DESCRIPTIVE)))
    }

    @Test
    fun plainFilesBeatManifestsButManifestsStillPlayIfAlone() {
        assertEquals(listOf("file"), pool(c("dash", Kind.ORIGINAL, progressive = false), c("file", Kind.ORIGINAL)))
        assertEquals(listOf("dash"), pool(c("dash", Kind.ORIGINAL, progressive = false)))
    }

    @Test
    fun streamsWithoutUrlAreDroppedUnlessNothingElse() {
        assertEquals(listOf("ok"), pool(c("nourl", Kind.ORIGINAL, hasUrl = false), c("ok", Kind.DUBBED)))
        assertEquals(listOf("nourl"), pool(c("nourl", Kind.ORIGINAL, hasUrl = false)))
    }

    @Test
    fun neverEmptiesANonEmptyList() {
        val kinds = Kind.entries
        val rnd = kotlin.random.Random(3)
        repeat(2_000) {
            val list = List(rnd.nextInt(1, 8)) { i ->
                c("s$i", kinds.random(rnd), progressive = rnd.nextBoolean(), hasUrl = rnd.nextBoolean())
            }
            val p = AudioStreamRanking.preferredPool(list)
            assertTrue("empty pool for $list", p.isNotEmpty())
            assertTrue(list.map { it.stream }.containsAll(p))
        }
        assertTrue(AudioStreamRanking.preferredPool(emptyList<Candidate<String>>()).isEmpty())
    }
}
