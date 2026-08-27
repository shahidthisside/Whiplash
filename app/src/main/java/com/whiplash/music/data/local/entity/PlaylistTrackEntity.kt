package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One track entry within a playlist. References a track by [trackId] plus
 * [source] rather than a foreign key into a single table, since a playlist
 * can mix [MediaSource.YOUTUBE] and [MediaSource.LOCAL] tracks (section 27).
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("playlistId"), Index("trackId")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val position: Int,
    val trackId: String,
    val source: MediaSource,
    val addedAtEpochMs: Long,
)
