// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.whiplash.music.data.local.entity.PlayEventEntity

/** Per-artist feedback totals over a window. */
data class ArtistFeedback(val artistKey: String, val plays: Int, val skips: Int, val completes: Int)

/** A song the listener keeps finishing. */
data class TopTrack(val trackId: String, val title: String, val artist: String, val completes: Int, val lastAt: Long)

/** How often an artist's songs were detected as each language. */
data class ArtistLanguage(val artistKey: String, val language: String, val n: Int)

@Dao
interface PlayEventDao {
    @Insert
    suspend fun insert(event: PlayEventEntity)

    @Query(
        """
        SELECT artistKey, COUNT(*) AS plays, SUM(skipped) AS skips, SUM(completed) AS completes
        FROM play_events WHERE startedAtEpochMs >= :sinceMs AND artistKey != ''
        GROUP BY artistKey
        """
    )
    suspend fun artistFeedback(sinceMs: Long): List<ArtistFeedback>

    /** Tracks skipped at least [minSkips] times and never completed since [sinceMs]. */
    @Query(
        """
        SELECT trackId FROM play_events WHERE startedAtEpochMs >= :sinceMs
        GROUP BY trackId HAVING SUM(skipped) >= :minSkips AND SUM(completed) = 0
        """
    )
    suspend fun rejectedTrackIds(sinceMs: Long, minSkips: Int): List<String>

    @Query("SELECT DISTINCT trackId FROM play_events WHERE startedAtEpochMs >= :sinceMs")
    suspend fun playedTrackIdsSince(sinceMs: Long): List<String>

    @Query(
        """
        SELECT artistKey, language, COUNT(*) AS n FROM play_events
        WHERE language IS NOT NULL AND artistKey IN (:artistKeys)
        GROUP BY artistKey, language
        """
    )
    suspend fun artistLanguages(artistKeys: List<String>): List<ArtistLanguage>

    /**
     * Online songs the listener finished most (recent finishes count more
     * via [sinceMs]), for seeding Quick Picks radios. Never includes songs
     * they also keep skipping.
     */
    @Query(
        """
        SELECT trackId, title, artist, SUM(completed) AS completes, MAX(startedAtEpochMs) AS lastAt
        FROM play_events WHERE startedAtEpochMs >= :sinceMs AND source IN ('YOUTUBE', 'DOWNLOAD')
        GROUP BY trackId HAVING SUM(completed) >= 1 AND SUM(skipped) <= SUM(completed)
        ORDER BY completes DESC, lastAt DESC LIMIT :limit
        """
    )
    suspend fun topCompleted(sinceMs: Long, limit: Int): List<TopTrack>

    @Query("SELECT * FROM play_events ORDER BY startedAtEpochMs DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<PlayEventEntity>

    @Query("DELETE FROM play_events WHERE startedAtEpochMs < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)

    /** Drops all but the newest [limit] events, so the log can't grow without bound. */
    @Query("DELETE FROM play_events WHERE id NOT IN (SELECT id FROM play_events ORDER BY startedAtEpochMs DESC LIMIT :limit)")
    suspend fun keepNewest(limit: Int)

    /** Songs finished since [sinceMs]; Quick Picks refreshes after a few. */
    @Query("SELECT COUNT(*) FROM play_events WHERE completed = 1 AND startedAtEpochMs >= :sinceMs")
    suspend fun completedSince(sinceMs: Long): Int

    @Query("SELECT COUNT(*) FROM play_events")
    suspend fun count(): Int

    @Query("SELECT * FROM play_events ORDER BY startedAtEpochMs")
    suspend fun all(): List<PlayEventEntity>

    @Insert
    suspend fun insertAll(events: List<PlayEventEntity>)

    @Query("DELETE FROM play_events")
    suspend fun clear()
}
