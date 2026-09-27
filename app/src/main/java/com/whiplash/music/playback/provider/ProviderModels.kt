package com.whiplash.music.playback.provider

/**
 * A resolved, directly-playable audio stream for a YouTube track, as
 * produced by a [PlaybackProvider]. Deliberately minimal — only what
 * [com.whiplash.music.playback.controller.PlayableItemMediaItemMapper]
 * needs to build a Media3 MediaItem.
 */
data class ResolvedStream(
    val streamUrl: String,
    val mimeType: String?,
    val bitrateBps: Int?,
    val expiresAtEpochMs: Long?,
    val providerId: String,
    /**
     * The highest-resolution thumbnail available from the full per-video
     * response used to resolve [streamUrl]. This is deliberately distinct
     * from (and normally higher-resolution than) the thumbnail a search
     * result list item exposes, since YouTube's search response only
     * includes a lightweight thumbnail (e.g. 480x360 hqdefault) while the
     * full watch-page response often has a much larger one (e.g. 1280x720
     * maxresdefault) available. [PlaybackController] uses this to upgrade
     * [PlaybackState.currentItem]'s artwork once a track starts resolving,
     * so the full player shows crisp artwork instead of an upscaled
     * search-result thumbnail.
     */
    val resolvedArtworkUrl: String?,
    /**
     * Every thumbnail size the response lists, largest first. The largest
     * (maxresdefault) does not exist for every video — YouTube answers 404 —
     * so callers try these in order and use the first that actually loads.
     */
    val resolvedArtworkCandidates: List<String> = listOfNotNull(resolvedArtworkUrl),
)

/**
 * Richer metadata about a track, independent of the resolved stream itself
 * (title/artist/duration/artwork). Kept separate from [ResolvedStream] per
 * the PlaybackProvider.getPlayerInfo / getStream split in CLAUDE.md section 7.
 */
data class ProviderPlayerInfo(
    val songId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    /** All thumbnail sizes, largest first (see ResolvedStream.resolvedArtworkCandidates). */
    val artworkCandidates: List<String> = listOfNotNull(artworkUrl),
    /** The video's YouTube category (e.g. "Music", "Comedy", "Entertainment"), if available. */
    val category: String? = null,
)
