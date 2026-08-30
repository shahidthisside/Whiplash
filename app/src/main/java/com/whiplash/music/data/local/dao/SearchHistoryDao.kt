package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whiplash.music.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    /** Most recently searched queries first. Re-searching an existing query bumps it back to the top (see [upsert]). */
    @Query("SELECT * FROM search_history ORDER BY searchedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    /** Replaces any existing row for the same query, so re-searching bumps its timestamp instead of creating a duplicate. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun remove(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
