package com.whiplash.music.data.repository

import com.whiplash.music.data.local.dao.LocalAlbumDao
import com.whiplash.music.data.local.dao.LocalArtistDao
import com.whiplash.music.data.local.dao.LocalSongDao
import com.whiplash.music.data.local.entity.LocalSongEntity
import com.whiplash.music.domain.model.LocalAlbum
import com.whiplash.music.domain.model.LocalArtist
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.localmedia.MediaStoreScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Bridges the [MediaStoreScanner] and Room DAOs, and exposes the local
 * library as domain models to the UI layer (section 30: refresh
 * intelligently rather than rescanning everything on every launch — the
 * caller decides when [refresh] runs, e.g. on first launch, pull-to-refresh,
 * or a MediaStore change observer added in a future iteration).
 */
class LocalLibraryRepository(
    private val scanner: MediaStoreScanner,
    private val localSongDao: LocalSongDao,
    private val localAlbumDao: LocalAlbumDao,
    private val localArtistDao: LocalArtistDao,
) {

    fun observeSongs(): Flow<List<PlayableItem.LocalTrack>> =
        localSongDao.observeAll().map { it.map(LocalSongEntity::toDomain) }

    fun observeSongsByAlbum(albumId: Long): Flow<List<PlayableItem.LocalTrack>> =
        localSongDao.observeByAlbum(albumId).map { it.map(LocalSongEntity::toDomain) }

    fun observeSongsByArtist(artistId: Long): Flow<List<PlayableItem.LocalTrack>> =
        localSongDao.observeByArtist(artistId).map { it.map(LocalSongEntity::toDomain) }

    fun search(query: String): Flow<List<PlayableItem.LocalTrack>> =
        localSongDao.search(query).map { it.map(LocalSongEntity::toDomain) }

    fun observeAlbums(): Flow<List<LocalAlbum>> =
        localAlbumDao.observeAll().map { albums ->
            albums.map { LocalAlbum(id = it.albumId, title = it.title, artist = it.artist, songCount = it.songCount, year = it.year) }
        }

    fun observeArtists(): Flow<List<LocalArtist>> =
        localArtistDao.observeAll().map { artists ->
            artists.map { LocalArtist(id = it.artistId, name = it.name, trackCount = it.trackCount, albumCount = it.albumCount) }
        }

    fun observeSongCount(): Flow<Int> = localSongDao.observeCount()

    /**
     * Runs a full MediaStore scan and reconciles the result into Room:
     * upserts everything found, then deletes rows for songs/albums/artists
     * that no longer exist on-device (handles deleted/moved files per
     * section 30).
     */
    suspend fun refresh() {
        val result = scanner.scan()

        val previousSongIds = localSongDao.getAllIds().toSet()
        val currentSongIds = result.songs.map { it.mediaStoreId }.toSet()
        val removedSongIds = (previousSongIds - currentSongIds).toList()

        localSongDao.upsertAll(result.songs)
        if (removedSongIds.isNotEmpty()) {
            localSongDao.deleteByIds(removedSongIds)
        }

        val previousAlbumIds = localAlbumDao.getAllIds().toSet()
        val currentAlbumIds = result.albums.map { it.albumId }.toSet()
        localAlbumDao.upsertAll(result.albums)
        val removedAlbumIds = (previousAlbumIds - currentAlbumIds).toList()
        if (removedAlbumIds.isNotEmpty()) {
            localAlbumDao.deleteByIds(removedAlbumIds)
        }

        val previousArtistIds = localArtistDao.getAllIds().toSet()
        val currentArtistIds = result.artists.map { it.artistId }.toSet()
        localArtistDao.upsertAll(result.artists)
        val removedArtistIds = (previousArtistIds - currentArtistIds).toList()
        if (removedArtistIds.isNotEmpty()) {
            localArtistDao.deleteByIds(removedArtistIds)
        }
    }
}

private fun LocalSongEntity.toDomain(): PlayableItem.LocalTrack = PlayableItem.LocalTrack(
    id = mediaStoreId.toString(),
    title = title,
    artist = artist,
    album = album,
    artworkUri = albumId?.let { "content://media/external/audio/albumart/$it" },
    durationMs = durationMs,
    mediaStoreUri = uri,
)
