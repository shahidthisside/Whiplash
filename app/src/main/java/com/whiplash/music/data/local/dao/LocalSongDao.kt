package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whiplash.music.data.local.entity.LocalSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalSongDao {

    @Query("SELECT * FROM local_songs ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<LocalSongEntity>>

    @Query("SELECT * FROM local_songs WHERE albumId = :albumId ORDER BY trackNumber ASC, title COLLATE NOCASE ASC")
    fun observeByAlbum(albumId: Long): Flow<List<LocalSongEntity>>

    @Query("SELECT * FROM local_songs WHERE artistId = :artistId ORDER BY title COLLATE NOCASE ASC")
    fun observeByArtist(artistId: Long): Flow<List<LocalSongEntity>>

    @Query(
        """
        SELECT * FROM local_songs
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    fun search(query: String): Flow<List<LocalSongEntity>>

    @Query("SELECT * FROM local_songs WHERE mediaStoreId = :id")
    suspend fun getById(id: Long): LocalSongEntity?

    @Query("SELECT mediaStoreId FROM local_songs")
    suspend fun getAllIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<LocalSongEntity>)

    @Query("DELETE FROM local_songs WHERE mediaStoreId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM local_songs")
    fun observeCount(): Flow<Int>
}
