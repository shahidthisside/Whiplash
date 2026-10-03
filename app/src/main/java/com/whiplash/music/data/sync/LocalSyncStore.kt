// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.sync

import androidx.room.withTransaction
import com.whiplash.music.data.backup.BackupManager
import com.whiplash.music.data.local.WhiplashDatabase
import com.whiplash.music.data.local.entity.FavoriteEntity
import com.whiplash.music.data.local.entity.HistoryEntity
import com.whiplash.music.data.local.entity.MediaSource
import com.whiplash.music.data.local.entity.PinnedEntity
import com.whiplash.music.data.local.entity.PlaylistEntity
import com.whiplash.music.data.local.entity.PlaylistTrackEntity
import com.whiplash.music.data.local.entity.ReplayTallyEntity
import com.whiplash.music.data.local.entity.SongEntity
import com.whiplash.music.data.repository.LyricOffsetStore
import com.whiplash.music.domain.model.PlaylistArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Reads this phone's library as a [SyncSnapshot] and writes a merged one back,
 * touching only what actually differs.
 */
class LocalSyncStore(
    private val database: WhiplashDatabase,
    private val backupManager: BackupManager,
    private val lyricOffsetStore: LyricOffsetStore,
    private val profileStore: ProfileStore,
    private val deletePlaylist: suspend (Long) -> Unit,
) {

    /** The snapshot plus what's needed to write back to it (local playlist ids). */
    class LocalState(val snapshot: SyncSnapshot, val playlistIds: Map<String, Long>)

    suspend fun read(): LocalState = withContext(Dispatchers.IO) {
        val online = setOf(MediaSource.YOUTUBE, MediaSource.DOWNLOAD)

        val favorites = HashMap<String, Long>()
        database.favoriteDao().observeAll().first().filter { it.source in online }.forEach {
            favorites[it.trackId] = minOf(favorites[it.trackId] ?: Long.MAX_VALUE, it.addedAtEpochMs)
        }
        val pinned = HashMap<String, Long>()
        database.pinnedDao().observeAll().first().filter { it.source in online }.forEach {
            pinned[it.trackId] = maxOf(pinned[it.trackId] ?: 0L, it.pinnedAtEpochMs)
        }
        val history = database.historyDao().getAllOnline()
            .associate { SyncSnapshot.historyKey(it.trackId, it.playedAtEpochMs) to it.playedAtEpochMs }

        val playlists = LinkedHashMap<String, SyncPlaylist>()
        val playlistIds = HashMap<String, Long>()
        database.playlistDao().observeAll().first().sortedBy { it.id }.forEach { p ->
            var key = SyncSnapshot.playlistKey(p.createdAtEpochMs)
            // Two playlists created in the same millisecond (practically never):
            // keep both rather than letting one overwrite the other.
            if (key in playlists) key = "$key-${p.id}"
            val tracks = database.playlistDao().observeTracks(p.id).first()
                .filter { it.source in online }
                .map { SyncTrack(it.trackId, it.addedAtEpochMs) }
                .distinctBy { it.id }
            playlists[key] = SyncPlaylist(
                name = p.name,
                description = p.description,
                artworkUrl = syncableArtwork(p.artworkUrl),
                createdAtEpochMs = p.createdAtEpochMs,
                updatedAtEpochMs = p.updatedAtEpochMs,
                pinnedAtEpochMs = p.pinnedAtEpochMs,
                tracks = tracks,
            )
            playlistIds[key] = p.id
        }

        val replay = database.replayTallyDao().getAll().filter { it.source in online }.associate {
            SyncSnapshot.replayKey(it.monthKey, it.trackId) to SyncReplay(
                title = it.title,
                artist = it.artist,
                artworkUrl = it.artworkUrl,
                durationMs = it.durationMs,
                plays = it.plays,
                listenedMs = it.listenedMs,
                lastPlayedAtEpochMs = it.lastPlayedAtEpochMs,
            )
        }

        val settingsJson = backupManager.settingsJson()
        settingsJson.remove(LYRIC_OFFSETS_KEY)
        val settings = settingsJson.keys().asSequence().associateWith { settingsJson.optString(it) }

        val partial = SyncSnapshot(
            favorites = favorites,
            pinned = pinned,
            history = history,
            playlists = playlists,
            replay = replay,
            settings = settings,
            lyricOffsets = lyricOffsetStore.offsets.value,
            profile = profileStore.profile.value,
        )
        LocalState(partial.copy(songs = readSongs(partial.referencedIds())), playlistIds)
    }

    /** Song rows for [ids]; downloads fill in any song missing from the song table. */
    private suspend fun readSongs(ids: Set<String>): Map<String, SyncSong> {
        if (ids.isEmpty()) return emptyMap()
        val out = HashMap<String, SyncSong>()
        ids.toList().chunked(SQLITE_MAX_VARIABLES).flatMap { database.songDao().getByIds(it) }.forEach { s ->
            out[s.id] = SyncSong(s.title, s.artist, s.album, s.artworkUrl, s.durationMs, s.albumId, s.artistId, s.isExplicit)
        }
        (ids - out.keys).forEach { id ->
            database.downloadDao().getById(id)?.let { d ->
                // A download's artwork is a file on this phone; the other device
                // loads the real cover when it next resolves the song.
                out[id] = SyncSong(d.title, d.artist, d.album, d.artworkPath?.takeIf { it.startsWith("http") }, d.durationMs)
            }
        }
        return out
    }

    /** Writes [merged] over [local], changing only what differs. */
    suspend fun apply(local: LocalState, merged: SyncSnapshot) = withContext(Dispatchers.IO) {
        val old = local.snapshot
        val now = System.currentTimeMillis()
        database.withTransaction {
            val newSongs = merged.songs.filterKeys { it !in old.songs }
            if (newSongs.isNotEmpty()) {
                database.songDao().upsertAll(newSongs.map { (id, s) -> s.toEntity(id, now) })
            }

            val favoriteDao = database.favoriteDao()
            (old.favorites.keys - merged.favorites.keys).forEach { id ->
                favoriteDao.remove(id, MediaSource.YOUTUBE)
                favoriteDao.remove(id, MediaSource.DOWNLOAD)
            }
            val newFavorites = merged.favorites.filterKeys { it !in old.favorites }
            if (newFavorites.isNotEmpty()) {
                favoriteDao.addAll(newFavorites.map { (id, at) -> FavoriteEntity(id, MediaSource.YOUTUBE, at) })
            }

            val pinnedDao = database.pinnedDao()
            (old.pinned.keys - merged.pinned.keys).forEach { id ->
                pinnedDao.remove(id, MediaSource.YOUTUBE)
                pinnedDao.remove(id, MediaSource.DOWNLOAD)
            }
            merged.pinned.filterKeys { it !in old.pinned }.forEach { (id, at) ->
                pinnedDao.add(PinnedEntity(id, MediaSource.YOUTUBE, at))
            }

            val historyDao = database.historyDao()
            (old.history.keys - merged.history.keys).forEach { key ->
                historyDao.deletePlay(SyncSnapshot.historyTrackId(key), old.history.getValue(key))
            }
            merged.history.filterKeys { it !in old.history }.forEach { (key, at) ->
                historyDao.insert(HistoryEntity(trackId = SyncSnapshot.historyTrackId(key), source = MediaSource.YOUTUBE, playedAtEpochMs = at))
            }
            historyDao.trimToMostRecent(SyncMerge.MAX_HISTORY)

            applyPlaylists(local, merged)

            val replayDao = database.replayTallyDao()
            (old.replay.keys - merged.replay.keys).forEach { key ->
                replayDao.deleteRow(key.substringBefore('|'), key.substringAfter('|'))
            }
            val changedReplay = merged.replay.filter { (key, r) -> old.replay[key] != r }
            if (changedReplay.isNotEmpty()) {
                replayDao.upsertAll(changedReplay.map { (key, r) -> r.toEntity(key) })
            }
        }

        applySettings(old.settings, merged.settings)

        (old.lyricOffsets.keys - merged.lyricOffsets.keys).forEach { lyricOffsetStore.setOffset(it, 0L) }
        merged.lyricOffsets.forEach { (id, ms) -> if (old.lyricOffsets[id] != ms) lyricOffsetStore.setOffset(id, ms) }

        if (merged.profile != old.profile) profileStore.applyFromSync(merged.profile)
    }

    private suspend fun applyPlaylists(local: LocalState, merged: SyncSnapshot) {
        val dao = database.playlistDao()
        val old = local.snapshot.playlists
        (old.keys - merged.playlists.keys).forEach { key -> local.playlistIds[key]?.let { deletePlaylist(it) } }

        merged.playlists.forEach { (key, p) ->
            val before = old[key]
            if (before == p) return@forEach
            val id = local.playlistIds[key]
            if (id == null) {
                val newId = dao.insert(
                    PlaylistEntity(
                        name = p.name,
                        description = p.description,
                        artworkUrl = p.artworkUrl,
                        createdAtEpochMs = p.createdAtEpochMs,
                        updatedAtEpochMs = p.updatedAtEpochMs,
                        pinnedAtEpochMs = p.pinnedAtEpochMs,
                    ),
                )
                p.tracks.forEachIndexed { i, t ->
                    dao.insertTrack(PlaylistTrackEntity(newId, i, t.id, MediaSource.YOUTUBE, t.addedAtEpochMs))
                }
                return@forEach
            }
            if (before == null || before.name != p.name || before.description != p.description || before.updatedAtEpochMs != p.updatedAtEpochMs) {
                dao.rename(id, p.name, p.description, p.updatedAtEpochMs)
            }
            if (before?.pinnedAtEpochMs != p.pinnedAtEpochMs) dao.setPinnedAt(id, p.pinnedAtEpochMs)
            if (before?.artworkUrl != p.artworkUrl) {
                // A gallery cover chosen on this phone never travels (it syncs as
                // null), so an empty cover from the cloud must not wipe it.
                val current = dao.getArtwork(id)
                if (p.artworkUrl != null || !isLocalOnlyArtwork(current)) dao.setArtwork(id, p.artworkUrl)
            }
            if (before?.tracks != p.tracks) {
                val existing = dao.observeTracks(id).first()
                val sourceOf = existing.associate { it.trackId to it.source }
                // Songs from this phone's music folder aren't synced; keep them at the end.
                val deviceOnly = existing.filter { it.source == MediaSource.LOCAL }
                dao.clearTracks(id)
                var position = 0
                p.tracks.forEach { t ->
                    val source = sourceOf[t.id]?.takeIf { it != MediaSource.LOCAL } ?: MediaSource.YOUTUBE
                    dao.insertTrack(PlaylistTrackEntity(id, position++, t.id, source, t.addedAtEpochMs))
                }
                deviceOnly.forEach { dao.insertTrack(it.copy(playlistId = id, position = position++)) }
            }
        }
    }

    private suspend fun applySettings(old: Map<String, String>, merged: Map<String, String>) {
        val changed = merged.filter { (k, v) -> old[k] != v }.keys
        if (changed.isEmpty()) return
        val keys = changed.toMutableSet()
        // The custom theme is written as one value, so send all of its parts together.
        if (keys.any { it in CUSTOM_THEME_KEYS }) keys += CUSTOM_THEME_KEYS.filter { it in merged }
        val json = JSONObject()
        keys.forEach { k -> merged[k]?.let { json.put(k, it) } }
        backupManager.applySettings(json)
    }

    companion object {
        private const val SQLITE_MAX_VARIABLES = 900
        private const val LYRIC_OFFSETS_KEY = "lyricOffsets"
        private val CUSTOM_THEME_KEYS = listOf("customThemeBackground", "customThemeAccent", "glassBackground", "glassBackgroundColor")

        /** A gallery picture stored on this phone — the only cover that can't sync. */
        fun isLocalOnlyArtwork(stored: String?): Boolean {
            val art = PlaylistArt.parse(stored) as? PlaylistArt.Image ?: return false
            return !art.uri.startsWith("http")
        }

        fun syncableArtwork(stored: String?): String? = if (isLocalOnlyArtwork(stored)) null else stored

        private fun SyncSong.toEntity(id: String, now: Long) = SongEntity(
            id = id,
            title = title,
            artist = artist,
            album = album,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            albumId = albumId,
            artistId = artistId,
            isExplicit = isExplicit,
            cachedAtEpochMs = now,
        )

        private fun SyncReplay.toEntity(key: String) = ReplayTallyEntity(
            monthKey = key.substringBefore('|'),
            trackId = key.substringAfter('|'),
            source = MediaSource.YOUTUBE,
            title = title,
            artist = artist,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            plays = plays,
            listenedMs = listenedMs,
            lastPlayedAtEpochMs = lastPlayedAtEpochMs,
        )
    }
}
