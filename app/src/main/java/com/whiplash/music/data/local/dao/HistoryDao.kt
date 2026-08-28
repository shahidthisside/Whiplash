package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.whiplash.music.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Insert
    suspend fun insert(entry: HistoryEntity)

    /** Most recent play per distinct track, newest first. */
    @Query(
        """
        SELECT h.* FROM history h
        WHERE h.id IN (
            SELECT MAX(id) FROM history GROUP BY trackId, source
        )
        ORDER BY h.playedAtEpochMs DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyPlayed(limit: Int = 50): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history")
    suspend fun clear()

    /** Removes every play record for one specific track (section 31: "remove from Speed dial"). */
    @Query("DELETE FROM history WHERE trackId = :trackId AND source = :source")
    suspend fun removeAllForTrack(trackId: String, source: com.whiplash.music.data.local.entity.MediaSource)

    @Query("DELETE FROM history WHERE playedAtEpochMs < :olderThanEpochMs")
    suspend fun pruneOlderThan(olderThanEpochMs: Long)
}
