// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.whiplash.music.data.local.entity.MediaSource
import com.whiplash.music.data.local.entity.ReplayTallyEntity
import kotlinx.coroutines.flow.Flow

/** 4.7 Monthly Replay tally — see [ReplayTallyEntity]. */
@Dao
interface ReplayTallyDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: ReplayTallyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ReplayTallyEntity>)

    @Query(
        """
        UPDATE replay_tally
        SET plays = plays + 1, source = :source, title = :title, artist = :artist,
            artworkUrl = COALESCE(:artworkUrl, artworkUrl), durationMs = :durationMs,
            lastPlayedAtEpochMs = :atEpochMs
        WHERE monthKey = :monthKey AND trackId = :trackId
        """
    )
    suspend fun bumpPlay(
        monthKey: String,
        trackId: String,
        source: MediaSource,
        title: String,
        artist: String,
        artworkUrl: String?,
        durationMs: Long,
        atEpochMs: Long,
    )

    @Query("UPDATE replay_tally SET listenedMs = listenedMs + :ms WHERE monthKey = :monthKey AND trackId = :trackId")
    suspend fun addListened(monthKey: String, trackId: String, ms: Long)

    /** Counts one play, creating the month's row for the song if needed. */
    @Transaction
    suspend fun recordPlay(row: ReplayTallyEntity) {
        insertIgnore(row.copy(plays = 0, listenedMs = 0))
        bumpPlay(row.monthKey, row.trackId, row.source, row.title, row.artist, row.artworkUrl, row.durationMs, row.lastPlayedAtEpochMs)
    }

    /** Adds listened time; [row] seeds the month's row if the song has none yet (plays stays 0). */
    @Transaction
    suspend fun recordListened(row: ReplayTallyEntity, ms: Long) {
        insertIgnore(row.copy(plays = 0, listenedMs = 0))
        addListened(row.monthKey, row.trackId, ms)
    }

    /**
     * One month's rows. Artwork prefers the song cache's current URL, which
     * PlaybackController upgrades to the sharpest cover that actually loads,
     * over the snapshot taken at play time (often a small thumbnail).
     */
    @Query(
        """
        SELECT t.monthKey, t.trackId, t.source, t.title, t.artist,
               COALESCE(s.artworkUrl, t.artworkUrl) AS artworkUrl,
               t.durationMs, t.plays, t.listenedMs, t.lastPlayedAtEpochMs
        FROM replay_tally t LEFT JOIN songs s ON s.id = t.trackId
        WHERE t.monthKey = :monthKey
        """
    )
    fun observeMonth(monthKey: String): Flow<List<ReplayTallyEntity>>

    /** Months that have at least one counted play, newest first. */
    @Query("SELECT DISTINCT monthKey FROM replay_tally WHERE plays > 0 ORDER BY monthKey DESC")
    fun observeMonths(): Flow<List<String>>

    @Query("SELECT * FROM replay_tally")
    suspend fun getAll(): List<ReplayTallyEntity>

    @Query("DELETE FROM replay_tally WHERE trackId = :trackId")
    suspend fun removeTrack(trackId: String)

    @Query("DELETE FROM replay_tally")
    suspend fun clear()

    @Query("DELETE FROM replay_tally WHERE monthKey = :monthKey AND trackId = :trackId")
    suspend fun deleteRow(monthKey: String, trackId: String)
}
