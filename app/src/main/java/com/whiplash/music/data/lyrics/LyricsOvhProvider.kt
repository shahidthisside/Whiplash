package com.whiplash.music.data.lyrics

import android.util.Log
import com.whiplash.music.domain.model.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * lyrics.ovh — a free, open lyrics API with no key or account. It returns
 * plain (unsynced) lyrics looked up by exact artist + title, so it is used
 * as a fallback when LRCLIB has nothing. Only the title and artist name are sent.
 */
class LyricsOvhProvider(client: OkHttpClient) : LyricsProvider {

    override val id = LyricsSourcePreference.LYRICS_OVH.name
    override val displayName = "lyrics.ovh"
    override val supportsSync = false

    // The service is slow (5–10 s is normal), so it gets its own longer timeout.
    private val http = client.newBuilder().callTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    override suspend fun getLyrics(title: String, artist: String, durationMs: Long): LyricsResult =
        withContext(Dispatchers.IO) {
            val cleanTitle = cleanTitle(title)
            val cleanArtist = primaryArtist(artist)
            if (cleanTitle.isBlank() || cleanArtist.isBlank()) return@withContext LyricsResult.Unavailable
            val url = "$BASE_URL/${encode(cleanArtist)}/${encode(cleanTitle)}"
            try {
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                http.newCall(request).execute().use { response ->
                    if (response.code == 404) return@withContext LyricsResult.Unavailable
                    if (!response.isSuccessful) return@withContext LyricsResult.Error("lyrics.ovh error ${response.code}")
                    val body = response.body?.string().orEmpty()
                    val text = normalizeLyrics(JSONObject(body).optString("lyrics", ""))
                    if (text.isBlank()) LyricsResult.Unavailable else LyricsResult.Plain(text)
                }
            } catch (e: IOException) {
                Log.w(TAG, "lyrics.ovh lookup failed: ${e.message}")
                LyricsResult.Error(e.message ?: "Network error")
            } catch (e: org.json.JSONException) {
                LyricsResult.Error("Couldn't read lyrics response")
            }
        }

    companion object {
        private const val TAG = "LyricsOvhProvider"
        private const val BASE_URL = "https://api.lyrics.ovh/v1"
        private const val USER_AGENT = "Whiplash Android Music Player"

        private val BRACKETS = Regex("""[(\[][^)\]]*[)\]]""")
        private val FEAT = Regex("""\s+(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
        private val DASH_SUFFIX = Regex("""\s+-\s+(official|lyric|lyrics|audio|video|remaster).*$""", RegexOption.IGNORE_CASE)

        /** Drops "(Official Video)", "[Remastered]", "feat. X" and similar suffixes. Visible for testing. */
        internal fun cleanTitle(title: String): String = title
            .replace(BRACKETS, " ")
            .replace(DASH_SUFFIX, "")
            .replace(FEAT, "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        /** First credited artist only: the API matches one exact name. Visible for testing. */
        internal fun primaryArtist(artist: String): String = artist
            .split(',', '&', '/', ';')
            .first()
            .replace(FEAT, "")
            .replace(Regex("""\s+-\s+Topic$""", RegexOption.IGNORE_CASE), "")
            .trim()

        /** CRLF → LF, drop the French header line the API sometimes prepends, collapse 3+ blank lines. */
        internal fun normalizeLyrics(raw: String): String = raw
            .replace("\r\n", "\n").replace('\r', '\n')
            .lines()
            .dropWhile { it.isBlank() || it.startsWith("Paroles de la chanson", ignoreCase = true) }
            .joinToString("\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()

        private fun encode(v: String): String = URLEncoder.encode(v, "UTF-8").replace("+", "%20")
    }
}
