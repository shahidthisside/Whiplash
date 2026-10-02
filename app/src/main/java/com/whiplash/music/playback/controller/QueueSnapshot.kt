package com.whiplash.music.playback.controller

import android.util.Log
import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The queue as it was when the app last ran: the songs, which one was
 * playing and how far in, which ones autoplay added, and shuffle/repeat.
 * Restored on the next launch paused, so the player picks up where it
 * left off instead of starting empty.
 */
data class QueueSnapshot(
    val items: List<PlayableItem>,
    val currentIndex: Int,
    val positionMs: Long,
    val autoplayIds: Set<String>,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode,
    /** The playing song's real length, which search results don't always carry. */
    val durationMs: Long = 0L,
) {
    /** A snapshot is only worth restoring if it points at a real song. */
    val isUsable: Boolean get() = items.isNotEmpty() && currentIndex in items.indices

    companion object {
        private const val VERSION = 1

        fun encode(s: QueueSnapshot): String = JSONObject()
            .put("v", VERSION)
            .put("index", s.currentIndex)
            .put("positionMs", s.positionMs)
            .put("durationMs", s.durationMs)
            .put("shuffle", s.shuffleEnabled)
            .put("repeat", s.repeatMode.name)
            .put("autoplay", JSONArray(s.autoplayIds.toList()))
            .put("items", JSONArray().apply { s.items.forEach { put(encodeItem(it)) } })
            .toString()

        /** Null for anything unreadable, so a damaged file just means an empty queue. */
        fun decode(json: String): QueueSnapshot? = runCatching {
            val o = JSONObject(json)
            if (o.optInt("v") != VERSION) return null
            val arr = o.getJSONArray("items")
            val items = (0 until arr.length()).mapNotNull { decodeItem(arr.getJSONObject(it)) }
            val auto = o.optJSONArray("autoplay")
            QueueSnapshot(
                items = items,
                // Items that couldn't be read are dropped, so the index is
                // re-clamped rather than trusted.
                currentIndex = o.getInt("index").coerceIn(-1, items.lastIndex),
                positionMs = o.optLong("positionMs").coerceAtLeast(0L),
                autoplayIds = if (auto == null) emptySet() else (0 until auto.length()).mapTo(HashSet()) { auto.getString(it) },
                shuffleEnabled = o.optBoolean("shuffle"),
                repeatMode = runCatching { RepeatMode.valueOf(o.optString("repeat")) }.getOrDefault(RepeatMode.OFF),
                durationMs = o.optLong("durationMs").coerceAtLeast(0L),
            )
        }.getOrNull()

        /** A plain list of songs of any kind, in the same form as the queue (used by Speed dial's saved copy). */
        fun encodeItems(items: List<PlayableItem>): String =
            org.json.JSONArray().apply { items.forEach { put(encodeItem(it)) } }.toString()

        fun decodeItems(json: String): List<PlayableItem> = runCatching {
            val a = org.json.JSONArray(json)
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::decodeItem) }
        }.getOrDefault(emptyList())

        private fun encodeItem(item: PlayableItem): JSONObject {
            val o = JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("artist", item.artist)
                .put("album", item.album ?: JSONObject.NULL)
                .put("artworkUri", item.artworkUri ?: JSONObject.NULL)
                .put("durationMs", item.durationMs)
            when (item) {
                is PlayableItem.YoutubeTrack -> o.put("type", "youtube")
                is PlayableItem.LocalTrack -> o.put("type", "local").put("uri", item.mediaStoreUri)
                is PlayableItem.DownloadedTrack -> o.put("type", "download").put("uri", item.fileUri)
            }
            return o
        }

        private fun decodeItem(o: JSONObject): PlayableItem? {
            val id = o.optString("id").ifEmpty { return null }
            val title = o.optString("title")
            val artist = o.optString("artist")
            val album = o.optNullableString("album")
            val art = o.optNullableString("artworkUri")
            val duration = o.optLong("durationMs")
            return when (o.optString("type")) {
                "youtube" -> PlayableItem.YoutubeTrack(id, title, artist, album, art, duration)
                "local" -> PlayableItem.LocalTrack(id, title, artist, album, art, duration, o.optNullableString("uri") ?: return null)
                "download" -> PlayableItem.DownloadedTrack(id, title, artist, album, art, duration, o.optNullableString("uri") ?: return null)
                else -> null
            }
        }

        private fun JSONObject.optNullableString(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
    }
}

/** Where [PlaybackController] keeps the queue between launches. */
interface QueueStore {
    suspend fun read(): QueueSnapshot?
    suspend fun write(snapshot: QueueSnapshot?)
}

/** A small JSON file in app storage; cleared when the queue is emptied. */
class FileQueueStore(private val file: File) : QueueStore {

    override suspend fun read(): QueueSnapshot? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching { QueueSnapshot.decode(file.readText()) }.getOrNull()?.takeIf { it.isUsable }
    }

    override suspend fun write(snapshot: QueueSnapshot?) = withContext(Dispatchers.IO) {
        runCatching {
            if (snapshot == null || !snapshot.isUsable) {
                file.delete()
                return@runCatching
            }
            // Temp file then rename, so a crash mid-write can't leave half a queue.
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(QueueSnapshot.encode(snapshot))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { Log.w("QueueStore", "Couldn't save the queue", it) }
        Unit
    }
}
