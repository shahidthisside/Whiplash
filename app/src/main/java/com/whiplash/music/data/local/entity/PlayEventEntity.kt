package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One play of one track, with how it ended. The recommendation system's
 * feedback signal: History only says a song started, this says whether the
 * listener kept it (completed) or rejected it (skipped early).
 *
 * [origin] is USER (picked by the listener) or AUTOPLAY (added by radio);
 * [endReason] is COMPLETED, SKIPPED (Next pressed), REPLACED (another song
 * picked), PREVIOUS or STOPPED. [skipped] and [completed] are derived once
 * at write time so queries stay trivial.
 */
@Entity(
    tableName = "play_events",
    indices = [Index("trackId"), Index("artistKey"), Index("startedAtEpochMs")],
)
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val source: MediaSource,
    val title: String,
    val artist: String,
    /** Normalised artist (see RadioRules.artistKey) for grouping. */
    val artistKey: String,
    /** Detected language code (pa, hi, ta, en…) or null when unknown. */
    val language: String?,
    val origin: String,
    /** Seed track id of the radio session that queued it, if any. */
    val radioSeedId: String?,
    val startedAtEpochMs: Long,
    val playedMs: Long,
    val durationMs: Long,
    val endReason: String,
    val skipped: Boolean,
    val completed: Boolean,
)
