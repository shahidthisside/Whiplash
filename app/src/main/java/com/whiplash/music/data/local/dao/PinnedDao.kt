package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whiplash.music.data.local.entity.MediaSource
import com.whiplash.music.data.local.entity.PinnedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedDao {

    @Query("SELECT * FROM pinned_speed_dial ORDER BY pinnedAtEpochMs DESC")
    fun observeAll(): Flow<List<PinnedEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM pinned_speed_dial WHERE trackId = :trackId AND source = :source)")
    fun observeIsPinned(trackId: String, source: MediaSource): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(pinned: PinnedEntity)

    @Query("DELETE FROM pinned_speed_dial WHERE trackId = :trackId AND source = :source")
    suspend fun remove(trackId: String, source: MediaSource)
}
