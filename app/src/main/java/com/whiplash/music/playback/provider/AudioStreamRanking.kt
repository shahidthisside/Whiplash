package com.whiplash.music.playback.provider

/**
 * 5.6 Prefer the plain music audio.
 *
 * YouTube can list the same song's audio several times: the original track,
 * dubbed tracks in other languages, an audio-described track (a narrator
 * talking over the music), "secondary" tracks, and formats that aren't a
 * plain downloadable file (DASH/HLS manifests). This narrows the list to the
 * best kind that's actually present, in this order:
 *
 * 1. the original track (or a video with only one, unlabelled track)
 * 2. a dubbed / secondary track
 * 3. an audio-described track
 *
 * and within that, plain progressive files over manifests.
 *
 * It only ever narrows, never empties: whatever YouTube offers, at least one
 * stream comes back, so no song is ever skipped because of this. It works
 * only on the list the app already fetched, so there's no extra network call.
 */
object AudioStreamRanking {

    enum class Kind { ORIGINAL, UNLABELLED, DUBBED, SECONDARY, DESCRIPTIVE }

    /** The few facts ranking needs about one stream. */
    data class Candidate<T>(
        val stream: T,
        val kind: Kind,
        val progressive: Boolean,
        val hasUrl: Boolean,
    )

    /**
     * The preferred pool from [candidates], never empty if [candidates] isn't.
     * Streams with no URL are dropped unless nothing else is left.
     */
    fun <T> preferredPool(candidates: List<Candidate<T>>): List<T> {
        if (candidates.isEmpty()) return emptyList()
        val playable = candidates.filter { it.hasUrl }.ifEmpty { candidates }
        val byKind = listOf(
            setOf(Kind.ORIGINAL, Kind.UNLABELLED),
            setOf(Kind.DUBBED, Kind.SECONDARY),
            setOf(Kind.DESCRIPTIVE),
        ).firstNotNullOfOrNull { kinds -> playable.filter { it.kind in kinds }.takeIf { it.isNotEmpty() } }
            ?: playable
        val progressive = byKind.filter { it.progressive }.ifEmpty { byKind }
        return progressive.map { it.stream }
    }
}
