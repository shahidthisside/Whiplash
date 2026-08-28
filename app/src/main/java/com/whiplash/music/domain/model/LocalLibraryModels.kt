package com.whiplash.music.domain.model

/** Domain-layer representation of a device-local album. */
data class LocalAlbum(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val year: Int?,
)

/** Domain-layer representation of a device-local artist. */
data class LocalArtist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
)
