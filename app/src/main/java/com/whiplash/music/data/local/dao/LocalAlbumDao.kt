package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whiplash.music.data.local.entity.LocalAlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalAlbumDao {

    @Query("SELECT * FROM local_albums ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<LocalAlbumEntity>>

    @Query("SELECT * FROM local_albums WHERE albumId = :albumId")
    suspend fun getById(albumId: Long): LocalAlbumEntity?

    @Query("SELECT albumId FROM local_albums")
    suspend fun getAllIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(albums: List<LocalAlbumEntity>)

    @Query("DELETE FROM local_albums WHERE albumId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
