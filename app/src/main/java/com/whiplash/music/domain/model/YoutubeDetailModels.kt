package com.whiplash.music.domain.model

/** Real track-list detail for an album or playlist (section 39: album page). */
data class YoutubePlaylistDetail(
    val url: String,
    val title: String,
    val uploaderName: String?,
    val artworkUrl: String?,
    val tracks: List<PlayableItem.YoutubeTrack>,
)

/** Real detail for an artist/channel (section 40: artist page). */
data class YoutubeArtistDetail(
    val channelUrl: String,
    val name: String,
    val artworkUrl: String?,
    val subscriberCount: Long?,
    val description: String?,
    /** Popular songs from the channel's real "tracks" tab, when the channel exposes one. Empty (not fabricated) if unavailable. */
    val popularSongs: List<PlayableItem.YoutubeTrack>,
    /** Real albums from the channel's "albums" tab, when available. Empty if unavailable. */
    val albums: List<YoutubePlaylistResult>,
)
