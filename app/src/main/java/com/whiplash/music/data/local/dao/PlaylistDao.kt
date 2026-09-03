package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.whiplash.music.data.local.entity.PlaylistEntity
import com.whiplash.music.data.local.entity.PlaylistTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, description = :description, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun rename(id: Long, name: String, description: String?, updatedAtEpochMs: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeTracks(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    /**
     * Real, reported crash: [addTrack] had no duplicate check, so adding
     * the same song to the same playlist twice (e.g. via "Add to
     * playlist" from two different screens, or twice from the same
     * screen before the first add's toast even appeared) inserted two
     * separate rows at different positions with the same trackId. Every
     * screen that displays a playlist's tracks
     * (PlayableItemsList.itemsIndexed) keys each row by
     * "${item.source}:${item.id}" — a real Compose crash
     * (IllegalArgumentException: "Key ... was already used") the moment
     * a playlist ever contained the same track twice. [addTrack] now
     * checks this first and no-ops if the track is already present,
     * rather than relying on a UNIQUE constraint + OnConflict (which
     * would need to silently pick one of the two rows to keep in a way
     * that isn't easily made position-stable) or a Room migration.
     */
    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun containsTrack(playlistId: Long, trackId: String): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND position = :position")
    suspend fun removeTrackAt(playlistId: Long, position: Int)

    /**
     * Removes a track by id rather than by position — used by the
     * playlist-scoped "Remove from playlist"/"Move to other playlist"
     * actions (see LibraryRepository.removeTrackFromPlaylist). Position-
     * based removal is unsafe for that use case: the *displayed* track
     * list a user actually sees (LibraryRepository.observePlaylistTracks,
     * via flatMapResolve) silently drops any track whose underlying
     * source row no longer resolves (e.g. a removed download), so its
     * on-screen list index can diverge from this table's own `position`
     * column. Removing by trackId is exact regardless of that drift.
     */
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearTracks(playlistId: Long)

    @Transaction
    suspend fun addTrack(playlistId: Long, trackId: String, source: com.whiplash.music.data.local.entity.MediaSource, addedAtEpochMs: Long): Boolean {
        if (containsTrack(playlistId, trackId) > 0) return false
        val position = nextPosition(playlistId)
        insertTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                position = position,
                trackId = trackId,
                source = source,
                addedAtEpochMs = addedAtEpochMs,
            )
        )
        return true
    }
}
