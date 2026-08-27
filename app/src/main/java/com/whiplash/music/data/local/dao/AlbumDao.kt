package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whiplash.music.data.local.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getById(id: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE id = :id")
    fun observeById(id: String): Flow<AlbumEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(album: AlbumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE cachedAtEpochMs < :olderThanEpochMs")
    suspend fun pruneOlderThan(olderThanEpochMs: Long)
}
