package com.whiplash.music.recommend

import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.playback.controller.SongLengthClass
import com.whiplash.music.playback.controller.classifySongLength
import com.whiplash.music.playback.controller.isSameSong

/** One page of a radio feed; [next] is an opaque cursor, null at the end. */
class RadioPage(val items: List<PlayableItem.YoutubeTrack>, val next: Any?)

/** Long-term feedback the radio reads from the play-event log. */
interface RadioFeedback {
    /** Tracks the listener keeps skipping and never finishes. */
    suspend fun rejectedTrackIds(): Set<String>

    /** Artists (by [RadioRules.artistKey]) the listener mostly skips. */
    suspend fun blockedArtistKeys(): Set<String>

    /** Songs played very recently, so the radio doesn't repeat them. */
    suspend fun recentlyPlayedIds(): Set<String>

    /** Most common detected language per artist key, learned from past plays. */
    suspend fun artistLanguages(artistKeys: Collection<String>): Map<String, String>
}

/**
 * State of one radio run. The [seed] is the song the listener chose; the
 * radio never re-seeds from whatever autoplay added last (that random walk
 * is what drifted Punjabi into 80s Hindi). When the seed's own YouTube Music
 * radio runs dry it continues from songs the listener *kept* ([kept]),
 * still judged by the same session profile.
 */
class RadioSession(val seed: PlayableItem.YoutubeTrack) {
    internal var feedSeed: PlayableItem.YoutubeTrack = seed
    internal var cursor: Any? = null
    internal var feedStarted = false
    internal var feedDone = false
    internal val usedFeedSeeds = hashSetOf(seed.id)
    internal val usedRelatedSeeds = HashSet<String>()
    internal val pool = ArrayDeque<PlayableItem.YoutubeTrack>()
    internal val seen = hashSetOf(seed.id)
    /** Ids that came from generic "related" (not YT Music radio) and need a Music-category check. */
    internal val unverified = HashSet<String>()
    internal val kept = mutableListOf<PlayableItem.YoutubeTrack>()
    internal val skippedArtists = HashMap<String, Int>()
    val profile = LanguageProfile()
    internal val lengthClass: SongLengthClass = classifySongLength(seed.title)
    internal val seedRetro: Boolean = RadioRules.isRetro(seed.title)
    internal var seedLanguageResolved = false
}

/**
 * Builds autoplay batches for a [RadioSession]:
 *
 * 1. Candidates come from YouTube Music's own radio for the seed
 *    (`RDAMVM<seed>`: music only, built around one song, deduplicated by
 *    YouTube across pages), then radios of songs the listener kept, then
 *    generic related videos as a last resort.
 * 2. Every candidate must pass the guards: not already queued or a
 *    duplicate upload, same length class as the seed (single vs jukebox),
 *    not recently played, not a track or artist the listener rejects, no
 *    retro compilations unless the seed is one, and in the session's
 *    language ([LanguageProfile]).
 * 3. The batch is spread out so no artist dominates ([RadioRules.applyArtistCap]).
 */
class RadioEngine(
    private val fetchRadio: suspend (seedId: String, cursor: Any?) -> RadioPage,
    private val fetchRelated: suspend (trackId: String) -> List<PlayableItem.YoutubeTrack>,
    private val isMusic: suspend (trackId: String) -> Boolean,
    private val feedback: RadioFeedback,
) {
    var current: RadioSession? = null
        private set

    /** The session for [seed], starting a fresh one when the seed changed. */
    fun sessionFor(seed: PlayableItem.YoutubeTrack): RadioSession {
        val s = current
        if (s != null && s.seed.id == seed.id) return s
        return RadioSession(seed).also { current = it }
    }

    fun reset() { current = null }

    /** The listener picked [track] themselves: strong evidence of what they want now. */
    suspend fun onChosen(session: RadioSession, track: PlayableItem.YoutubeTrack) {
        languageOf(track)?.let { session.profile.add(it.code, if (it.confident) 2.0 else 1.2) }
    }

    /** An autoplay song was played through: it's on target. */
    suspend fun onKept(session: RadioSession, track: PlayableItem.YoutubeTrack) {
        if (session.kept.none { it.id == track.id }) session.kept += track
        languageOf(track)?.let { session.profile.add(it.code, if (it.confident) 1.0 else 0.6) }
    }

    /** An autoplay song was skipped early: steer away from it. */
    suspend fun onSkipped(session: RadioSession, track: PlayableItem.YoutubeTrack) {
        val key = RadioRules.artistKey(track.artist)
        session.skippedArtists[key] = (session.skippedArtists[key] ?: 0) + 1
        languageOf(track)?.let { session.profile.add(it.code, -0.5) }
    }

    /**
     * Up to [count] songs to append after [queue]. Never throws for a
     * network failure; returns fewer (or none) instead.
     */
    suspend fun nextBatch(session: RadioSession, queue: List<PlayableItem>, count: Int): List<PlayableItem.YoutubeTrack> {
        if (!session.seedLanguageResolved) {
            session.seedLanguageResolved = true
            languageOf(session.seed)?.let { session.profile.add(it.code, if (it.confident) 3.0 else 2.0) }
        }
        val queueIds = queue.mapTo(HashSet()) { it.id }
        val existing = queue.filterIsInstance<PlayableItem.YoutubeTrack>().map { it.title to it.durationMs }.toMutableList()
        val rejected = runCatching { feedback.rejectedTrackIds() }.getOrDefault(emptySet())
        val blocked = runCatching { feedback.blockedArtistKeys() }.getOrDefault(emptySet())
        val recent = runCatching { feedback.recentlyPlayedIds() }.getOrDefault(emptySet())

        val accepted = mutableListOf<PlayableItem.YoutubeTrack>()
        var refills = 0
        while (accepted.size < count * 2) {
            if (session.pool.isEmpty()) {
                if (refills >= MAX_REFILLS_PER_BATCH || !refill(session)) break
                refills++
                // Learn languages of the new pool's artists in one query.
                artistLanguageCache.putAll(
                    runCatching {
                        feedback.artistLanguages(session.pool.map { RadioRules.artistKey(it.artist) }.filter { it !in artistLanguageCache })
                    }.getOrDefault(emptyMap()),
                )
            }
            val c = session.pool.removeFirst()
            if (c.id in queueIds || c.id in rejected || c.id in recent) continue
            if (existing.any { (t, d) -> isSameSong(t, d, c.title, c.durationMs) }) continue
            if (classifySongLength(c.title) != session.lengthClass) continue
            val key = RadioRules.artistKey(c.artist)
            if (key in blocked || (session.skippedArtists[key] ?: 0) >= 2) continue
            if (!session.seedRetro && RadioRules.isRetro(c.title)) continue
            val lang = languageOf(c)
            if (!session.profile.allows(lang?.code, lang?.confident == true)) continue
            if (c.id in session.unverified && !runCatching { isMusic(c.id) }.getOrDefault(true)) continue
            accepted += c
            existing += c.title to c.durationMs
        }

        val tail = queue.takeLast(RadioRules.ARTIST_WINDOW).map { RadioRules.artistKey(it.artist) }
        val capped = RadioRules.applyArtistCap(accepted, tail)
        val out = capped.placed.take(count)
        // Good candidates that didn't fit this time go back to the front.
        (capped.placed.drop(count) + capped.deferred).asReversed().forEach { session.pool.addFirst(it) }
        return out
    }

    private val artistLanguageCache = HashMap<String, String>()

    private suspend fun languageOf(track: PlayableItem.YoutubeTrack): LanguageGuess? {
        LanguageDetector.detect(track.title, track.artist)?.let { return it }
        val key = RadioRules.artistKey(track.artist)
        val known = artistLanguageCache[key]
            ?: runCatching { feedback.artistLanguages(listOf(key))[key] }.getOrNull()?.also { artistLanguageCache[key] = it }
        return known?.let { LanguageGuess(it, confident = false) }
    }

    /** Adds unseen candidates to the pool. False when every source is exhausted. */
    private suspend fun refill(session: RadioSession): Boolean {
        repeat(MAX_SOURCE_HOPS) {
            if (!session.feedDone) {
                val page = runCatching {
                    fetchRadio(session.feedSeed.id, if (session.feedStarted) session.cursor else null)
                }.getOrNull()
                if (page == null) {
                    session.feedDone = true
                } else {
                    session.feedStarted = true
                    session.cursor = page.next
                    if (page.next == null) session.feedDone = true
                    val fresh = page.items.filter { session.seen.add(it.id) }
                    // A page of only repeats means this radio has looped.
                    if (fresh.isEmpty()) session.feedDone = true
                    session.pool.addAll(fresh)
                    if (fresh.isNotEmpty()) return true
                }
            }
            // Continue from the most recent song the listener kept.
            val nextSeed = session.kept.lastOrNull { it.id !in session.usedFeedSeeds }
            if (nextSeed != null) {
                session.usedFeedSeeds += nextSeed.id
                session.feedSeed = nextSeed
                session.cursor = null
                session.feedStarted = false
                session.feedDone = false
                return@repeat
            }
            // Last resort: generic related videos of the seed or a kept song.
            val base = (listOf(session.seed) + session.kept.asReversed()).firstOrNull { it.id !in session.usedRelatedSeeds }
                ?: return false
            session.usedRelatedSeeds += base.id
            val related = runCatching { fetchRelated(base.id) }.getOrDefault(emptyList())
            val fresh = related.filter { session.seen.add(it.id) }
            session.unverified += fresh.map { it.id }
            session.pool.addAll(fresh)
            if (fresh.isNotEmpty()) return true
        }
        return false
    }

    companion object {
        private const val MAX_REFILLS_PER_BATCH = 4
        private const val MAX_SOURCE_HOPS = 4
    }
}
