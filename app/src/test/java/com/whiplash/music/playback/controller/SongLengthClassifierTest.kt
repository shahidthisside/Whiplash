package com.whiplash.music.playback.controller

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [classifySongLength] — the title-keyword classifier that keeps
 * autoplay's queue-extension from mixing a normal single song's
 * recommendations with mashups/medleys or full-album/long-form uploads
 * (see its own doc comment for the full rationale).
 *
 * Specifically covers a real, on-device-confirmed bug found via manual
 * autoplay testing (playing "Barsaat" by Banjaare and Roni twice,
 * inspecting the resulting queue extensions): a mashup title using the
 * Unicode multiplication sign "×" (U+00D7) as its separator was
 * incorrectly classified as SINGLE and recommended alongside normal
 * songs, because the mashup regex previously only matched the plain
 * ASCII letter "x" — RegexOption.IGNORE_CASE affects letter casing only,
 * not Unicode look-alike characters.
 */
class SongLengthClassifierTest {

    @Test
    fun `real mashup title with Unicode multiplication sign is classified as MASHUP`() {
        // The exact real-world title that slipped through before the fix.
        val title = "Jo Tum Mere Ho × Jhol × Samjho Na × Sahiba × Pal Pal × Bairan × Majboor × Finding Her |Lofi by Nishu"
        assertEquals(SongLengthClass.MASHUP, classifySongLength(title))
    }

    @Test
    fun `mashup title with ASCII x separator is classified as MASHUP`() {
        assertEquals(SongLengthClass.MASHUP, classifySongLength("Kesariya x Tum Hi Ho Mashup"))
    }

    @Test
    fun `title containing the word mashup is classified as MASHUP`() {
        assertEquals(SongLengthClass.MASHUP, classifySongLength("Bollywood Romantic Mashup 2025"))
    }

    @Test
    fun `title containing medley is classified as MASHUP`() {
        assertEquals(SongLengthClass.MASHUP, classifySongLength("90s Love Medley"))
    }

    @Test
    fun `vs separator is classified as MASHUP`() {
        assertEquals(SongLengthClass.MASHUP, classifySongLength("Arijit Singh vs Shreya Ghoshal"))
    }

    @Test
    fun `normal single song title is classified as SINGLE`() {
        assertEquals(SongLengthClass.SINGLE, classifySongLength("Barsaat"))
    }

    @Test
    fun `real Bollywood single song title with pipe-separated credits is NOT misclassified as a compilation`() {
        // The specific false positive already documented and avoided by
        // this classifier's own design — a genuine single song crediting
        // its cast/singers/lyricist with pipe separators, not a
        // compilation. Confirms the fix for the "×" bug didn't introduce
        // a new false positive on ordinary titles containing "x" as part
        // of a normal word (there is no standalone " x " in this title,
        // so this also guards against over-matching within words).
        val title = "JAWAN: Chaleya (Hindi) | Shah Rukh Khan | Nayanthara | Atlee | Anirudh | Arijit S, Shilpa R | Kumaar"
        assertEquals(SongLengthClass.SINGLE, classifySongLength(title))
    }

    @Test
    fun `full album keyword is classified as LONG_FORM`() {
        assertEquals(SongLengthClass.LONG_FORM, classifySongLength("Arijit Singh Full Album Jukebox"))
    }

    @Test
    fun `non stop keyword is classified as LONG_FORM`() {
        assertEquals(SongLengthClass.LONG_FORM, classifySongLength("Punjabi Nonstop Songs 2025"))
    }

    @Test
    fun `lofi mix keyword is classified as LONG_FORM`() {
        assertEquals(SongLengthClass.LONG_FORM, classifySongLength("Bollywood Lofi Mix 2025"))
    }

    @Test
    fun `real multi-artist compilation ending in bare MIX is classified as LONG_FORM`() {
        // The exact real-world title that slipped through before this
        // fix — confirmed on-device at 16:19 duration with multiple
        // different artists explicitly credited together in the title.
        val title = "Die With A Smile - Lady Gaga, Bruno Mars (Lyrics) ZAYN, Ed Sheeran,... MIX"
        assertEquals(SongLengthClass.LONG_FORM, classifySongLength(title))
    }

    @Test
    fun `real multi-artist compilation with a bare year-tagged Hits is classified as LONG_FORM`() {
        // Confirmed on-device at 123:45 (over two hours) — uploaded by a
        // channel literally named "Sunset Playlist and Sound View".
        val title = "Spotify Pop Hits 2025 Lady Gaga, Bruno Mars, Ed Sheeran, Billie Eilish, Miley Cyrus, Tate McRae #1"
        assertEquals(SongLengthClass.LONG_FORM, classifySongLength(title))
    }

    @Test
    fun `real compilation with Spanish ad-free branding is classified as LONG_FORM`() {
        // Confirmed on-device at 74:57.
        val title = "Musica Pop en Ingles 2026 Melhores Musicas Internacionais 2026 Canciones Pop Sin Anuncios"
        assertEquals(SongLengthClass.LONG_FORM, classifySongLength(title))
    }

    @Test
    fun `legitimate single-track Extended Mix title is NOT misclassified as LONG_FORM`() {
        // Real, common single-track convention (confirmed via research:
        // "(Extended Mix)"/"(Radio Mix)"/"(Club Mix)"/"(Original Mix)" are
        // standard genuine single-song title suffixes in electronic/pop
        // music, e.g. real Beatport/Spotify tracks use this exact
        // convention) — the bare-trailing-"mix" pattern must NOT catch
        // these, since they end with a closing parenthesis after a
        // qualifying word, not with the literal bare word "mix" itself.
        assertEquals(SongLengthClass.SINGLE, classifySongLength("Faded (Original Mix)"))
        assertEquals(SongLengthClass.SINGLE, classifySongLength("Blinding Lights (Extended Mix)"))
        assertEquals(SongLengthClass.SINGLE, classifySongLength("Some Song (Radio Mix)"))
        assertEquals(SongLengthClass.SINGLE, classifySongLength("Some Song (Club Mix)"))
    }

    @Test
    fun `a title matching both LONG_FORM and MASHUP patterns resolves to LONG_FORM (checked first) but is still excluded from a SINGLE seed's recommendations either way`() {
        // classifySongLength checks LONG_FORM_KEYWORDS before
        // MASHUP_KEYWORDS and returns on the first match, so a title
        // matching both patterns (e.g. ending in bare "Mix" AND
        // containing a "vs" separator) resolves to LONG_FORM rather than
        // MASHUP. This is a deliberately don't-care distinction for the
        // actual autoplay filtering that consumes this result: it only
        // ever compares a candidate's class against the seed's own class
        // (see PlaybackController.maybeExtendQueueWithRecommendations),
        // so a normal SINGLE seed excludes both LONG_FORM and MASHUP
        // candidates equally regardless of which of the two a
        // both-patterns-matching title happens to land on.
        val title = "Arijit Singh vs Shreya Ghoshal Mix"
        assertEquals(SongLengthClass.LONG_FORM, classifySongLength(title))
    }

    @Test
    fun `a generic vibes-branded title with no explicit compilation keyword is NOT caught (known gap)`() {
        // Real, on-device-confirmed gap: this exact title (a genuine
        // 15:35 lofi compilation, confirmed by inspecting its real
        // duration in the app) has no explicit compilation keyword at
        // all, and "vibes" alone isn't a safe keyword to add since real
        // songs are legitimately titled "Midnight Vibes" too. This test
        // documents the known limitation rather than papering over it —
        // classifySongLength is deliberately keyword-only (no duration
        // heuristic, to avoid misclassifying long single songs like
        // qawwali), so a title with no explicit signal at all is
        // expected to fall through to SINGLE.
        val title = "MIDNIGHT VIBES || बैरण song #tredingsong #viralsongs"
        assertEquals(SongLengthClass.SINGLE, classifySongLength(title))
    }

    @Test
    fun `a generic mood-city-branded compilation title with no explicit keyword is NOT caught (same known gap)`() {
        // Second confirmed instance of the same accepted gap, found on a
        // third independent "Blinding Lights" autoplay run — confirmed
        // on-device at 70:48. Purely aesthetic/decade/city branding, no
        // explicit compilation keyword, and none of those words (city,
        // pop, midnight, highway, memories) is safe to add on its own
        // for the same reason MIDNIGHT VIBES isn't above.
        val title = "Japanese City Pop 80s – Tokyo Friday Midnight | Bayside Highway & Neon Memories"
        assertEquals(SongLengthClass.SINGLE, classifySongLength(title))
    }
}
