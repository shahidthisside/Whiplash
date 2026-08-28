package com.whiplash.music.domain.model

/**
 * A YouTube Music album or playlist search result (section 32/37/39).
 * NewPipeExtractor's MUSIC_ALBUMS and MUSIC_PLAYLISTS search filters both
 * return [org.schabi.newpipe.extractor.playlist.PlaylistInfoItem] — the
 * distinction between "album" and "playlist" here is which filter/section
 * the result came from, not a field on the underlying item itself (real
 * data, not fabricated: confirmed via direct inspection of NewPipeExtractor
 * v0.26.5's actual class definitions before building this).
 */
data class YoutubePlaylistResult(
    val url: String,
    val title: String,
    val uploaderName: String?,
    val artworkUrl: String?,
    val trackCount: Long?,
    val isAlbum: Boolean,
)

/** A YouTube channel/artist search result (section 32/37/40). */
data class YoutubeArtistResult(
    val channelUrl: String,
    val name: String,
    val artworkUrl: String?,
    val subscriberCount: Long?,
)
