package com.whiplash.music.playback.provider.lrclib

import android.util.Log
import com.whiplash.music.domain.model.LyricLine
import com.whiplash.music.domain.model.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Fetches real, live lyrics (plain or LRC-timestamp-synced) from
 * lrclib.net (CLAUDE.md section 20). LRCLIB is a free, open-source, public
 * lyrics database with no API key or registration required — it is not
 * scraped or reverse-engineered, and returns genuine user-contributed
 * lyrics rather than anything fabricated by this app. Only the track title
 * and artist name (already public search metadata, not local library or
 * account data) are sent, matching section 74's data-privacy rules.
 *
 * A track with no matching entry returns [LyricsResult.Unavailable] — a
 * real negative result, not a fake placeholder (section 20: "never
 * fabricate lyrics").
 */
class LrcLibProvider(
    private val client: OkHttpClient,
) {

    suspend fun getLyrics(title: String, artist: String, durationMs: Long): LyricsResult =
        withContext(Dispatchers.IO) {
            try {
                fetchByExactMatch(title, artist, durationMs)
                    ?: fetchBySearch(title, artist, durationMs)
                    ?: LyricsResult.Unavailable
            } catch (e: IOException) {
                Log.w(TAG, "Lyrics lookup failed for '$title' by '$artist': ${e.message}")
                LyricsResult.Error(e.message ?: "Network error")
            } catch (e: org.json.JSONException) {
                Log.w(TAG, "Lyrics response parsing failed for '$title' by '$artist': ${e.message}")
                LyricsResult.Error("Couldn't read lyrics response")
            }
        }

    /** `/api/get` — an exact title+artist(+duration) lookup, LRCLIB's most accurate match mode. */
    private fun fetchByExactMatch(title: String, artist: String, durationMs: Long): LyricsResult? {
        val urlBuilder = StringBuilder(BASE_URL)
            .append("/api/get?track_name=").append(encode(title))
            .append("&artist_name=").append(encode(artist))
        if (durationMs > 0) {
            urlBuilder.append("&duration=").append(durationMs / 1000)
        }
        val response = execute(urlBuilder.toString())
        if (response.code == 404) {
            response.close()
            return null
        }
        val body = response.use { it.body?.string() } ?: return null
        if (!response.isSuccessful) return null
        return parseTrackJson(JSONObject(body))
    }

    /**
     * `/api/search` — a looser fallback when the exact lookup finds nothing
     * (e.g. duration mismatch, or a YouTube title carrying a `(From "…")`
     * suffix the exact endpoint won't match).
     *
     * Real correctness bug this fixes: this used to return
     * `parseTrackJson(array.getJSONObject(0))` — the first result of a
     * *fuzzy* search, with no check that it actually corresponds to the
     * track being played. LRCLIB's search endpoint happily returns loosely
     * related songs, so whenever the exact lookup 404'd (which is common for
     * this app, because YouTube titles routinely carry extra suffixes) the
     * player could confidently display **a completely different song's
     * lyrics**, scrolling in sync, with nothing to indicate they were wrong.
     * That is worse than showing nothing and directly contradicts the
     * "lyrics are never fabricated / honest unavailable state" guarantee.
     *
     * Now every candidate must genuinely correspond on title, on artist
     * (when both sides declare one), and on duration within
     * [DURATION_TOLERANCE_SECONDS]; the closest surviving candidate wins and
     * if none survives this returns null, which surfaces the honest
     * [LyricsResult.Unavailable] instead.
     */
    private fun fetchBySearch(title: String, artist: String, durationMs: Long): LyricsResult? {
        val url = "$BASE_URL/api/search?track_name=${encode(title)}&artist_name=${encode(artist)}"
        val response = execute(url)
        val body = response.use { it.body?.string() } ?: return null
        if (!response.isSuccessful) return null
        val array = JSONArray(body)
        if (array.length() == 0) return null
        val best = bestMatchingCandidate(array, title, artist, durationMs)
        if (best == null) {
            Log.w(TAG, "Discarded ${array.length()} lyrics search result(s) for '$title' by '$artist' — none matched")
            return null
        }
        return parseTrackJson(best)
    }

    /**
     * Picks the search result that genuinely corresponds to the requested
     * track, or null if none does. Visible for testing.
     */
    internal fun bestMatchingCandidate(
        array: JSONArray,
        title: String,
        artist: String,
        durationMs: Long,
    ): JSONObject? {
        val wantTitle = normalizeForMatch(title)
        val wantArtists = splitArtists(artist)
        val wantSeconds = (durationMs / 1000).takeIf { durationMs > 0 }

        var best: JSONObject? = null
        var bestDeltaSeconds = Long.MAX_VALUE
        var bestIsExactTitle = false

        for (index in 0 until array.length()) {
            val candidate = array.optJSONObject(index) ?: continue
            val candidateTitle = normalizeForMatch(candidate.optString("trackName"))
            if (candidateTitle.isEmpty() || wantTitle.isEmpty()) continue
            if (!titlesCorrespond(wantTitle, candidateTitle)) continue

            val candidateArtists = splitArtists(candidate.optString("artistName"))
            if (wantArtists.isNotEmpty() && candidateArtists.isNotEmpty() &&
                !artistsCorrespond(wantArtists, candidateArtists)
            ) {
                continue
            }

            val candidateSeconds = candidate.optDouble("duration", -1.0)
                .takeIf { it > 0.0 }
                ?.toLong()
            val deltaSeconds = if (wantSeconds != null && candidateSeconds != null) {
                kotlin.math.abs(candidateSeconds - wantSeconds)
            } else {
                // No duration to compare on either side: allowed through on
                // title+artist alone, but ranked below anything that did
                // match on duration.
                DURATION_TOLERANCE_SECONDS
            }
            if (deltaSeconds > DURATION_TOLERANCE_SECONDS) continue

            // An exactly-matching title always beats a whole-word containment
            // match, however close the latter's duration happens to be.
            val isExactTitle = candidateTitle == wantTitle
            val betterThanBest = when {
                isExactTitle && !bestIsExactTitle -> true
                !isExactTitle && bestIsExactTitle -> false
                else -> deltaSeconds < bestDeltaSeconds
            }
            if (betterThanBest) {
                best = candidate
                bestDeltaSeconds = deltaSeconds
                bestIsExactTitle = isExactTitle
            }
        }
        return best
    }

    /**
     * Lowercases, drops parenthesised/bracketed qualifiers (`(From "Ikkis")`,
     * `[Official Video]`, `- Lyrical`) and strips punctuation, so the same
     * song titled slightly differently on YouTube and on LRCLIB still
     * matches.
     */
    private fun normalizeForMatch(raw: String): String = raw
        .lowercase()
        .replace(PARENTHETICAL_REGEX, " ")
        .replace(NOISE_WORDS_REGEX, " ")
        .replace(NON_ALPHANUMERIC_REGEX, " ")
        .trim()
        .replace(WHITESPACE_RUN_REGEX, " ")

    /** Splits a possibly multi-artist credit ("A, B & C feat. D") into normalized names. */
    private fun splitArtists(raw: String): List<String> = raw
        .split(',', '&', '/', ';')
        .flatMap { it.split(" feat. ", " ft. ", " featuring ", ignoreCase = true) }
        .map { normalizeForMatch(it) }
        .filter { it.isNotEmpty() }

    /**
     * Whether two normalized titles refer to the same song.
     *
     * Exact equality always wins. Containment is allowed only because YouTube
     * titles routinely carry extra words the LRCLIB entry doesn't, but it is
     * deliberately restricted: the shorter title must be a whole-word run
     * inside the longer one AND be substantial enough to be meaningful on its
     * own. Without that restriction a short title matched any unrelated longer
     * title containing it as a substring — "Go" vs "Let It Go", "One" vs
     * "Someone", "Sun" vs "Sunflower" — which, once artist and duration
     * happened to be close, put the wrong song's lyrics on screen and
     * re-violated the "lyrics are never fabricated" guarantee this matching was
     * added to protect.
     */
    private fun titlesCorrespond(want: String, candidate: String): Boolean {
        if (want == candidate) return true
        val shorter = if (want.length <= candidate.length) want else candidate
        val longer = if (shorter === want) candidate else want
        if (shorter.length < MIN_SUBSTRING_TITLE_CHARS) return false
        // Whole-word containment only, so "go" can't match inside "goodbye"
        // and "one" can't match inside "someone".
        return longer == shorter ||
            longer.startsWith("$shorter ") ||
            longer.endsWith(" $shorter") ||
            longer.contains(" $shorter ")
    }

    /**
     * A YouTube track is often credited to several artists while LRCLIB
     * lists one, so sharing any single artist is enough to correspond.
     */
    private fun artistsCorrespond(want: List<String>, candidate: List<String>): Boolean =
        want.any { wanted ->
            candidate.any { found -> wanted == found || wanted.contains(found) || found.contains(wanted) }
        }

    private fun execute(url: String): okhttp3.Response {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return client.newCall(request).execute()
    }

    private fun parseTrackJson(json: JSONObject): LyricsResult {
        val instrumental = json.optBoolean("instrumental", false)
        val syncedLyrics = json.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
        val plainLyrics = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }

        return when {
            syncedLyrics != null -> {
                val lines = parseLrc(syncedLyrics)
                if (lines.isNotEmpty()) {
                    LyricsResult.Synced(lines)
                } else {
                    plainLyrics?.let { LyricsResult.Plain(it) } ?: LyricsResult.Unavailable
                }
            }
            plainLyrics != null -> LyricsResult.Plain(plainLyrics)
            instrumental -> LyricsResult.Plain("(Instrumental — no lyrics)")
            else -> LyricsResult.Unavailable
        }
    }

    /** Parses standard `[mm:ss.xx] text` LRC lines into ordered, timestamped [LyricLine]s. */
    private fun parseLrc(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (rawLine in lrc.lineSequence()) {
            val match = LRC_LINE_REGEX.find(rawLine) ?: continue
            val minutes = match.groupValues[1].toLongOrNull() ?: continue
            val seconds = match.groupValues[2].toDoubleOrNull() ?: continue
            val text = match.groupValues[3].trim()
            val timestampMs = (minutes * 60_000L) + (seconds * 1000L).toLong()
            lines += LyricLine(timestampMs, text)
        }
        return lines.sortedBy { it.timestampMs }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val TAG = "LrcLibProvider"
        private const val BASE_URL = "https://lrclib.net"
        private const val USER_AGENT = "Whiplash Android Music Player (https://github.com)"

        // Matches "[00:13.18] Some lyric text" — LRCLIB's syncedLyrics format.
        private val LRC_LINE_REGEX = Regex("""^\[(\d+):(\d+(?:\.\d+)?)]\s*(.*)$""")

        /**
         * How far a candidate's duration may differ from the track being
         * played and still be considered the same song. Covers the routine
         * couple-of-seconds difference between a YouTube upload and a
         * studio release without letting an unrelated song through.
         */
        internal const val DURATION_TOLERANCE_SECONDS = 5L

        /**
         * Shortest a title may be and still be accepted as a whole-word
         * containment match rather than an exact one — see [titlesCorrespond].
         * Below this, a title carries too little information to distinguish the
         * real song from an unrelated one that merely contains the same word.
         */
        private const val MIN_SUBSTRING_TITLE_CHARS = 6

        private val PARENTHETICAL_REGEX = Regex("""[(\[][^)\]]*[)\]]""")
        private val NOISE_WORDS_REGEX =
            Regex("""\b(official|video|audio|lyrical|lyrics|full|song|hd|4k|remastered)\b""")
        private val NON_ALPHANUMERIC_REGEX = Regex("""[^a-z0-9]+""")
        private val WHITESPACE_RUN_REGEX = Regex("""\s+""")
    }
}
