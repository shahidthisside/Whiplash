package com.whiplash.music.domain.model

/**
 * 4.1: which shelves the Home feed shows, worked out from the listener's own
 * history. Shelves are honest searches ("Albums by X" is a real album search
 * for X), not a claim of YouTube Music's personalised feed, which this app has
 * no access to. Pure Kotlin, unit-tested.
 */
enum class ShelfKind { ALBUMS, PLAYLISTS, SONGS }

data class ShelfSpec(val kind: ShelfKind, val title: String, val query: String) {
    /** Stable key for lists and caching. */
    val key: String get() = "${kind.name}:$query"
}

/** First credited artist only ("A, B & C feat. D" → "A"), trimmed; blank stays blank. */
fun primaryArtistName(raw: String): String = raw
    .split(',', '&', '/', ';')
    .first()
    .replace(Regex("""\s+(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\s+-\s+Topic$""", RegexOption.IGNORE_CASE), "")
    .trim()

/**
 * Artists ranked by how often they appear in [playedArtists] (most-recent
 * first), ties going to whoever was played more recently. Case-insensitive,
 * using each artist's primary name; returns at most [max].
 */
fun rankArtists(playedArtists: List<String>, max: Int): List<String> {
    data class Tally(val name: String, var count: Int, val firstIndex: Int)
    val tallies = LinkedHashMap<String, Tally>()
    playedArtists.forEachIndexed { index, raw ->
        val name = primaryArtistName(raw)
        if (name.isEmpty()) return@forEachIndexed
        val key = name.lowercase()
        val t = tallies.getOrPut(key) { Tally(name, 0, index) }
        t.count++
    }
    return tallies.values
        .sortedWith(compareByDescending<Tally> { it.count }.thenBy { it.firstIndex })
        .take(max)
        .map { it.name }
}

/** Shelves for a listener with no history yet (or when every personal shelf came back empty). */
val STARTER_SHELVES = listOf(
    ShelfSpec(ShelfKind.ALBUMS, "New albums", "new album 2026"),
    ShelfSpec(ShelfKind.PLAYLISTS, "Top hits playlists", "top hits 2026"),
    ShelfSpec(ShelfKind.SONGS, "Popular songs", "popular songs 2026"),
)

/**
 * Two shelves per ranked artist — their albums, then playlists built around
 * them — followed by the starter shelves, so the feed never simply ends after
 * a listener's few favourite artists. Duplicated queries are dropped.
 */
fun planHomeShelves(rankedArtists: List<String>): List<ShelfSpec> {
    val personal = rankedArtists.flatMap { artist ->
        listOf(
            ShelfSpec(ShelfKind.ALBUMS, "Albums by $artist", artist),
            ShelfSpec(ShelfKind.PLAYLISTS, "Playlists with $artist", "$artist mix"),
        )
    }
    return (personal + STARTER_SHELVES).distinctBy { it.key }
}

/** How many shelves one "load more" fetches. */
const val SHELVES_PER_PAGE = 2
