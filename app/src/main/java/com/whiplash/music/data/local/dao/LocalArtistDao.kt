package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whiplash.music.data.local.entity.LocalArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalArtistDao {

    @Query("SELECT * FROM local_artists ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<LocalArtistEntity>>

    @Query("SELECT * FROM local_artists WHERE artistId = :artistId")
    suspend fun getById(artistId: Long): LocalArtistEntity?

    @Query("SELECT artistId FROM local_artists")
    suspend fun getAllIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(artists: List<LocalArtistEntity>)

    @Query("DELETE FROM local_artists WHERE artistId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
