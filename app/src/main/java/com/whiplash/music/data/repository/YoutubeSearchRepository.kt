package com.whiplash.music.data.repository

import android.util.Log
import com.whiplash.music.data.local.dao.SearchCacheDao
import com.whiplash.music.data.local.entity.SearchCacheEntity
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.YoutubeArtistResult
import com.whiplash.music.domain.model.YoutubePlaylistResult
import com.whiplash.music.playback.provider.newpipe.YoutubeSearchProvider
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridges [YoutubeSearchProvider] and [SearchCacheDao], caching the most
 * recent result set per query so a repeated search shows results
 * immediately while a fresh network search runs in the background
 * (section 53: "cache -> display immediately -> background refresh").
 */
class YoutubeSearchRepository(
    private val searchProvider: YoutubeSearchProvider,
    private val searchCacheDao: SearchCacheDao,
) {

    /** Cached results for [query] if present and not older than [maxAgeMs], or null. */
    suspend fun cachedResults(query: String, maxAgeMs: Long = DEFAULT_CACHE_MAX_AGE_MS): List<PlayableItem.YoutubeTrack>? {
        val normalized = normalize(query)
        val entry = searchCacheDao.get(normalized) ?: return null
        if (System.currentTimeMillis() - entry.cachedAtEpochMs > maxAgeMs) return null
        return runCatching { deserialize(entry.resultJson) }.getOrNull()
    }

    /** Runs a real search and caches the result set for next time. */
    suspend fun search(query: String): List<PlayableItem.YoutubeTrack> {
        val normalized = normalize(query)
        val results = searchProvider.search(normalized)
        runCatching {
            searchCacheDao.upsert(
                SearchCacheEntity(
                    query = normalized,
                    resultJson = serialize(results),
                    cachedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }.onFailure { Log.w(TAG, "Failed to cache search results for '$normalized'", it) }
        return results
    }

    /** Real album search (section 32/37), not cached — album/artist/playlist results are lighter-weight and less frequently repeated than song searches. */
    suspend fun searchAlbums(query: String): List<YoutubePlaylistResult> = searchProvider.searchAlbums(normalize(query))

    /** Real playlist search (section 32/37). */
    suspend fun searchPlaylists(query: String): List<YoutubePlaylistResult> = searchProvider.searchPlaylists(normalize(query))

    /** Real artist search (section 32/37/40). */
    suspend fun searchArtists(query: String): List<YoutubeArtistResult> = searchProvider.searchArtists(normalize(query))

    private fun normalize(query: String): String = query.trim().lowercase()

    private fun serialize(results: List<PlayableItem.YoutubeTrack>): String {
        val array = JSONArray()
        results.forEach { track ->
            array.put(
                JSONObject().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("album", track.album ?: JSONObject.NULL)
                    put("artworkUri", track.artworkUri ?: JSONObject.NULL)
                    put("durationMs", track.durationMs)
                },
            )
        }
        return array.toString()
    }

    private fun deserialize(json: String): List<PlayableItem.YoutubeTrack> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            PlayableItem.YoutubeTrack(
                id = obj.getString("id"),
                title = obj.getString("title"),
                artist = obj.getString("artist"),
                album = obj.optString("album", null.toString()).takeIf { it != "null" },
                artworkUri = obj.optString("artworkUri", null.toString()).takeIf { it != "null" },
                durationMs = obj.getLong("durationMs"),
            )
        }
    }

    private companion object {
        const val TAG = "YoutubeSearchRepository"
        const val DEFAULT_CACHE_MAX_AGE_MS = 10 * 60_000L // 10 minutes
    }
}
