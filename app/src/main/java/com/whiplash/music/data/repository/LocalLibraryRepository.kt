package com.whiplash.music.data.repository

import com.whiplash.music.data.local.dao.LocalAlbumDao
import com.whiplash.music.data.local.dao.LocalArtistDao
import com.whiplash.music.data.local.dao.LocalSongDao
import com.whiplash.music.data.local.entity.LocalSongEntity
import com.whiplash.music.domain.model.LocalAlbum
import com.whiplash.music.domain.model.LocalArtist
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.localmedia.MediaStoreScanner
import androidx.room.withTransaction
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
    private val database: com.whiplash.music.data.local.WhiplashDatabase,
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

        // The scan itself stays outside the transaction (it's MediaStore I/O,
        // not database work, and can take a while on a large library), but the
        // reconciliation below is wrapped so it is atomic. Previously these
        // were six independent writes: an upsert and a delete for each of
        // songs, albums and artists. A cancellation or process death between
        // any two of them — entirely possible, since refresh() is triggered by
        // a ContentObserver that can fire at any moment — left the local
        // library in a visibly inconsistent state, e.g. songs already removed
        // while their albums/artists still listed them, or album counts
        // disagreeing with the songs actually present.
        database.withTransaction {
            reconcileScanResult(result)
        }
    }

    private suspend fun reconcileScanResult(result: MediaStoreScanner.ScanResult) {
        val previousSongIds = localSongDao.getAllIds().toSet()
        val currentSongIds = result.songs.map { it.mediaStoreId }.toSet()
        val removedSongIds = (previousSongIds - currentSongIds).toList()

        localSongDao.upsertAll(result.songs)
        // Delete removed rows in chunks: `deleteByIds` uses `WHERE id IN (:ids)`,
        // one bound variable per id, and SQLite caps a statement at
        // SQLITE_MAX_VARIABLE_NUMBER (999 on the SQLite bundled with API < 30,
        // and minSdk here is 26). Deleting a folder of 1000+ local tracks at
        // once makes `removedSongIds` exceed that limit, throwing
        // SQLiteException ("too many SQL variables") and aborting this whole
        // reconcile transaction — and refresh() is driven by a ContentObserver
        // that can fire on exactly such a bulk deletion. Chunking keeps every
        // statement within the limit.
        removedSongIds.chunked(SQLITE_MAX_VARIABLES).forEach { localSongDao.deleteByIds(it) }

        val previousAlbumIds = localAlbumDao.getAllIds().toSet()
        val currentAlbumIds = result.albums.map { it.albumId }.toSet()
        localAlbumDao.upsertAll(result.albums)
        val removedAlbumIds = (previousAlbumIds - currentAlbumIds).toList()
        removedAlbumIds.chunked(SQLITE_MAX_VARIABLES).forEach { localAlbumDao.deleteByIds(it) }

        val previousArtistIds = localArtistDao.getAllIds().toSet()
        val currentArtistIds = result.artists.map { it.artistId }.toSet()
        localArtistDao.upsertAll(result.artists)
        val removedArtistIds = (previousArtistIds - currentArtistIds).toList()
        removedArtistIds.chunked(SQLITE_MAX_VARIABLES).forEach { localArtistDao.deleteByIds(it) }
    }

    private companion object {
        /**
         * Max ids per `WHERE id IN (:ids)` statement. Kept well under
         * SQLite's SQLITE_MAX_VARIABLE_NUMBER (999 on the SQLite shipped with
         * API < 30) so a bulk reconcile never overflows a single statement.
         */
        const val SQLITE_MAX_VARIABLES = 900
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
