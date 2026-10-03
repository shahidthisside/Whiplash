// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

/**
 * A playlist's cover choice, stored in the playlist's `artworkUrl` column.
 *
 * - [Auto]: no custom cover; the cover is built from the playlist's songs
 *   (stored as null, which is also what every existing playlist has).
 * - [Songs]: covers of 1–4 chosen songs, drawn as one image or a mosaic.
 * - [Image]: a picture from the gallery, copied into app storage.
 */
sealed interface PlaylistArt {
    data object Auto : PlaylistArt
    data class Songs(val artworks: List<String>) : PlaylistArt
    data class Image(val uri: String) : PlaylistArt

    companion object {
        const val MAX_SONGS = 4
        private const val SONGS_PREFIX = "songs:"
        private const val IMAGE_PREFIX = "image:"

        /** Reads a stored value. Anything unrecognised falls back to [Auto]. */
        fun parse(stored: String?): PlaylistArt {
            if (stored.isNullOrBlank()) return Auto
            return when {
                stored.startsWith(SONGS_PREFIX) -> {
                    val uris = stored.removePrefix(SONGS_PREFIX).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                    if (uris.isEmpty()) Auto else Songs(uris.take(MAX_SONGS))
                }
                stored.startsWith(IMAGE_PREFIX) -> stored.removePrefix(IMAGE_PREFIX).trim().takeIf { it.isNotEmpty() }?.let(::Image) ?: Auto
                // A bare URL (for example from an older backup) is treated as an image.
                stored.startsWith("http") || stored.startsWith("file:") || stored.startsWith("content:") -> Image(stored)
                else -> Auto
            }
        }

        /** The value to store; null for [Auto]. */
        fun encode(art: PlaylistArt): String? = when (art) {
            Auto -> null
            is Songs -> art.artworks.filter { it.isNotBlank() }.take(MAX_SONGS)
                .takeIf { it.isNotEmpty() }?.joinToString(separator = "\n", prefix = SONGS_PREFIX)
            is Image -> IMAGE_PREFIX + art.uri
        }
    }
}
