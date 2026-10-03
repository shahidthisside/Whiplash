package com.whiplash.music.data.repository

import android.util.Log
import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The last complete Quick Picks list, kept on disk so Home can show it the
 * moment it opens while a fresh one is built in the background (the way
 * YouTube Music shows its last feed first). It's always replaced by the
 * next full refresh, so it never stands in for the recommendations
 * themselves.
 */
class QuickPicksSnapshot(private val file: File) {

    /** When a saved list was built and from which top artists, so a launch can tell if it's still fresh. */
    data class Meta(val builtAtMs: Long, val topArtists: List<String>)

    /** A saved list and its [Meta]; meta is null for lists saved before it was recorded. */
    data class Saved(val tracks: List<PlayableItem.YoutubeTrack>, val meta: Meta?)

    suspend fun read(): List<PlayableItem.YoutubeTrack> = readSaved().tracks

    suspend fun readSaved(): Saved = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext Saved(emptyList(), null)
        runCatching { decode(file.readText()) }
            .onFailure { Log.w(TAG, "Couldn't read saved Quick Picks", it) }
            .getOrDefault(Saved(emptyList(), null))
    }

    suspend fun write(tracks: List<PlayableItem.YoutubeTrack>, meta: Meta? = null) = withContext(Dispatchers.IO) {
        runCatching {
            // Written to a temp file first so a crash mid-write never leaves half a list.
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(encode(tracks, meta))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { Log.w(TAG, "Couldn't save Quick Picks", it) }
    }

    /** After history or recommendations are reset, old picks shouldn't come back. */
    fun clear() {
        runCatching { file.delete() }
    }

    internal companion object {
        private const val TAG = "QuickPicksSnapshot"

        fun encode(tracks: List<PlayableItem.YoutubeTrack>, meta: Meta?): String {
            if (meta == null) return YoutubeTrackJson.encode(tracks)
            return JSONObject().apply {
                put("builtAtMs", meta.builtAtMs)
                put("topArtists", JSONArray(meta.topArtists))
                put("tracks", JSONArray(YoutubeTrackJson.encode(tracks)))
            }.toString()
        }

        /** Reads both the current form and the older bare list (no meta). */
        fun decode(json: String): Saved {
            if (json.trimStart().startsWith("[")) return Saved(YoutubeTrackJson.decode(json), null)
            val obj = JSONObject(json)
            val artists = obj.optJSONArray("topArtists")
            val meta = Meta(
                builtAtMs = obj.getLong("builtAtMs"),
                topArtists = (0 until (artists?.length() ?: 0)).map { artists!!.getString(it) },
            )
            return Saved(YoutubeTrackJson.decode(obj.getJSONArray("tracks").toString()), meta)
        }
    }
}

/** The JSON form search results and saved Quick Picks are stored in. */
internal object YoutubeTrackJson {

    fun encode(tracks: List<PlayableItem.YoutubeTrack>): String {
        val array = JSONArray()
        tracks.forEach { track ->
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

    fun decode(json: String): List<PlayableItem.YoutubeTrack> {
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
}
