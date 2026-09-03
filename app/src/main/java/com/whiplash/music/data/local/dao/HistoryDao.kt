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

    /**
     * Most recent play per distinct track, newest first — YOUTUBE/DOWNLOAD
     * only.
     *
     * Real, reported design gap: Speed dial and the full History screen
     * are both meant to be an *online* listening history (per the app's
     * own YouTube-Music-style framing) — the local on-device library is a
     * separate, additional feature (Library > Songs/Albums/Artists, its
     * own tab) with no "recently played" concept of its own. Recording
     * every [com.whiplash.music.data.local.entity.MediaSource.LOCAL] play
     * into the same `history` table this query reads meant local tracks
     * showed up mixed into Speed dial/History right alongside real
     * online plays, which was never the intent. Filtering to
     * YOUTUBE/DOWNLOAD here (rather than at each of this query's several
     * call sites — HomeViewModel's Speed dial/recentlyPlayed,
     * HistoryViewModel, AppStartupPreloader) keeps every one of them
     * correct automatically, since they all read through this single
     * query. [insert] itself is left recording LOCAL plays unchanged —
     * only this read path filters them out — so no data is lost or
     * needs a destructive migration if local "recently played" is ever
     * wanted as its own separate feature later.
     *
     * Real, reported bug (unrelated to the above, fixed at the same
     * time): playing the exact same song via two different surfaces —
     * once as a live-streamed [com.whiplash.music.data.local.entity.MediaSource.YOUTUBE]
     * track (e.g. from Search) and once as an offline
     * [com.whiplash.music.data.local.entity.MediaSource.DOWNLOAD] track
     * (e.g. from the Downloads tab) — produced two separate History rows
     * for what a user experiences as one song, since both source values
     * are literally the same YouTube video id and this query used to
     * GROUP BY the raw (trackId, source) pair. YOUTUBE and DOWNLOAD share
     * the exact same id namespace by construction (a DownloadedTrack's id
     * IS the YouTube video id it was downloaded from — see
     * PlayableItem.DownloadedTrack's own doc), so they're normalized
     * together for grouping. Whether the *most recent* play of that
     * identity happened to be the streamed or downloaded variant is
     * preserved (MAX(id) still picks the actual latest row, source and
     * all) — this only changes what counts as "the same track" for
     * grouping, not which row's data is shown.
     */
    @Query(
        """
        SELECT h.* FROM history h
        WHERE h.source IN ('YOUTUBE', 'DOWNLOAD')
          AND h.id IN (
            SELECT MAX(id) FROM history
            WHERE source IN ('YOUTUBE', 'DOWNLOAD')
            GROUP BY trackId
        )
        ORDER BY h.playedAtEpochMs DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyPlayed(limit: Int = 50): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history")
    suspend fun clear()

    /**
     * Removes every play record for one specific track (section 31:
     * "remove from Speed dial") — matches both the YOUTUBE and DOWNLOAD
     * source rows for [trackId] (same YouTube-video-id identity as
     * [observeRecentlyPlayed]'s grouping above), not just whichever
     * single source [item] happened to have been played as. Without
     * this, "Remove from history"/"Remove from Speed dial" on a song
     * that has *both* a streamed and a downloaded play recorded would
     * only delete one of the two rows, leaving the same song still
     * showing right back in Recently Played/Speed dial from its other
     * source — a real, reported "remove doesn't actually remove it"
     * follow-on of the same underlying duplicate-identity bug.
     */
    @Query(
        """
        DELETE FROM history
        WHERE trackId = :trackId
          AND (
            (:source = 'LOCAL' AND source = 'LOCAL')
            OR (:source != 'LOCAL' AND source IN ('YOUTUBE', 'DOWNLOAD'))
          )
        """
    )
    suspend fun removeAllForTrack(trackId: String, source: com.whiplash.music.data.local.entity.MediaSource)

    @Query("DELETE FROM history WHERE playedAtEpochMs < :olderThanEpochMs")
    suspend fun pruneOlderThan(olderThanEpochMs: Long)
}
