// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

/**
 * 4.3: the Explore catalogue. NewPipeExtractor has no working charts or
 * mood feed (YouTube removed its public Trending/Charts page), so every
 * Explore surface is an honest, labelled search: "Charts" are chart
 * playlists found by search, a mood page is playlists and songs searched for
 * that mood. Explore is a fixed set; it does not page.
 */
data class ExploreGenre(
    val id: String,
    val title: String,
    /** Search used for this genre's playlists. */
    val playlistQuery: String,
    /** Search used for this genre's songs. */
    val songQuery: String,
    /** Index into the UI's tile palette. */
    val colorIndex: Int,
)

// Original work of Shahid Ansari (-SA). Not licensed for reuse.
val EXPLORE_GENRES: List<ExploreGenre> = listOf(
    genre("chill", "Chill", 0),
    genre("workout", "Workout", 1),
    genre("focus", "Focus", 2),
    genre("party", "Party", 3),
    genre("romance", "Romance", 4),
    genre("sleep", "Sleep", 5),
    genre("commute", "Commute", 6),
    genre("feel-good", "Feel good", 7),
    genre("pop", "Pop", 8),
    genre("hip-hop", "Hip-hop", 9),
    genre("rock", "Rock", 10),
    genre("bollywood", "Bollywood", 11),
    genre("punjabi", "Punjabi", 0),
    genre("lofi", "Lo-fi", 1),
    genre("edm", "Dance & EDM", 2),
    genre("rnb", "R&B & Soul", 3),
    genre("indie", "Indie", 4),
    genre("jazz", "Jazz", 5),
    genre("classical", "Classical", 6),
    genre("kpop", "K-pop", 7),
)

private fun genre(id: String, title: String, colorIndex: Int) = ExploreGenre(
    id = id,
    title = title,
    playlistQuery = "$title playlist",
    songQuery = "$title songs",
    colorIndex = colorIndex,
)

fun exploreGenre(id: String): ExploreGenre? = EXPLORE_GENRES.firstOrNull { it.id == id }

/** Explore's two fixed shelves (albums, then chart playlists). */
val EXPLORE_NEW_RELEASES = ShelfSpec(ShelfKind.ALBUMS, "New releases", "new album 2026")
val EXPLORE_CHARTS = ShelfSpec(ShelfKind.PLAYLISTS, "Charts", "top 100 songs chart 2026")
