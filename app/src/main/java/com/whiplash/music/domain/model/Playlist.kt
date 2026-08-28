package com.whiplash.music.domain.model

/** A user-created playlist (section 38), local-first regardless of what it contains. */
data class Playlist(
    val id: Long,
    val name: String,
    val description: String?,
    val artworkUrl: String?,
)
