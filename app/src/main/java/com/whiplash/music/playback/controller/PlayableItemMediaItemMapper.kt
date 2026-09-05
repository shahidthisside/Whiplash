package com.whiplash.music.playback.controller

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.whiplash.music.domain.model.PlayableItem
import java.io.File

/**
 * Resolves a [PlayableItem] to a Media3 [MediaItem].
 *
 * For [PlayableItem.LocalTrack], the MediaStore content URI is used
 * directly as the playable source. For [PlayableItem.YoutubeTrack], no
 * URI is embedded in the domain model itself (a resolved stream expires
 * and must be refreshed by the provider layer) — the caller resolves one
 * via [com.whiplash.music.playback.provider.PlaybackManager] first and
 * passes it in as [resolvedStreamUrl].
 *
 * [PlayableItem.YoutubeTrack] also gets a stable [MediaItem.Builder.setCustomCacheKey]
 * derived from the track's own id (never the resolved stream URL itself).
 * YouTube's resolved `googlevideo.com` URLs carry a signature/expiry that
 * changes on every fresh resolve of the *same* video — without a stable
 * key, [androidx.media3.datasource.cache.CacheDataSource]'s default
 * cache-key factory falls back to the request URI itself, so replaying an
 * already-fully-cached track (whose stream was re-resolved to a new URL
 * since it was last played) was a guaranteed cache miss: it re-downloaded
 * into a brand new cache entry rather than reading the existing one,
 * despite the audio bytes for that track already being on disk. This is
 * exactly why a cached song still took time to reload on replay.
 */
object PlayableItemMediaItemMapper {

    fun toMediaItem(item: PlayableItem, resolvedStreamUrl: String? = null): MediaItem {
        // DownloadedTrack's artworkUri is a plain local file path (see
        // DownloadManager), not a URI string — Uri.parse (what the
        // String.toUri() extension does) would produce a schemeless URI
        // that Coil/MediaMetadata can't load. Every other source's
        // artworkUri is already a proper content://, https://, etc URI.
        val artworkUri = when (item) {
            is PlayableItem.DownloadedTrack -> item.artworkUri?.let { Uri.fromFile(File(it)) }
            else -> item.artworkUri?.toUri()
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .setAlbumTitle(item.album)
            .setArtworkUri(artworkUri)
            // Publish the duration we already know, which every PlayableItem
            // carries before playback even starts.
            //
            // Media3's legacy bridge — LegacyConversions
            // .convertToMediaMetadataCompat, which is what the lock screen,
            // Bluetooth/AVRCP and OEM media surfaces actually read — takes
            // the player's own duration when it has one, falls back to this
            // field when it doesn't, and writes METADATA_KEY_DURATION = -1
            // when neither is available. ExoPlayer only learns the duration
            // once it has parsed enough of the container, so any metadata
            // published before that point sent -1, leaving the lock screen
            // with no scale to draw: blank elapsed/remaining and a progress
            // bar that sat still until some later state change forced a
            // refresh. Whether that race was won or lost varied with network
            // speed, cache hits and main-thread load, which is why the
            // symptom came and went and looked like a vendor rendering quirk.
            //
            // Setting it here removes the race without ever overriding the
            // player: its duration still wins whenever it knows one, so this
            // only ever replaces -1 with a real value. Nothing in the app
            // reads this field — the in-app seek bar uses the player's
            // duration directly — so this is confined to what external
            // surfaces see.
            //
            // Guarded on a positive value: an unknown/zero duration must stay
            // unset so the bridge keeps reporting -1 ("unknown") rather than
            // asserting a zero-length track, which would peg a progress bar
            // at its end instead of leaving it blank.
            .apply { if (item.durationMs > 0) setDurationMs(item.durationMs) }
            .build()

        val builder = MediaItem.Builder()
            .setMediaId(mediaIdOf(item))
            .setMediaMetadata(metadata)

        when (item) {
            is PlayableItem.LocalTrack -> builder.setUri(item.mediaStoreUri.toUri())
            is PlayableItem.DownloadedTrack -> builder.setUri(Uri.fromFile(File(item.fileUri)))
            is PlayableItem.YoutubeTrack -> {
                // Left unset if no stream has been resolved yet; the caller
                // is responsible for resolving one before calling this with
                // a YoutubeTrack (see PlaybackController.playNow).
                resolvedStreamUrl?.let { builder.setUri(it.toUri()) }
                builder.setCustomCacheKey(mediaIdOf(item))
            }
        }

        return builder.build()
    }

    /** Stable id combining source + id so LOCAL/YOUTUBE ids never collide. */
    fun mediaIdOf(item: PlayableItem): String = "${item.source.name}:${item.id}"
}
