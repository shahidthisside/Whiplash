// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.sync

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The cloud file's format: gzipped JSON. [FORMAT_VERSION] is bumped only if
 * the shape changes incompatibly; a newer file is refused rather than
 * half-read, so an old app version can never damage a newer library.
 */
object SyncCodec {

    const val FORMAT_VERSION = 1

    class UnsupportedVersion(val version: Int) : Exception("Sync file version $version is newer than this app supports")

    fun encode(snapshot: SyncSnapshot, deviceName: String, nowMs: Long): ByteArray {
        val root = JSONObject().apply {
            put("v", FORMAT_VERSION)
            put("updatedAt", nowMs)
            put("device", deviceName)
            put("songs", JSONObject().apply {
                snapshot.songs.forEach { (id, s) ->
                    put(id, JSONObject().apply {
                        put("title", s.title)
                        put("artist", s.artist)
                        s.album?.let { put("album", it) }
                        s.artworkUrl?.let { put("art", it) }
                        put("dur", s.durationMs)
                        s.albumId?.let { put("albumId", it) }
                        s.artistId?.let { put("artistId", it) }
                        if (s.isExplicit) put("explicit", true)
                    })
                }
            })
            put("favorites", longMap(snapshot.favorites))
            put("pinned", longMap(snapshot.pinned))
            put("history", longMap(snapshot.history))
            put("playlists", JSONObject().apply {
                snapshot.playlists.forEach { (key, p) ->
                    put(key, JSONObject().apply {
                        put("name", p.name)
                        p.description?.let { put("desc", it) }
                        p.artworkUrl?.let { put("art", it) }
                        put("created", p.createdAtEpochMs)
                        put("updated", p.updatedAtEpochMs)
                        p.pinnedAtEpochMs?.let { put("pinned", it) }
                        put("tracks", JSONArray().apply {
                            p.tracks.forEach { t -> put(JSONArray().put(t.id).put(t.addedAtEpochMs)) }
                        })
                    })
                }
            })
            put("replay", JSONObject().apply {
                snapshot.replay.forEach { (key, r) ->
                    put(key, JSONObject().apply {
                        put("title", r.title)
                        put("artist", r.artist)
                        r.artworkUrl?.let { put("art", it) }
                        put("dur", r.durationMs)
                        put("plays", r.plays)
                        put("ms", r.listenedMs)
                        put("last", r.lastPlayedAtEpochMs)
                    })
                }
            })
            put("settings", JSONObject().apply { snapshot.settings.forEach { (k, v) -> put(k, v) } })
            put("lyricOffsets", longMap(snapshot.lyricOffsets))
            snapshot.profile?.let { p ->
                put("profile", JSONObject().apply {
                    p.name?.let { put("name", it) }
                    p.photoJpeg?.let { put("photo", java.util.Base64.getEncoder().encodeToString(it)) }
                    put("updated", p.updatedAtEpochMs)
                })
            }
        }
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): SyncSnapshot {
        val text = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }.toString(Charsets.UTF_8)
        val root = JSONObject(text)
        val version = root.optInt("v", 1)
        if (version > FORMAT_VERSION) throw UnsupportedVersion(version)

        val songs = root.optJSONObject("songs")?.let { o ->
            o.keys().asSequence().mapNotNull { id ->
                val s = o.optJSONObject(id) ?: return@mapNotNull null
                id to SyncSong(
                    title = s.optString("title"),
                    artist = s.optString("artist"),
                    album = s.optStringOrNull("album"),
                    artworkUrl = s.optStringOrNull("art"),
                    durationMs = s.optLong("dur"),
                    albumId = s.optStringOrNull("albumId"),
                    artistId = s.optStringOrNull("artistId"),
                    isExplicit = s.optBoolean("explicit", false),
                )
            }.toMap()
        } ?: emptyMap()

        val playlists = root.optJSONObject("playlists")?.let { o ->
            o.keys().asSequence().mapNotNull { key ->
                val p = o.optJSONObject(key) ?: return@mapNotNull null
                val tracksJson = p.optJSONArray("tracks") ?: JSONArray()
                val tracks = (0 until tracksJson.length()).mapNotNull { i ->
                    val t = tracksJson.optJSONArray(i) ?: return@mapNotNull null
                    val id = t.optString(0).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    SyncTrack(id, t.optLong(1))
                }
                key to SyncPlaylist(
                    name = p.optString("name"),
                    description = p.optStringOrNull("desc"),
                    artworkUrl = p.optStringOrNull("art"),
                    createdAtEpochMs = p.optLong("created"),
                    updatedAtEpochMs = p.optLong("updated"),
                    pinnedAtEpochMs = if (p.has("pinned") && !p.isNull("pinned")) p.optLong("pinned") else null,
                    tracks = tracks,
                )
            }.toMap()
        } ?: emptyMap()

        val replay = root.optJSONObject("replay")?.let { o ->
            o.keys().asSequence().mapNotNull { key ->
                val r = o.optJSONObject(key) ?: return@mapNotNull null
                key to SyncReplay(
                    title = r.optString("title"),
                    artist = r.optString("artist"),
                    artworkUrl = r.optStringOrNull("art"),
                    durationMs = r.optLong("dur"),
                    plays = r.optInt("plays"),
                    listenedMs = r.optLong("ms"),
                    lastPlayedAtEpochMs = r.optLong("last"),
                )
            }.toMap()
        } ?: emptyMap()

        val settings = root.optJSONObject("settings")?.let { o ->
            o.keys().asSequence().associateWith { o.optString(it) }
        } ?: emptyMap()

        return SyncSnapshot(
            songs = songs,
            favorites = readLongMap(root.optJSONObject("favorites")),
            pinned = readLongMap(root.optJSONObject("pinned")),
            history = readLongMap(root.optJSONObject("history")),
            playlists = playlists,
            replay = replay,
            settings = settings,
            lyricOffsets = readLongMap(root.optJSONObject("lyricOffsets")),
            profile = root.optJSONObject("profile")?.let { p ->
                SyncProfile(
                    name = p.optStringOrNull("name"),
                    photoJpeg = p.optStringOrNull("photo")?.let { runCatching { java.util.Base64.getDecoder().decode(it) }.getOrNull() },
                    updatedAtEpochMs = p.optLong("updated"),
                )
            },
        )
    }

    private fun longMap(map: Map<String, Long>) = JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }

    private fun readLongMap(o: JSONObject?): Map<String, Long> =
        o?.keys()?.asSequence()?.associateWith { o.optLong(it) } ?: emptyMap()

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) optString(name) else null
}
