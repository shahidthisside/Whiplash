// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.innertube

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import androidx.core.content.edit
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * A minimal client for YouTube Music's own web API (the WEB_REMIX client
 * music.youtube.com uses), for the parts NewPipe doesn't expose: the real
 * YT Music radio with per-track upload kinds, and "You might also like".
 * Anonymous only; every caller falls back to NewPipe when this fails.
 */
class InnerTubeClient(
    private val http: OkHttpClient,
    private val versionStore: VersionStore? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {

    @Volatile private var visitorData: String? = null
    @Volatile private var clientVersion: String? = null
    // One lookup at a time: the first calls of a session arrive together,
    // and each used to download the music.youtube.com page on its own.
    private val versionLock = Mutex()
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
        // A whole-call limit: the read timeout only covers gaps between bytes,
        // so a response trickling in could otherwise hold up autoplay for minutes.
        val call = http.newCall(request).apply { timeout().timeout(CALL_TIMEOUT_S, TimeUnit.SECONDS) }
        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("InnerTube $endpoint: HTTP ${response.code}")
            val json = JSONObject(response.body.string())
            json.optJSONObject("responseContext")?.optString("visitorData")?.takeIf { it.isNotEmpty() }?.let { visitorData = it }
            json
        }
    }

    /**
     * The version music.youtube.com currently serves. Looked up once and
     * remembered between launches; a remembered one older than a day is
     * still used straight away while a fresh one is fetched in the
     * background. A known-good version stands in when the page can't be read.
     */
    internal suspend fun version(): String {
        clientVersion?.let { return it }
        return versionLock.withLock {
            clientVersion?.let { return@withLock it }
            val stored = runCatching { versionStore?.read() }.getOrNull()
            if (stored != null && now() - stored.savedAtMs in 0 until VERSION_MAX_AGE_MS) {
                clientVersion = stored.version
                if (now() - stored.savedAtMs >= VERSION_REFRESH_AGE_MS) {
                    background.launch { scrapeVersion()?.let { clientVersion = it } }
                }
                return@withLock stored.version
            }
            (scrapeVersion() ?: stored?.version ?: FALLBACK_VERSION).also { clientVersion = it }
        }
    }

    private fun scrapeVersion(): String? = runCatching {
        val request = Request.Builder().url(ORIGIN).header("User-Agent", USER_AGENT).header("Cookie", "SOCS=CAI").build()
        val call = http.newCall(request)
        // A slow page shouldn't hold up every request waiting on it.
        call.timeout().timeout(SCRAPE_TIMEOUT_S, TimeUnit.SECONDS)
        call.execute().use { r -> VERSION_RX.find(r.body.string())?.groupValues?.get(1) }
    }.getOrNull()?.also { v -> runCatching { versionStore?.write(StoredVersion(v, now())) } }

    private fun region(): String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"

    private companion object {
        const val ORIGIN = "https://music.youtube.com"
        const val BASE = "$ORIGIN/youtubei/v1"
        const val FALLBACK_VERSION = "1.20250122.01.00"
        const val SCRAPE_TIMEOUT_S = 5L
        const val CALL_TIMEOUT_S = 20L
        const val VERSION_REFRESH_AGE_MS = 24 * 3_600_000L
        const val VERSION_MAX_AGE_MS = 30 * 24 * 3_600_000L
        const val RADIO_PARAMS = "wAEB"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        val JSON = "application/json".toMediaType()
        val VERSION_RX = Regex("\"INNERTUBE_CLIENT_VERSION\":\"(1\\.\\d{8}\\.\\d{2}\\.\\d{2})\"")
    }
}

/** A remembered client version and when it was read. */
data class StoredVersion(val version: String, val savedAtMs: Long)

/** Where [InnerTubeClient] keeps the client version between launches. */
interface VersionStore {
    fun read(): StoredVersion?
    fun write(value: StoredVersion)
}

class SharedPrefsVersionStore(private val prefs: android.content.SharedPreferences) : VersionStore {
    override fun read(): StoredVersion? {
        val v = prefs.getString(KEY_VERSION, null) ?: return null
        return StoredVersion(v, prefs.getLong(KEY_SAVED_AT, 0L))
    }

    override fun write(value: StoredVersion) {
        prefs.edit {
            putString(KEY_VERSION, value.version)
            putLong(KEY_SAVED_AT, value.savedAtMs)
        }
    }

    private companion object {
        const val KEY_VERSION = "client_version"
        const val KEY_SAVED_AT = "client_version_saved_at"
    }
}
