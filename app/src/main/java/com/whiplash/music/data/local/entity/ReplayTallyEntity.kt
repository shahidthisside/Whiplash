package com.whiplash.music.data.local.entity

import androidx.room.Entity

/**
 * 4.7 Monthly Replay: one row per track per calendar month (local time,
 * [monthKey] = "yyyy-MM").
 *
 * Kept separate from `history` on purpose: history is capped at the 200
 * most recent plays, which would make a month's recap shrink as you keep
 * listening. This table only ever holds one small row per song per month.
 *
 * Title/artist/artwork are a snapshot taken when the song was played, so a
 * past month still reads correctly even if the song's cached metadata is
 * gone later. [listenedMs] is real time spent playing (see
 * PlaybackController's listening tracker), not plays × song length.
 */
@Entity(tableName = "replay_tally", primaryKeys = ["monthKey", "trackId"])
data class ReplayTallyEntity(
    val monthKey: String,
    val trackId: String,
    val source: MediaSource,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val plays: Int,
    val listenedMs: Long,
    val lastPlayedAtEpochMs: Long,
)
