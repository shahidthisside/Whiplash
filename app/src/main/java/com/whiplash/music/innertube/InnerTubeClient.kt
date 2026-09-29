package com.whiplash.music.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/**
 * A minimal client for YouTube Music's own web API (the WEB_REMIX client
 * music.youtube.com uses), for the parts NewPipe doesn't expose: the real
 * YT Music radio with per-track upload kinds, and "You might also like".
 * Anonymous only; every caller falls back to NewPipe when this fails.
 */
class InnerTubeClient(private val http: OkHttpClient) {

    @Volatile private var visitorData: String? = null
    @Volatile private var clientVersion: String? = null
    private val relatedIds = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** One page of the song radio for [seedId]; [cursor] is the previous page's `next`. */
    suspend fun radio(seedId: String, cursor: InnerTubeCursor?): InnerTubePage {
        val body = JSONObject()
            .put("playlistId", cursor?.playlistId ?: "RDAMVM$seedId")
            .put("params", RADIO_PARAMS)
            .put("isAudioOnly", true)
        if (cursor == null) body.put("videoId", seedId) else body.put("continuation", cursor.token)
        val json = post("next", body)
        val page = InnerTubeParser.radioPage(json)
        if (cursor == null) InnerTubeParser.relatedBrowseId(json)?.let { relatedIds[seedId] = it }
        return page
    }

    /** YT Music's "You might also like" songs and similar artists for [videoId]. */
    suspend fun related(videoId: String): InnerTubeRelated {
        val browseId = relatedIds[videoId]
            ?: InnerTubeParser.relatedBrowseId(post("next", JSONObject().put("videoId", videoId).put("isAudioOnly", true)))
                ?.also { relatedIds[videoId] = it }
            ?: return InnerTubeRelated(emptyList(), emptyList())
        return InnerTubeParser.related(post("browse", JSONObject().put("browseId", browseId)))
    }

    private suspend fun post(endpoint: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val version = version()
        val client = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", version)
            .put("hl", "en")
            .put("gl", region())
        visitorData?.let { client.put("visitorData", it) }
        body.put("context", JSONObject().put("client", client))
        val request = Request.Builder()
            .url("$BASE/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody(JSON))
            .header("User-Agent", USER_AGENT)
            .header("Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", version)
            .apply { visitorData?.let { header("X-Goog-Visitor-Id", it) } }
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("InnerTube $endpoint: HTTP ${response.code}")
            val json = JSONObject(response.body.string())
            json.optJSONObject("responseContext")?.optString("visitorData")?.takeIf { it.isNotEmpty() }?.let { visitorData = it }
            json
        }
    }

    /** The version music.youtube.com currently serves, read once per process; a known-good one otherwise. */
    private fun version(): String {
        clientVersion?.let { return it }
        val scraped = runCatching {
            val request = Request.Builder().url(ORIGIN).header("User-Agent", USER_AGENT).header("Cookie", "SOCS=CAI").build()
            http.newCall(request).execute().use { r -> VERSION_RX.find(r.body.string())?.groupValues?.get(1) }
        }.getOrNull()
        return (scraped ?: FALLBACK_VERSION).also { clientVersion = it }
    }

    private fun region(): String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"

    private companion object {
        const val ORIGIN = "https://music.youtube.com"
        const val BASE = "$ORIGIN/youtubei/v1"
        const val FALLBACK_VERSION = "1.20250122.01.00"
        const val RADIO_PARAMS = "wAEB"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        val JSON = "application/json".toMediaType()
        val VERSION_RX = Regex("\"INNERTUBE_CLIENT_VERSION\":\"(1\\.\\d{8}\\.\\d{2}\\.\\d{2})\"")
    }
}
