package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whiplash.music.data.local.entity.FavoriteEntity
import com.whiplash.music.data.local.entity.MediaSource
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY addedAtEpochMs DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId AND source = :source)")
    fun observeIsFavorite(trackId: String, source: MediaSource): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId AND source = :source")
    suspend fun remove(trackId: String, source: MediaSource)
}
