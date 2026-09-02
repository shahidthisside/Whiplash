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
