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
                    ?: fetchBySearch(title, artist)
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

    /** `/api/search` — a looser fallback when the exact lookup finds nothing (e.g. duration mismatch). */
    private fun fetchBySearch(title: String, artist: String): LyricsResult? {
        val url = "$BASE_URL/api/search?track_name=${encode(title)}&artist_name=${encode(artist)}"
        val response = execute(url)
        val body = response.use { it.body?.string() } ?: return null
        if (!response.isSuccessful) return null
        val array = JSONArray(body)
        if (array.length() == 0) return null
        return parseTrackJson(array.getJSONObject(0))
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
    }
}
