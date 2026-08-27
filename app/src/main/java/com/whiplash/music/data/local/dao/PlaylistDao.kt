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

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND position = :position")
    suspend fun removeTrackAt(playlistId: Long, position: Int)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearTracks(playlistId: Long)

    @Transaction
    suspend fun addTrack(playlistId: Long, trackId: String, source: com.whiplash.music.data.local.entity.MediaSource, addedAtEpochMs: Long) {
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
    }
}
