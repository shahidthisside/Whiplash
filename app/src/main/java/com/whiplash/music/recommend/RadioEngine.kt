package com.whiplash.music.recommend

import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.playback.controller.SongLengthClass
import com.whiplash.music.playback.controller.classifySongLength

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

    /** 0…1 per artist key: how reliably the listener finishes their songs (missing = no data). */
    suspend fun artistAffinity(): Map<String, Double> = emptyMap()
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
    /** Everything this radio has queued, in order, and which of those were skipped. */
    internal val served = mutableListOf<PlayableItem.YoutubeTrack>()
    internal val skippedIds = HashSet<String>()
    internal val skippedArtists = HashMap<String, Int>()
    val profile = LanguageProfile()
    val vibe = SessionVibe()
    /** What the session learned about artists' feel (from songs they were heard with). */
    internal val learnedVibes = HashMap<String, Vibe>()
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
        languageOf(track)?.let { session.profile.add(it.code, langWeight(it, 2.0)) }
        session.vibe.add(VibeTagger.tag(track, session.learnedVibes), 2.0)
    }

    /** An autoplay song was played through: it's on target. */
    suspend fun onKept(session: RadioSession, track: PlayableItem.YoutubeTrack) {
        if (session.kept.none { it.id == track.id }) session.kept += track
        languageOf(track)?.let { session.profile.add(it.code, langWeight(it, 1.0)) }
        session.vibe.add(VibeTagger.tag(track, session.learnedVibes), 1.0)
    }

    /** An autoplay song was skipped early: steer away from it. */
    suspend fun onSkipped(session: RadioSession, track: PlayableItem.YoutubeTrack) {
        val key = RadioRules.artistKey(track.artist)
        session.skippedIds += track.id
        session.skippedArtists[key] = (session.skippedArtists[key] ?: 0) + 1
        // A skip is about that song, not its language: language is left alone.
        session.vibe.add(VibeTagger.tag(track, session.learnedVibes), -0.4)
    }

    /**
     * Up to [count] songs to append after [queue]. Never throws for a
     * network failure; returns fewer (or none) instead.
     */
    suspend fun nextBatch(session: RadioSession, queue: List<PlayableItem>, count: Int): List<PlayableItem.YoutubeTrack> {
        if (!session.seedLanguageResolved) {
            session.seedLanguageResolved = true
            languageOf(session.seed)?.let { session.profile.add(it.code, langWeight(it, 3.0)) }
            session.vibe.add(VibeTagger.tag(session.seed), 3.0, anchor = true)
        }
        val queueIds = queue.mapTo(HashSet()) { it.id }
        val dupes = NearDuplicateFilter().apply { queue.forEach { add(it) } }
        val rejected = runCatching { feedback.rejectedTrackIds() }.getOrDefault(emptySet())
        val blocked = runCatching { feedback.blockedArtistKeys() }.getOrDefault(emptySet())
        val recent = runCatching { feedback.recentlyPlayedIds() }.getOrDefault(emptySet())
        if (affinity == null) affinity = runCatching { feedback.artistAffinity() }.getOrDefault(emptyMap())

        // 1. Candidates that pass every hard rule, in feed order.
        val accepted = mutableListOf<PlayableItem.YoutubeTrack>()
        val acceptedAt = java.util.IdentityHashMap<SongKey, Int>()
        // Played in the last few hours: only used when there aren't enough fresh songs.
        val recentBackup = mutableListOf<PlayableItem.YoutubeTrack>()
        // Only failed "soft" rules (artist skipped twice this session): used before the radio would run dry.
        val softBackup = mutableListOf<PlayableItem.YoutubeTrack>()
        var refills = 0
        while (accepted.size < count * CANDIDATE_FACTOR) {
            if (session.pool.isEmpty()) {
                if (refills >= MAX_REFILLS_PER_BATCH || !refill(session)) break
                refills++
                artistLanguageCache.putAll(
                    runCatching {
                        feedback.artistLanguages(session.pool.map { RadioRules.artistKey(it.artist) }.filter { it !in artistLanguageCache })
                    }.getOrDefault(emptyMap()),
                )
                learnFromFeed(session, session.pool.toList())
            }
            val c = session.pool.removeFirst()
            if (c.id in queueIds || c.id in rejected) continue
            if (classifySongLength(c.title) != session.lengthClass) continue
            val key = RadioRules.artistKey(c.artist)
            if (key in blocked) continue
            val softRejected = (session.skippedArtists[key] ?: 0) >= 2
            if (!session.seedRetro && RadioRules.isRetro(c.title)) continue
            // Slowed/8D/lofi/karaoke versions only when the session is into them.
            // Slowed/8D/sped-up/lofi/karaoke/instrumental versions only when the
            // session is into them; live/acoustic/cover/remix just score lower.
            val version = SongKey.versionOf(c.title)
            if (version in ALTERED_VERSIONS && !session.vibe.accepts(version)) continue
            val lang = languageOf(c)
            if (!session.profile.allows(lang?.code, lang?.strength ?: 0)) continue
            if (c.id in recent) {
                if (recentBackup.size < count && recentBackup.none { SongKey.of(it).sameSongAs(SongKey.of(c)) }) recentBackup += c
                continue
            }
            if (softRejected) {
                if (softBackup.size < count) softBackup += c
                continue
            }
            val original = dupes.match(SongKey.of(c), c.durationMs)
            if (original != null) {
                // Same song as one already picked: keep whichever upload is better
                // (the official audio over its music video, say).
                val at = acceptedAt[original] ?: continue
                if (uploadQuality(c) > uploadQuality(accepted[at])) accepted[at] = c
                continue
            }
            // YouTube Music only lists music; other sources need checking.
            if (c.id in session.unverified && UploadKinds.of(c.id) == null && !runCatching { isMusic(c.id) }.getOrDefault(true)) continue
            acceptedAt[SongKey.of(c)] = accepted.size
            accepted += c
        }
        // Never let the radio run dry: relax the soft rules before giving up,
        // freshest first (recently played only after everything else).
        if (accepted.size < count) softBackup.filter { dupes.accept(it) }.take(count - accepted.size).forEach { accepted += it }
        if (accepted.size < count) recentBackup.filter { dupes.accept(it) }.take(count - accepted.size).forEach { accepted += it }
        if (accepted.isEmpty()) return emptyList()

        // 2. Score: feed order (YouTube Music's own ranking) + vibe fit +
        //    how much the listener likes the artist + a little novelty.
        val aff = affinity.orEmpty()
        val tailKeys = queue.takeLast(20).mapTo(HashSet()) { RadioRules.artistKey(it.artist) }
        val vibes = accepted.associate { it.id to VibeTagger.tag(it, session.learnedVibes) }
        val langs = accepted.associate { it.id to languageOf(it) }
        val scores = accepted.mapIndexed { i, c ->
            val key = RadioRules.artistKey(c.artist)
            val rank = 1.0 - i.toDouble() / accepted.size
            val fit = session.vibe.similarity(vibes.getValue(c.id))
            val liked = aff[key] ?: 0.5
            val novel = if (key !in tailKeys) 1.0 else 0.0
            val lang = langs[c.id]
            val offLanguage = session.profile.penalty(lang?.code, lang?.strength ?: 0)
            c.id to (W_RANK * rank + W_VIBE * fit + W_AFFINITY * liked + W_NOVELTY * novel - W_LANGUAGE * offLanguage)
        }.toMap()

        // 3. Pick with MMR: best score, minus likeness to what's already picked
        //    (and the end of the queue), so the batch doesn't clump.
        val context = queue.takeLast(RadioRules.ARTIST_WINDOW).filterIsInstance<PlayableItem.YoutubeTrack>()
        val contextVibes = context.map { it to VibeTagger.tag(it, session.learnedVibes) }
        val remaining = accepted.toMutableList()
        val ordered = mutableListOf<PlayableItem.YoutubeTrack>()
        val maxScore = scores.values.max().takeIf { it > 0 } ?: 1.0
        while (remaining.isNotEmpty()) {
            val best = remaining.maxBy { c ->
                val cv = vibes.getValue(c.id)
                val like = (ordered.map { it to vibes.getValue(it.id) } + contextVibes)
                    .maxOfOrNull { (o, ov) -> vibeLikeness(c, cv, o, ov) } ?: 0.0
                MMR_LAMBDA * (scores.getValue(c.id) / maxScore) - (1 - MMR_LAMBDA) * like
            }
            remaining.remove(best)
            ordered += best
        }

        val tail = queue.takeLast(RadioRules.ARTIST_WINDOW).map { RadioRules.artistKey(it.artist) }
        val capped = RadioRules.applyArtistCap(ordered, tail)
        val out = capped.placed.take(count)
        // Good candidates that didn't make this batch go back, best first.
        (capped.placed.drop(count) + capped.deferred).asReversed().forEach { session.pool.addFirst(it) }
        session.served += out
        return out
    }

    private var affinity: Map<String, Double>? = null

    /**
     * Artists with no known feel pick it up from the radio page they came
     * in on: a YouTube Music radio is one vibe, so songs heard together
     * share it. Only fills gaps; the built-in profiles and title words win.
     */
    private fun learnFromFeed(session: RadioSession, page: List<PlayableItem.YoutubeTrack>) {
        val tags = page.map { it to VibeTagger.tag(it, session.learnedVibes) }
        val moodCounts = tags.flatMap { it.second.moods }.groupingBy { it }.eachCount()
        val genreCounts = tags.flatMap { it.second.genres }.groupingBy { it }.eachCount()
        val min = (page.size / 4).coerceAtLeast(2)
        val moods = moodCounts.filterValues { it >= min }.keys
        val genres = genreCounts.filterValues { it >= min }.keys
        if (moods.isEmpty() && genres.isEmpty()) return
        val energies = tags.mapNotNull { it.second.energy }
        val v = Vibe(moods, genres, energies.takeIf { it.isNotEmpty() }?.average()?.toFloat(), null, SongVersion.ORIGINAL)
        tags.filter { it.second.moods.isEmpty() && it.second.genres.isEmpty() }
            .forEach { (track, _) -> session.learnedVibes.putIfAbsent(RadioRules.artistKey(track.artist), v) }
    }

    private val artistLanguageCache = HashMap<String, String>()

    private suspend fun languageOf(track: PlayableItem.YoutubeTrack): LanguageGuess? {
        val detected = LanguageDetector.detect(track.title, track.artist)
        if (detected != null && detected.strength >= LanguageGuess.ARTIST) return detected
        // What this artist's songs turned out to be in past plays beats title words.
        val key = RadioRules.artistKey(track.artist)
        val known = artistLanguageCache[key]
            ?: runCatching { feedback.artistLanguages(listOf(key))[key] }.getOrNull()?.also { artistLanguageCache[key] = it }
        return known?.let { LanguageGuess(it, LanguageGuess.ARTIST) } ?: detected
    }

    /** Weak evidence moves the session's language less. */
    private fun langWeight(g: LanguageGuess, full: Double) = when (g.strength) {
        LanguageGuess.STRONG -> full
        LanguageGuess.ARTIST -> full * 0.8
        else -> full * 0.4
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
            // Nothing finished yet (the listener is sampling): continue from the
            // most recent song this radio served that wasn't skipped.
            val sampled = session.served.lastOrNull { it.id !in session.usedFeedSeeds && it.id !in session.skippedIds }
            if (sampled != null) {
                session.usedFeedSeeds += sampled.id
                session.feedSeed = sampled
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
        private val ALTERED_VERSIONS = setOf(
            SongVersion.SLOWED, SongVersion.SPED_UP, SongVersion.EIGHT_D, SongVersion.LOFI, SongVersion.KARAOKE, SongVersion.INSTRUMENTAL,
        )
        /** Candidates gathered per song wanted, so scoring has a real choice. */
        private const val CANDIDATE_FACTOR = 3
        private const val W_RANK = 1.0
        private const val W_VIBE = 1.4
        private const val W_AFFINITY = 0.6
        private const val W_NOVELTY = 0.25
        private const val W_LANGUAGE = 1.2
        private const val MMR_LAMBDA = 0.72
        private const val MAX_SOURCE_HOPS = 6
    }
}
