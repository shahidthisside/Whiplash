package com.whiplash.music.data.repository

import com.whiplash.music.data.local.dao.DownloadDao
import com.whiplash.music.data.local.dao.FavoriteDao
import com.whiplash.music.data.local.dao.HistoryDao
import com.whiplash.music.data.local.dao.LocalSongDao
import com.whiplash.music.data.local.dao.PinnedDao
import com.whiplash.music.data.local.dao.PlaylistDao
import com.whiplash.music.data.local.dao.SongDao
import com.whiplash.music.data.local.entity.FavoriteEntity
import com.whiplash.music.data.local.entity.HistoryEntity
import com.whiplash.music.data.local.entity.PinnedEntity
import com.whiplash.music.data.local.entity.SongEntity
import com.whiplash.music.domain.model.MediaSource
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.whiplash.music.data.local.entity.MediaSource as EntityMediaSource

private fun MediaSource.toEntity(): EntityMediaSource = when (this) {
    MediaSource.YOUTUBE -> EntityMediaSource.YOUTUBE
    MediaSource.LOCAL -> EntityMediaSource.LOCAL
    MediaSource.DOWNLOAD -> EntityMediaSource.DOWNLOAD
}

private fun EntityMediaSource.toDomain(): MediaSource = when (this) {
    EntityMediaSource.YOUTUBE -> MediaSource.YOUTUBE
    EntityMediaSource.LOCAL -> MediaSource.LOCAL
    EntityMediaSource.DOWNLOAD -> MediaSource.DOWNLOAD
}

/**
 * Ties together history, favorites, and playlists (sections 21, 26, 33, 38)
 * with the two metadata caches ([SongDao] for YouTube tracks, [LocalSongDao]
 * for device tracks) so those features can resolve full, displayable
 * [PlayableItem]s from a bare (trackId, source) reference rather than
 * needing a fresh network/MediaStore fetch every time.
 *
 * [cacheSong] must be called whenever a YouTube track starts playing (see
 * [com.whiplash.music.playback.controller.PlaybackController]) so its
 * metadata is available here later — history/favorites/playlists only ever
 * store references, never full metadata themselves (avoids duplicating the
 * same title/artist/artwork across many tables).
 */
class LibraryRepository(
    private val historyDao: HistoryDao,
    private val favoriteDao: FavoriteDao,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val localSongDao: LocalSongDao,
    private val pinnedDao: PinnedDao,
    private val downloadDao: DownloadDao,
) {

    /** Caches metadata for a YouTube track so it can be resolved later by id alone. */
    suspend fun cacheSong(track: PlayableItem.YoutubeTrack) {
        songDao.upsert(
            SongEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUri,
                durationMs = track.durationMs,
                albumId = null,
                artistId = null,
                cachedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun recordPlayed(item: PlayableItem) {
        historyDao.insert(
            HistoryEntity(
                trackId = item.id,
                source = item.source.toEntity(),
                playedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    fun observeRecentlyPlayed(limit: Int = 50): Flow<List<PlayableItem>> =
        historyDao.observeRecentlyPlayed(limit)
            .map { it.map { h -> h.trackId to h.source.toDomain() } }
            .flatMapResolve()

    suspend fun clearHistory() = historyDao.clear()

    /**
     * Removes [item] from history only (the History screen's own per-item
     * "Remove from history" action) — deliberately distinct from
     * [removeFromSpeedDial], which also unpins: a history-only removal
     * should never silently change a song's pinned status. Pinned songs
     * still show in Speed dial afterward exactly as before, since Speed
     * dial's own query already sources pinned tracks independently of
     * history (see [observePinned]/[speedDial] composition in
     * HomeViewModel).
     */
    suspend fun removeFromHistory(item: PlayableItem) {
        historyDao.removeAllForTrack(item.id, item.source.toEntity())
    }

    /**
     * Removes [item] from Speed dial (section 31): deletes every history
     * entry for it (so it stops surfacing as "recently played") AND unpins
     * it if it was pinned, since "remove" should fully remove it from the
     * grid regardless of how it got there.
     */
    suspend fun removeFromSpeedDial(item: PlayableItem) {
        historyDao.removeAllForTrack(item.id, item.source.toEntity())
        pinnedDao.remove(item.id, item.source.toEntity())
    }

    fun observeFavorites(): Flow<List<PlayableItem>> =
        favoriteDao.observeAll().map { it.map { fav -> fav.trackId to fav.source.toDomain() } }.flatMapResolve()

    fun observeIsFavorite(item: PlayableItem): Flow<Boolean> =
        favoriteDao.observeIsFavorite(item.id, item.source.toEntity())

    suspend fun toggleFavorite(item: PlayableItem, isCurrentlyFavorite: Boolean) {
        if (isCurrentlyFavorite) {
            favoriteDao.remove(item.id, item.source.toEntity())
        } else {
            if (item is PlayableItem.YoutubeTrack) cacheSong(item)
            favoriteDao.add(FavoriteEntity(item.id, item.source.toEntity(), System.currentTimeMillis()))
        }
    }

    /** Tracks pinned to the Home screen's Speed dial grid (section 31), most-recently-pinned first. */
    fun observePinned(): Flow<List<PlayableItem>> =
        pinnedDao.observeAll().map { it.map { p -> p.trackId to p.source.toDomain() } }.flatMapResolve()

    fun observeIsPinned(item: PlayableItem): Flow<Boolean> =
        pinnedDao.observeIsPinned(item.id, item.source.toEntity())

    suspend fun togglePinned(item: PlayableItem, isCurrentlyPinned: Boolean) {
        if (isCurrentlyPinned) {
            pinnedDao.remove(item.id, item.source.toEntity())
        } else {
            if (item is PlayableItem.YoutubeTrack) cacheSong(item)
            pinnedDao.add(PinnedEntity(item.id, item.source.toEntity(), System.currentTimeMillis()))
        }
    }

    fun observePlaylists(): Flow<List<Playlist>> = playlistDao.observeAll().map { entities ->
        entities.map { Playlist(id = it.id, name = it.name, description = it.description, artworkUrl = it.artworkUrl) }
    }

    suspend fun createPlaylist(name: String): Long {
        val now = System.currentTimeMillis()
        return playlistDao.insert(
            com.whiplash.music.data.local.entity.PlaylistEntity(
                name = name,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
    }

    suspend fun renamePlaylist(id: Long, name: String, description: String?) {
        playlistDao.rename(id, name, description, System.currentTimeMillis())
    }

    suspend fun deletePlaylist(id: Long) = playlistDao.delete(id)

    /**
     * Real, reported crash: rows already inserted before [PlaylistDao.addTrack]'s
     * own duplicate check existed (or from any future code path that
     * bypasses it) leave the same (trackId, source) pair at two different
     * positions in the same playlist — every screen showing a playlist's
     * tracks keys each row by "${item.source}:${item.id}"
     * (PlayableItemsList.itemsIndexed), so a genuine duplicate crashes
     * Compose outright (IllegalArgumentException: "Key ... was already
     * used") rather than just rendering the same song twice. distinctBy
     * here is a permanent safety net independent of the DB-level
     * duplicate check — it makes this method itself correct regardless
     * of whatever state already exists in the table, with no migration
     * required to repair a playlist that became corrupted before the fix.
     */
    fun observePlaylistTracks(playlistId: Long): Flow<List<PlayableItem>> =
        playlistDao.observeTracks(playlistId)
            .map { it.map { t -> t.trackId to t.source.toDomain() } }
            .flatMapResolve()
            .map { it.distinctBy { item -> "${item.source}:${item.id}" } }

    /** Returns true if [item] was actually added, false if it was already in [playlistId] (see [PlaylistDao.addTrack]'s own doc — a real, reported duplicate-track crash). */
    suspend fun addToPlaylist(playlistId: Long, item: PlayableItem): Boolean {
        if (item is PlayableItem.YoutubeTrack) cacheSong(item)
        return playlistDao.addTrack(playlistId, item.id, item.source.toEntity(), System.currentTimeMillis())
    }

    suspend fun removeFromPlaylistAt(playlistId: Long, position: Int) =
        playlistDao.removeTrackAt(playlistId, position)

    /**
     * Removes [item] from [playlistId] by id (see [PlaylistDao.removeTrack]
     * for why this is id-based rather than position-based) — backs the
     * Playlist detail screen's "Remove from playlist" action.
     */
    suspend fun removeFromPlaylist(playlistId: Long, item: PlayableItem) =
        playlistDao.removeTrack(playlistId, item.id)

    /**
     * Moves [item] from [fromPlaylistId] to [toPlaylistId] — the
     * "Move to other playlist" action. Removal from the source playlist
     * only runs if the add to the target actually happened (a genuinely
     * new row, not a duplicate) — see [PlaylistDao.addTrack]'s doc.
     *
     * This was previously unconditional (add-then-remove regardless of
     * the add's outcome): a real, reported bug where moving a song into
     * a playlist it was *already* in correctly showed "Already in X" but
     * still silently deleted the song from the source playlist — the
     * user's song vanished from where they were looking at it, with no
     * playlist actually gaining anything, since it was already there.
     * Now a "song already exists in target" outcome is a true no-op: the
     * source is left completely untouched, matching the toast's own
     * "nothing happened" message. Only a genuine move (added = true)
     * removes the source-side row, so the song ends up in exactly one
     * place either way — never in both playlists (the old add-then-
     * remove could momentarily leave it in both, but only ever as an
     * intermediate step toward a state where it now stays in both by design,
     * not because of a partial-failure edge case) and never in neither.
     */
    suspend fun moveToPlaylist(fromPlaylistId: Long, toPlaylistId: Long, item: PlayableItem): Boolean {
        val added = addToPlaylist(toPlaylistId, item)
        if (added) {
            removeFromPlaylist(fromPlaylistId, item)
        }
        return added
    }

    // --- Offline downloads (Library > Downloads, YouTube-Music-style) ---

    /** Completed downloads, most recent first — backs the Downloads tab. */
    fun observeDownloads(): Flow<List<PlayableItem.DownloadedTrack>> =
        downloadDao.observeCompleted().map { entities ->
            entities.map {
                PlayableItem.DownloadedTrack(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    artworkUri = it.artworkPath,
                    durationMs = it.durationMs,
                    fileUri = it.filePath,
                )
            }
        }

    /** Set of ids of every completed download — used to render the small checkmark badge on any matching track anywhere it appears. */
    fun observeDownloadedIds(): Flow<Set<String>> = downloadDao.observeCompletedIds().map { it.toSet() }

    suspend fun removeDownload(id: String, filePath: String) {
        runCatching { java.io.File(filePath).delete() }
        downloadDao.delete(id)
    }

    /** Resolves a list of (trackId, source) pairs into displayable [PlayableItem]s, silently dropping any that no longer resolve. */
    private fun Flow<List<Pair<String, MediaSource>>>.flatMapResolve(): Flow<List<PlayableItem>> = map { refs ->
        if (refs.isEmpty()) return@map emptyList()

        val youtubeIds = refs.filter { it.second == MediaSource.YOUTUBE }.map { it.first }
        val localIds = refs.filter { it.second == MediaSource.LOCAL }.mapNotNull { it.first.toLongOrNull() }
        val downloadIds = refs.filter { it.second == MediaSource.DOWNLOAD }.map { it.first }

        val songs = if (youtubeIds.isNotEmpty()) songDao.getByIds(youtubeIds).associateBy { it.id } else emptyMap()
        val localSongs = if (localIds.isNotEmpty()) {
            localIds.mapNotNull { localSongDao.getById(it) }.associateBy { it.mediaStoreId.toString() }
        } else {
            emptyMap()
        }
        val downloads = if (downloadIds.isNotEmpty()) {
            downloadIds.mapNotNull { downloadDao.getById(it) }.associateBy { it.id }
        } else {
            emptyMap()
        }

        refs.mapNotNull { (id, source) ->
            when (source) {
                MediaSource.YOUTUBE -> songs[id]?.let {
                    PlayableItem.YoutubeTrack(
                        id = it.id,
                        title = it.title,
                        artist = it.artist,
                        album = it.album,
                        artworkUri = it.artworkUrl,
                        durationMs = it.durationMs,
                    )
                }
                MediaSource.LOCAL -> localSongs[id]?.let {
                    PlayableItem.LocalTrack(
                        id = it.mediaStoreId.toString(),
                        title = it.title,
                        artist = it.artist,
                        album = it.album,
                        artworkUri = null,
                        durationMs = it.durationMs,
                        mediaStoreUri = it.uri,
                    )
                }
                MediaSource.DOWNLOAD -> downloads[id]?.let {
                    PlayableItem.DownloadedTrack(
                        id = it.id,
                        title = it.title,
                        artist = it.artist,
                        album = it.album,
                        artworkUri = it.artworkPath,
                        durationMs = it.durationMs,
                        fileUri = it.filePath,
                    )
                }
            }
        }
    }
}
