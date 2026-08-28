package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whiplash.music.data.local.entity.SearchCacheEntity

@Dao
interface SearchCacheDao {

    @Query("SELECT * FROM search_cache WHERE query = :query")
    suspend fun get(query: String): SearchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchCacheEntity)

    @Query("DELETE FROM search_cache WHERE cachedAtEpochMs < :olderThanEpochMs")
    suspend fun pruneOlderThan(olderThanEpochMs: Long)
}
