package com.whiplash.music.playback.provider.lrclib

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for a real bug: [LrcLibProvider.fetchBySearch] used to
 * return `parseTrackJson(array.getJSONObject(0))` — the first result of
 * LRCLIB's *fuzzy* `/api/search` endpoint — with no check that the candidate
 * actually corresponded to the track being played, and without even passing
 * the track's duration in. Whenever the exact `/api/get` lookup 404'd (common,
 * because YouTube titles carry suffixes like `(From "Ikkis")`), the player
 * would confidently display a completely different song's lyrics, scrolling in
 * sync, with nothing indicating they were wrong. That silently violated the
 * app's "lyrics are never fabricated / honest unavailable state" guarantee.
 *
 * Instrumented rather than a plain JVM unit test because `org.json` is a
 * framework-provided API: under a stubbed android.jar it throws
 * "not mocked", so these must run on a real device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class LrcLibProviderMatchingInstrumentedTest {

    private val provider = LrcLibProvider(OkHttpClient())

    private fun candidate(track: String, artist: String, durationSeconds: Double): JSONObject =
        JSONObject().apply {
            put("trackName", track)
            put("artistName", artist)
            put("duration", durationSeconds)
            put("plainLyrics", "lyrics for $track")
        }

    /**
     * The exact shape of the old bug: an unrelated song sits at index 0. The
     * old code returned it; the fix must reject it and pick the real match.
     */
    @Test
    fun picksTheGenuineMatch_notMerelyTheFirstFuzzyResult() {
        val results = JSONArray().apply {
            put(candidate("Some Unrelated Song", "Another Artist", 200.0))
            put(candidate("Believer", "Imagine Dragons", 204.0))
        }

        val best = provider.bestMatchingCandidate(
            array = results,
            title = "Believer",
            artist = "Imagine Dragons",
            durationMs = 204_000L,
        )

        assertEquals("Believer", best?.optString("trackName"))
        assertEquals("Imagine Dragons", best?.optString("artistName"))
    }

    /** No candidate corresponds at all -> null, so the UI shows an honest "unavailable". */
    @Test
    fun returnsNull_whenNoCandidateCorresponds() {
        val results = JSONArray().apply {
            put(candidate("Totally Different Song", "Someone Else", 180.0))
            put(candidate("Another Wrong One", "Nobody", 190.0))
        }

        val best = provider.bestMatchingCandidate(
            array = results,
            title = "Believer",
            artist = "Imagine Dragons",
            durationMs = 204_000L,
        )

        assertNull(best)
    }

    /**
     * A right-titled, right-artist candidate whose duration is wildly off is a
     * different recording (a remix, a live cut, a full album upload) and its
     * timestamps would not line up, so it must be rejected.
     */
    @Test
    fun rejectsCandidateWithWildlyDifferentDuration() {
        val results = JSONArray().apply {
            put(candidate("Believer", "Imagine Dragons", 3600.0)) // hour-long upload
        }

        val best = provider.bestMatchingCandidate(
            array = results,
            title = "Believer",
            artist = "Imagine Dragons",
            durationMs = 204_000L,
        )

        assertNull(best)
    }

    /**
     * Real-world title noise must still match: YouTube titles carry
     * parenthesised qualifiers that LRCLIB's own titles don't.
     */
    @Test
    fun matchesThroughYoutubeStyleTitleNoise() {
        val results = JSONArray().apply {
            put(candidate("Channa Mereya", "Arijit Singh", 289.0))
        }

        val best = provider.bestMatchingCandidate(
            array = results,
            title = "Channa Mereya (From \"Ae Dil Hai Mushkil\") [Official Video]",
            artist = "Arijit Singh, Pritam",
            durationMs = 289_000L,
        )

        assertEquals("Channa Mereya", best?.optString("trackName"))
    }

    /**
     * A YouTube track is often credited to several artists while LRCLIB lists
     * one; sharing any single artist must be enough.
     */
    @Test
    fun matchesWhenOnlyOneOfSeveralCreditedArtistsOverlaps() {
        val results = JSONArray().apply {
            put(candidate("Gehra Hua", "Arijit Singh", 240.0))
        }

        val best = provider.bestMatchingCandidate(
            array = results,
            title = "Gehra Hua",
            artist = "Shashwat Sachdev, Arijit Singh, Armaan Malik",
            durationMs = 240_000L,
        )

        assertEquals("Gehra Hua", best?.optString("trackName"))
    }

    /** Of several genuine matches, the closest duration wins. */
    @Test
    fun prefersTheClosestDurationAmongRealMatches() {
        val results = JSONArray().apply {
            put(candidate("Believer", "Imagine Dragons", 208.0)) // +4s
            put(candidate("Believer", "Imagine Dragons", 204.0)) // exact
        }

        val best = provider.bestMatchingCandidate(
            array = results,
            title = "Believer",
            artist = "Imagine Dragons",
            durationMs = 204_000L,
        )

        assertEquals(204.0, best!!.optDouble("duration"), 0.001)
    }

    /**
     * Wave-2 regression: a short title must not substring-match an unrelated
     * longer one. "Go" is a substring of "Let It Go", and with a same-ish
     * artist and a duration inside the tolerance the old containment check
     * accepted it — putting the wrong song's lyrics on screen.
     */
    @Test
    fun rejectsUnrelatedLongerTitleThatMerelyContainsAShortTitle() {
        val results = JSONArray().apply {
            put(candidate("Let It Go", "Idina Menzel", 202.0))
        }

        val best = provider.bestMatchingCandidate(
            array = results,
            title = "Go",
            artist = "Idina Menzel",
            durationMs = 200_000L,
        )

        assertNull(best)
    }

    /** Same class: "One" must not match "Someone" via bare substring containment. */
    @Test
    fun rejectsSubwordSubstringMatch() {
        val results = JSONArray().apply {
            put(candidate("Someone", "Some Artist", 200.0))
        }

        val best = provider.bestMatchingCandidate(
            array = results,
            title = "One",
            artist = "Some Artist",
            durationMs = 200_000L,
        )

        assertNull(best)
    }

    /**
     * The tightening must not break the legitimate case it exists for: a real
     * whole-word title extension still matches.
     */
    @Test
    fun stillMatchesLegitimateWholeWordTitleExtension() {
        val results = JSONArray().apply {
            put(candidate("Channa Mereya", "Arijit Singh", 289.0))
        }

        val best = provider.bestMatchingCandidate(
            array = results,
            title = "Channa Mereya Unplugged",
            artist = "Arijit Singh",
            durationMs = 289_000L,
        )

        assertEquals("Channa Mereya", best?.optString("trackName"))
    }

    /** An exactly-matching title outranks a containment match with a closer duration. */
    @Test
    fun prefersExactTitleOverContainmentMatchEvenWithWorseDuration() {
        val results = JSONArray().apply {
            put(candidate("Believer Acoustic Version", "Imagine Dragons", 204.0)) // exact duration, inexact title
            put(candidate("Believer", "Imagine Dragons", 207.0))                   // exact title, +3s
        }

        val best = provider.bestMatchingCandidate(
            array = results,
            title = "Believer",
            artist = "Imagine Dragons",
            durationMs = 204_000L,
        )

        assertEquals("Believer", best?.optString("trackName"))
    }
}
