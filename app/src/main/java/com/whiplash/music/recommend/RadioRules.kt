package com.whiplash.music.recommend

import com.whiplash.music.domain.model.PlayableItem

/**
 * Pure, testable rules the radio applies to every candidate batch. No I/O.
 */
object RadioRules {

    /** How many songs by one artist may play back to back. */
    const val MAX_ARTIST_RUN = 2

    /** At most this many songs by one artist in any [ARTIST_WINDOW] in a row. */
    const val MAX_ARTIST_PER_WINDOW = 3
    const val ARTIST_WINDOW = 5

    /**
     * Grouping key for an artist/uploader: lowercase, "- Topic", "VEVO",
     * "Official" and punctuation removed, so "DILJIT DOSANJH", "Diljit
     * Dosanjh - Topic" and "DiljitDosanjhVEVO" all match.
     */
    fun artistKey(artist: String): String = artistKeys.getOrPut(artist) { computeArtistKey(artist) }

    private val artistKeys = Memo<String, String>()

    private fun computeArtistKey(artist: String): String = artist.lowercase()
        .replace(RX_RR0, "")
        .replace(RX_RR1, "")
        .replace(RX_RR2, "")
        .replace(RX_RR3, "")

    private val RETRO = Regex(
        "(?i)\\b(19[5-9]\\d|[5-9]0'?s|old is gold|evergreen|purane|purana|retro|golden era|classic hits|oldies)\\b",
    )

    /** Titles that announce old songs ("80s Hits", "Old Is Gold", "1985"). */
    fun isRetro(title: String): Boolean = RETRO.containsMatchIn(title)

    /**
     * Reorders [batch] so no artist plays more than [MAX_ARTIST_RUN] in a
     * row or more than [MAX_ARTIST_PER_WINDOW] in any [ARTIST_WINDOW],
     * counting the queue's existing [tailArtistKeys] (oldest first).
     * Candidates that can't be placed anywhere yet are dropped from this
     * batch rather than breaking the rule; they're returned to the caller
     * in [ArtistCapResult.deferred] to try again next time.
     */
    fun applyArtistCap(
        batch: List<PlayableItem.YoutubeTrack>,
        tailArtistKeys: List<String>,
    ): ArtistCapResult {
        val placed = mutableListOf<PlayableItem.YoutubeTrack>()
        val recent = tailArtistKeys.takeLast(ARTIST_WINDOW).toMutableList()
        val pending = batch.toMutableList()
        while (pending.isNotEmpty()) {
            val i = pending.indexOfFirst { fits(artistKey(it.artist), recent) }
            if (i < 0) break
            val pick = pending.removeAt(i)
            placed += pick
            recent += artistKey(pick.artist)
            if (recent.size > ARTIST_WINDOW) recent.removeAt(0)
        }
        return ArtistCapResult(placed, pending)
    }

    private fun fits(key: String, recent: List<String>): Boolean {
        if (key.isEmpty()) return true
        val run = recent.takeLastWhile { it == key }.size
        if (run >= MAX_ARTIST_RUN) return false
        val inWindow = recent.takeLast(ARTIST_WINDOW - 1).count { it == key }
        return inWindow < MAX_ARTIST_PER_WINDOW
    }

    data class ArtistCapResult(
        val placed: List<PlayableItem.YoutubeTrack>,
        val deferred: List<PlayableItem.YoutubeTrack>,
    )
}

/**
 * Weighted language counts for one listening session. The seed and songs
 * the listener chose count most, songs they kept count a little, skipped
 * ones count against. [dominant] is the language the radio sticks to.
 */
class LanguageProfile {
    private val weights = HashMap<String, Double>()

    fun add(code: String?, weight: Double) {
        if (code == null) return
        weights[code] = ((weights[code] ?: 0.0) + weight).coerceAtLeast(0.0)
    }

    /** Languages the session has actually accepted (any positive weight). */
    val accepted: Set<String> get() = weights.filterValues { it > 0.0 }.keys

    /**
     * The one language holding at least [share] of the weight (with a
     * minimum amount of evidence), or null if the session is mixed.
     */
    fun dominant(share: Double = 0.6, minWeight: Double = 2.0): String? {
        val total = weights.values.sum()
        if (total < minWeight) return null
        val top = weights.maxByOrNull { it.value } ?: return null
        return top.key.takeIf { top.value / total >= share }
    }

    private fun share(code: String): Double {
        val total = weights.values.sum()
        return if (total <= 0) 0.0 else (weights[code] ?: 0.0) / total
    }

    /**
     * Whether a candidate may join the radio at all. Only reliable evidence
     * ([LanguageGuess.STRONG]) ever excludes a song, since titles lie:
     * Hindi songs have English or Punjabi-word titles and vice versa.
     * Unknown always may; so may anything the dominant language is, or that
     * the listener already accepted this session. A reliably different
     * language is excluded when it's another family (Korean in a Punjabi
     * session), and a sister language (Hindi in a Punjabi session) only
     * when the session is firmly one language; otherwise it just scores
     * lower (see [penalty]).
     */
    fun allows(code: String?, strength: Int): Boolean {
        if (code == null) return true
        val dom = dominant() ?: return true
        if (code == dom || (weights[code] ?: 0.0) >= 1.0) return true
        if (strength < LanguageGuess.STRONG) return true
        if (LanguageDetector.family(code) != LanguageDetector.family(dom)) return false
        val firm = weights.values.sum() >= 3.0 && share(dom) >= 0.85
        return !firm
    }

    /** 0 (fits) … 1 (clearly another language) for scoring; weak evidence counts little. */
    fun penalty(code: String?, strength: Int): Double {
        if (code == null) return 0.0
        val dom = dominant() ?: return 0.0
        if (code == dom) return 0.0
        val acceptedShare = share(code)
        if (acceptedShare >= 0.25) return 0.0
        val sameFamily = LanguageDetector.family(code) == LanguageDetector.family(dom)
        val base = when (strength) {
            LanguageGuess.STRONG -> 1.0
            LanguageGuess.ARTIST -> 0.75
            else -> 0.3
        }
        return base * (if (sameFamily) 0.7 else 1.0) * (1.0 - acceptedShare * 4)
    }
}

// Compiled once: building a Regex per call made the radio freeze the UI.
private val RX_RR0 = Regex("\\s*-\\s*topic$")
private val RX_RR1 = Regex("vevo$")
private val RX_RR2 = Regex("\\b(official|music)\\b")
private val RX_RR3 = Regex("[^\\p{L}\\p{M}\\p{N}]+")
