package com.whiplash.music.playback.provider.direct

import android.util.Log
import com.whiplash.music.data.local.entity.ProviderStatus
import com.whiplash.music.domain.model.AudioQuality
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.playback.provider.PlaybackProvider
import com.whiplash.music.playback.provider.ProviderFailure
import com.whiplash.music.playback.provider.ProviderHealthTracker
import com.whiplash.music.playback.provider.ProviderPlayerInfo
import com.whiplash.music.playback.provider.ResolvedStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Second stream source: asks YouTube's player API directly, as the YouTube
 * app for Apple Vision Pro does, with no NewPipe in between.
 *
 * That client is the one YouTube still gives complete, directly playable
 * links to without a proof-of-origin token (NewPipe itself takes its audio
 * from it first). A lookup is one request, two on the first use (a visitor
 * id, kept and reused), against NewPipe's several. It returns the same audio
 * formats, so cached and pinned songs stay valid whichever source served
 * them. If YouTube changes something, it fails over to NewPipe (or the other
 * way round, see Settings → Audio quality → Stream source).
 */
class YoutubeDirectProvider(
    private val http: OkHttpClient,
    private val healthTracker: ProviderHealthTracker,
) : PlaybackProvider {

    override val id: String = PROVIDER_ID
    override val displayName: String = "YouTube direct"

    override fun supports(item: PlayableItem): Boolean = item is PlayableItem.YoutubeTrack

    @Volatile private var visitorData: String? = null
    @Volatile private var visitorFetchedAtMs = 0L
    private val random = SecureRandom()

    override suspend fun getStream(songId: String, quality: AudioQuality, preferredItag: Int?): ResolvedStream =
        withContext(Dispatchers.IO) {
            tracked {
                val cpn = newCpn()
                val json = player(songId, cpn)
                YoutubeDirectParser.resolve(json, songId, quality, preferredItag, cpn, id).also {
                    Log.i(TAG, "$songId: itag ${it.itag} from YouTube direct")
                }
            }
        }

    override suspend fun getPlayerInfo(songId: String): ProviderPlayerInfo = withContext(Dispatchers.IO) {
        tracked { YoutubeDirectParser.playerInfo(player(songId, newCpn()), songId) }
    }

    override suspend fun providerStatus(): ProviderStatus = healthTracker.statusOf(id)

    /** The player response for [videoId]; a refusal that looks like a stale visitor id is retried once with a fresh one. */
    private suspend fun player(videoId: String, cpn: String): JSONObject {
        val first = runInterruptible { playerRequest(videoId, cpn, visitor(forceNew = false)) }
        val state = first.optJSONObject("playabilityStatus")?.optString("status")
        if (state == "OK" || state == "ERROR" || state == "UNPLAYABLE") return first
        // LOGIN_REQUIRED ("confirm you're not a bot") or no status: try a new visitor id.
        return runInterruptible { playerRequest(videoId, cpn, visitor(forceNew = true)) }
    }

    private fun playerRequest(videoId: String, cpn: String, visitor: String): JSONObject {
        val body = JSONObject()
            .put("videoId", videoId)
            .put("cpn", cpn)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put("context", JSONObject().put("client", clientJson(visitor)))
        return post("$API_HOST/youtubei/v1/player?prettyPrint=false&id=$videoId", body, visitor)
    }

    /** A visitor id from YouTube, reused for [VISITOR_MAX_AGE_MS]. */
    @Synchronized
    private fun visitor(forceNew: Boolean): String {
        val cached = visitorData
        if (!forceNew && cached != null && System.currentTimeMillis() - visitorFetchedAtMs < VISITOR_MAX_AGE_MS) return cached
        val body = JSONObject().put("context", JSONObject().put("client", clientJson(null)))
        val json = post("$WEB_HOST/youtubei/v1/visitor_id?prettyPrint=false", body, null)
        val fresh = json.optJSONObject("responseContext")?.optString("visitorData")?.takeIf { it.isNotBlank() }
            ?: throw ProviderFailure.ProviderParserFailure("No visitor id from YouTube")
        visitorData = fresh
        visitorFetchedAtMs = System.currentTimeMillis()
        return fresh
    }

    private fun clientJson(visitor: String?): JSONObject = JSONObject()
        .put("clientName", CLIENT_NAME)
        .put("clientVersion", CLIENT_VERSION)
        .put("clientScreen", "WATCH")
        .put("platform", "MOBILE")
        .put("deviceMake", "Apple")
        .put("deviceModel", DEVICE_MODEL)
        .put("osName", "visionOS")
        .put("osVersion", OS_VERSION)
        .put("hl", "en")
        .put("gl", java.util.Locale.getDefault().country.takeIf { it.length == 2 } ?: "US")
        .apply { if (visitor != null) put("visitorData", visitor) }

    private fun post(url: String, body: JSONObject, visitor: String?): JSONObject {
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON))
            .header("User-Agent", USER_AGENT)
            .header("X-YouTube-Client-Name", CLIENT_ID)
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .apply { if (visitor != null) header("X-Goog-Visitor-Id", visitor) }
            .build()
        val call = http.newCall(request)
        call.timeout().timeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
        call.execute().use { response ->
            if (response.code == 429) throw ProviderFailure.RateLimited("YouTube direct: HTTP 429")
            if (!response.isSuccessful) throw ProviderFailure.UnknownPlaybackFailure("YouTube direct: HTTP ${response.code}")
            return try {
                JSONObject(response.body.string())
            } catch (e: org.json.JSONException) {
                throw ProviderFailure.ProviderParserFailure("YouTube direct: unreadable response", e)
            }
        }
    }

    /** A content playback nonce: 16 random URL-safe characters, as YouTube's apps send. */
    private fun newCpn(): String {
        val chars = CharArray(16) { CPN_ALPHABET[random.nextInt(CPN_ALPHABET.length)] }
        return String(chars)
    }

    /** Records health like the NewPipe source: a lost connection is the phone's problem, not this source's. */
    private suspend fun <T> tracked(block: suspend () -> T): T {
        try {
            return block().also { healthTracker.recordSuccess(id) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: ProviderFailure) {
            if (e !is ProviderFailure.NetworkFailure) healthTracker.recordFailure(id)
            throw e
        } catch (e: InterruptedIOException) {
            throw ProviderFailure.NetworkFailure(e.message ?: "Network failure", e)
        } catch (e: IOException) {
            throw ProviderFailure.NetworkFailure(e.message ?: "Network failure", e)
        } catch (e: Exception) {
            healthTracker.recordFailure(id)
            throw ProviderFailure.UnknownPlaybackFailure(e.message ?: "YouTube direct failed", e)
        }
    }

    companion object {
        const val PROVIDER_ID = "youtube_direct"
        private const val TAG = "YoutubeDirectProvider"
        private const val API_HOST = "https://youtubei.googleapis.com"
        private const val WEB_HOST = "https://www.youtube.com"
        private const val CLIENT_NAME = "VISIONOS"
        private const val CLIENT_ID = "101"
        private const val CLIENT_VERSION = "1.02"
        private const val DEVICE_MODEL = "RealityDevice14,1"
        private const val OS_VERSION = "25.6.0.23O471"
        private const val USER_AGENT = "com.google.visionos.youtube/1.02(RealityDevice14,1; U; CPU visionOS 25_6_0 like Mac OS X; US)"
        private const val CALL_TIMEOUT_S = 15L
        private const val VISITOR_MAX_AGE_MS = 6 * 3_600_000L
        private const val CPN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        private val JSON = "application/json".toMediaType()
    }
}
