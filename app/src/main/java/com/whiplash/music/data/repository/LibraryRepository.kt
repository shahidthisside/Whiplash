package com.whiplash.music.data.repository

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
}

private fun EntityMediaSource.toDomain(): MediaSource = when (this) {
    EntityMediaSource.YOUTUBE -> MediaSource.YOUTUBE
    EntityMediaSource.LOCAL -> MediaSource.LOCAL
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

    fun observePlaylistTracks(playlistId: Long): Flow<List<PlayableItem>> =
        playlistDao.observeTracks(playlistId)
            .map { it.map { t -> t.trackId to t.source.toDomain() } }
            .flatMapResolve()

    suspend fun addToPlaylist(playlistId: Long, item: PlayableItem) {
        if (item is PlayableItem.YoutubeTrack) cacheSong(item)
        playlistDao.addTrack(playlistId, item.id, item.source.toEntity(), System.currentTimeMillis())
    }

    suspend fun removeFromPlaylistAt(playlistId: Long, position: Int) =
        playlistDao.removeTrackAt(playlistId, position)

    /** Resolves a list of (trackId, source) pairs into displayable [PlayableItem]s, silently dropping any that no longer resolve. */
    private fun Flow<List<Pair<String, MediaSource>>>.flatMapResolve(): Flow<List<PlayableItem>> = map { refs ->
        if (refs.isEmpty()) return@map emptyList()

        val youtubeIds = refs.filter { it.second == MediaSource.YOUTUBE }.map { it.first }
        val localIds = refs.filter { it.second == MediaSource.LOCAL }.mapNotNull { it.first.toLongOrNull() }

        val songs = if (youtubeIds.isNotEmpty()) songDao.getByIds(youtubeIds).associateBy { it.id } else emptyMap()
        val localSongs = if (localIds.isNotEmpty()) {
            localIds.mapNotNull { localSongDao.getById(it) }.associateBy { it.mediaStoreId.toString() }
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
            }
        }
    }
}
