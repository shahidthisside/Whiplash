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

    /**
     * Real, reported design gap: Speed dial is meant to be an *online*
     * listening history — the local on-device library is a separate,
     * additional feature with no Speed dial concept of its own. Filters
     * out LOCAL here (rather than only at the UI's "Pin to Speed dial"
     * action, see PlayableItemsList's own doc) as a second, independent
     * safety net: any LOCAL row that ever ends up in this table for any
     * reason (a future code path, or a row pinned before this fix
     * existed) still never surfaces in Speed dial, with no destructive
     * migration required to remove it.
     */
    @Query("SELECT * FROM pinned_speed_dial WHERE source IN ('YOUTUBE', 'DOWNLOAD') ORDER BY pinnedAtEpochMs DESC")
    fun observeAll(): Flow<List<PinnedEntity>>

    /**
     * Same YOUTUBE/DOWNLOAD identity normalization as [com.whiplash.music.data.local.dao.HistoryDao]'s
     * own doc explains in full — a DownloadedTrack's id is the same
     * YouTube video id it was downloaded from, so pinning it should
     * report/toggle the exact same pinned state a YoutubeTrack pin of
     * that same id would, not a separate one. LOCAL is never normalized
     * with the other two (a genuinely different id namespace).
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM pinned_speed_dial
            WHERE trackId = :trackId
              AND (
                (:source = 'LOCAL' AND source = 'LOCAL')
                OR (:source != 'LOCAL' AND source IN ('YOUTUBE', 'DOWNLOAD'))
              )
        )
        """
    )
    fun observeIsPinned(trackId: String, source: MediaSource): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(pinned: PinnedEntity)

    /** See [observeIsPinned]'s own doc on the YOUTUBE/DOWNLOAD normalization — removes whichever of the two source rows actually exists for this id, not just the exact [source] passed in. */
    @Query(
        """
        DELETE FROM pinned_speed_dial
        WHERE trackId = :trackId
          AND (
            (:source = 'LOCAL' AND source = 'LOCAL')
            OR (:source != 'LOCAL' AND source IN ('YOUTUBE', 'DOWNLOAD'))
          )
        """
    )
    suspend fun remove(trackId: String, source: MediaSource)
}
