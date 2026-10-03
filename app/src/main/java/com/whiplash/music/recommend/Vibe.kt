// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.recommend

import com.whiplash.music.domain.model.PlayableItem
import java.util.Calendar
import kotlin.math.abs

enum class Mood { PARTY, SAD, ROMANTIC, CHILL, WORKOUT, DEVOTIONAL, MOTIVATION, FOCUS }

/**
 * What a song feels like, as far as can be told without hearing it:
 * [moods], [genres], [energy] (0 calm … 1 intense, null unknown), [decade]
 * (1980, 2020…, null unknown) and upload [version]. Built by [VibeTagger].
 */
data class Vibe(
    val moods: Set<Mood>,
    val genres: Set<String>,
    val energy: Float?,
    val decade: Int?,
    val version: SongVersion,
) {
    val isEmpty: Boolean get() = moods.isEmpty() && genres.isEmpty() && energy == null && decade == null
}

/**
 * Tags songs with a [Vibe] from what the title says ("Sad Song", "Party
 * Anthem", "Drill", "1995", "Slowed + Reverb") and what the artist is known
 * for (a built-in profile of widely streamed artists, and whatever the
 * session has learned about an artist from songs heard together). No
 * network, no model; cheap enough to run on every candidate.
 */
object VibeTagger {

    fun tag(track: PlayableItem, learned: Map<String, Vibe> = emptyMap()): Vibe = tag(track.title, track.artist, learned)

    fun tag(title: String, artist: String, learned: Map<String, Vibe> = emptyMap()): Vibe {
        // Learned vibes only fill gaps, so a song with its own tags is cacheable.
        val base = cache.getOrPut(title + "\u0000" + artist) { compute(title, artist, emptyMap()) }
        if (learned.isEmpty() || !(base.moods.isEmpty() || base.genres.isEmpty())) return base
        return compute(title, artist, learned)
    }

    private val cache = Memo<String, Vibe>()

    private fun compute(title: String, artist: String, learned: Map<String, Vibe>): Vibe {
        val l = " " + title.lowercase().replace(RX_VB0, " ") + " "
        val moods = HashSet<Mood>()
        val genres = HashSet<String>()
        MOOD_WORDS.forEach { (re, mood) -> if (re.containsMatchIn(l)) moods += mood }
        GENRE_WORDS.forEach { (re, g) -> if (re.containsMatchIn(l)) genres += g }
        val version = SongKey.versionOf(title)

        val key = RadioRules.artistKey(artist)
        val profile = ARTISTS[key] ?: SongKey.of(title, artist).artists.firstNotNullOfOrNull { ARTISTS[it] }
        profile?.let { moods += it.moods; genres += it.genres }
        learned[key]?.let { v -> if (moods.isEmpty()) moods += v.moods; if (genres.isEmpty()) genres += v.genres }

        var energy: Float? = profile?.energy
        if (energy == null && (moods.isNotEmpty() || genres.isNotEmpty())) {
            val parts = moods.mapNotNull { MOOD_ENERGY[it] } + genres.mapNotNull { GENRE_ENERGY[it] }
            if (parts.isNotEmpty()) energy = parts.average().toFloat()
        }
        // Versions shift energy predictably.
        energy = when (version) {
            SongVersion.SLOWED, SongVersion.LOFI, SongVersion.ACOUSTIC, SongVersion.UNPLUGGED -> (energy ?: 0.5f) * 0.6f
            SongVersion.SPED_UP -> ((energy ?: 0.5f) + 0.2f).coerceAtMost(1f)
            else -> energy
        }
        return Vibe(moods, genres, energy, decadeOf(l) ?: profile?.decade, version)
    }

    private val currentDecade = Calendar.getInstance().get(Calendar.YEAR) / 10 * 10

    private fun decadeOf(l: String): Int? {
        RX_VB1.find(l)?.let { return it.groupValues[1].toInt() / 10 * 10 }
        RX_VB2.find(l)?.let { return 1900 + it.groupValues[1].toInt() }
        if (RX_VB3.containsMatchIn(l)) return 1980
        if (RX_VB4.containsMatchIn(l)) return currentDecade
        return null
    }

    private fun w(vararg words: String) = Regex(" (" + words.joinToString("|") + ") ")

    private val MOOD_WORDS = listOf(
        w("party", "club", "dance", "dj", "banger", "wedding", "shaadi", "nachna", "thumka", "lit") to Mood.PARTY,
        w("sad", "broken", "breakup", "heartbreak", "dard", "judaai", "bewafa", "tanha", "alone", "cry", "crying", "tears", "hurt", "miss you", "yaad") to Mood.SAD,
        w("love", "romantic", "romance", "pyaar", "pyar", "ishq", "mohabbat", "dil", "sanam", "valentine", "jaan", "kiss") to Mood.ROMANTIC,
        w("chill", "relax", "calm", "sleep", "night drive", "late night", "peaceful", "soft", "slow") to Mood.CHILL,
        w("workout", "gym", "motivation", "beast", "pump", "fight", "attitude", "gangster") to Mood.WORKOUT,
        w("bhajan", "aarti", "mantra", "shabad", "gurbani", "kirtan", "naat", "hamd", "worship", "gospel", "devotional", "bhakti", "krishna", "mahadev", "waheguru") to Mood.DEVOTIONAL,
        w("motivational", "inspire", "inspirational", "believe", "rise", "champion", "winning") to Mood.MOTIVATION,
        w("study", "focus", "concentration", "instrumental", "piano", "ambient") to Mood.FOCUS,
    )

    private val GENRE_WORDS = listOf(
        w("drill") to "drill", w("rap", "rapper", "cypher", "diss", "bars") to "hiphop", w("hip hop", "hiphop") to "hiphop",
        w("trap") to "trap", w("phonk") to "phonk", w("bhangra", "dhol") to "bhangra", w("sufi") to "sufi",
        w("qawwali") to "qawwali", w("ghazal") to "ghazal", w("edm", "house", "techno", "trance", "dubstep") to "edm",
        w("rock") to "rock", w("metal") to "metal", w("jazz") to "jazz", w("classical", "raag", "raga") to "classical",
        w("r b", "rnb") to "rnb", w("reggaeton") to "reggaeton", w("k pop", "kpop") to "kpop", w("indie") to "indie",
        w("folk", "lok geet", "boliyan") to "folk", w("country") to "country", w("afrobeats", "amapiano") to "afro",
        w("ost", "movie", "film") to "filmi",
    )

    private val MOOD_ENERGY = mapOf(
        Mood.PARTY to 0.85, Mood.WORKOUT to 0.9, Mood.MOTIVATION to 0.75, Mood.ROMANTIC to 0.45,
        Mood.SAD to 0.25, Mood.CHILL to 0.25, Mood.DEVOTIONAL to 0.35, Mood.FOCUS to 0.2,
    )
    private val GENRE_ENERGY = mapOf(
        "drill" to 0.85, "hiphop" to 0.75, "trap" to 0.8, "phonk" to 0.9, "bhangra" to 0.85, "edm" to 0.9,
        "rock" to 0.75, "metal" to 0.95, "reggaeton" to 0.8, "kpop" to 0.75, "afro" to 0.7,
        "sufi" to 0.45, "qawwali" to 0.5, "ghazal" to 0.2, "classical" to 0.25, "jazz" to 0.35,
        "rnb" to 0.45, "indie" to 0.4, "folk" to 0.5, "country" to 0.5, "filmi" to 0.55,
    )

    private class Profile(val genres: Set<String>, val moods: Set<Mood>, val energy: Float, val decade: Int? = null)

    private fun p(genres: String, moods: String, energy: Double, decade: Int? = null) = Profile(
        genres.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        moods.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { Mood.valueOf(it) }.toSet(),
        energy.toFloat(), decade,
    )

    /** Widely streamed artists and what they usually sound like. */
    private val ARTISTS: Map<String, Profile> = mapOf(
        // Punjabi
        "sidhumoosewala" to p("hiphop", "WORKOUT", 0.8), "karanaujla" to p("hiphop", "WORKOUT", 0.75),
        "shubh" to p("hiphop", "WORKOUT", 0.7), "apdhillon" to p("rnb", "ROMANTIC,CHILL", 0.55),
        "diljitdosanjh" to p("bhangra", "PARTY", 0.7), "gurudhandhawa" to p("bhangra", "PARTY", 0.75),
        "ammyvirk" to p("folk", "ROMANTIC", 0.5), "babbumaan" to p("folk", "ROMANTIC", 0.5),
        "satindersartaaj" to p("sufi,folk", "ROMANTIC", 0.35), "gurdasmaan" to p("folk", "", 0.5, 1990),
        "badshah" to p("hiphop", "PARTY", 0.85), "yoyohoneysingh" to p("hiphop", "PARTY", 0.85),
        "sukha" to p("drill", "WORKOUT", 0.85), "chaninattan" to p("hiphop", "WORKOUT", 0.75),
        "bohemia" to p("hiphop", "WORKOUT", 0.75), "garrysandhu" to p("bhangra", "PARTY", 0.7),
        // Hindi
        "arijitsingh" to p("filmi", "ROMANTIC,SAD", 0.35), "atifaslam" to p("filmi", "ROMANTIC,SAD", 0.4),
        "shreyaghoshal" to p("filmi", "ROMANTIC", 0.4), "jubinnautiyal" to p("filmi", "ROMANTIC,SAD", 0.35),
        "darshanraval" to p("filmi", "ROMANTIC,SAD", 0.35), "anuvjain" to p("indie", "ROMANTIC,CHILL", 0.25),
        "prateekkuhad" to p("indie", "ROMANTIC,CHILL", 0.25), "kishorekumar" to p("filmi", "ROMANTIC", 0.45, 1970),
        "latamangeshkar" to p("filmi", "ROMANTIC", 0.3, 1970), "mohammedrafi" to p("filmi", "ROMANTIC", 0.4, 1960),
        "kumarsanu" to p("filmi", "ROMANTIC", 0.4, 1990), "alkayagnik" to p("filmi", "ROMANTIC", 0.4, 1990),
        "uditnarayan" to p("filmi", "ROMANTIC", 0.45, 1990), "sonunigam" to p("filmi", "ROMANTIC", 0.45, 2000),
        "kk" to p("filmi", "ROMANTIC,SAD", 0.5, 2000), "nusratfatehalikhan" to p("qawwali,sufi", "DEVOTIONAL", 0.5, 1990),
        "rahatfatehalikhan" to p("sufi,filmi", "SAD,ROMANTIC", 0.4), "divine" to p("hiphop", "WORKOUT", 0.75),
        "krsna" to p("hiphop", "WORKOUT", 0.75), "kinguniverse" to p("hiphop", "ROMANTIC", 0.6),
        "vishalmishra" to p("filmi", "ROMANTIC,SAD", 0.35), "papon" to p("filmi", "SAD,CHILL", 0.3),
        "nehakakkar" to p("filmi", "PARTY", 0.75), "sachetparampara" to p("filmi", "ROMANTIC", 0.4),
        // South
        "anirudhravichander" to p("filmi", "PARTY", 0.8), "sidsriram" to p("filmi", "ROMANTIC", 0.35),
        "arrahman" to p("filmi", "ROMANTIC", 0.5), "dsp" to p("filmi", "PARTY", 0.8), "devisriprasad" to p("filmi", "PARTY", 0.8),
        "ilaiyaraaja" to p("filmi", "ROMANTIC", 0.4, 1980), "spbalasubrahmanyam" to p("filmi", "ROMANTIC", 0.45, 1990),
        // Global
        "theweeknd" to p("rnb", "PARTY", 0.65), "taylorswift" to p("", "ROMANTIC", 0.55), "edsheeran" to p("", "ROMANTIC", 0.5),
        "drake" to p("hiphop", "CHILL", 0.6), "eminem" to p("hiphop", "WORKOUT", 0.85), "travisscott" to p("hiphop,trap", "PARTY", 0.8),
        "kendricklamar" to p("hiphop", "", 0.7), "billieeilish" to p("indie", "SAD,CHILL", 0.35), "lanadelrey" to p("indie", "SAD,CHILL", 0.3),
        "arianagrande" to p("rnb", "ROMANTIC", 0.6), "dualipa" to p("edm", "PARTY", 0.8), "badbunny" to p("reggaeton", "PARTY", 0.8),
        "bts" to p("kpop", "PARTY", 0.75), "blackpink" to p("kpop", "PARTY", 0.85), "coldplay" to p("rock", "CHILL", 0.5),
        "imaginedragons" to p("rock", "MOTIVATION", 0.8), "linkinpark" to p("rock,metal", "WORKOUT", 0.85),
        "arcticmonkeys" to p("rock,indie", "", 0.6), "sza" to p("rnb", "ROMANTIC,CHILL", 0.4), "brunomars" to p("rnb", "PARTY", 0.7),
    )
}

/**
 * The vibe of the current listening session, as weighted tag counts: the
 * seed and songs the listener chose weigh most, songs they let play weigh
 * some, skipped songs push their tags down. [similarity] says how well a
 * candidate fits (0…1, 0.5 when nothing is known either way).
 */
class SessionVibe {
    private val moods = HashMap<Mood, Double>()
    private val genres = HashMap<String, Double>()
    private val versions = HashMap<SongVersion, Double>()
    private var energySum = 0.0
    private var energyWeight = 0.0
    private var decadeSum = 0.0
    private var decadeWeight = 0.0

    // The seed's own tags: skips can lower the session below them, never erase them.
    private val floor = HashMap<Any, Double>()

    /** [anchor]: this is the seed; its tags are the session's floor. */
    fun add(v: Vibe, weight: Double, anchor: Boolean = false) {
        fun <K : Any> bump(map: HashMap<K, Double>, k: K) {
            if (anchor) floor[k] = (floor[k] ?: 0.0) + weight * 0.5
            map[k] = ((map[k] ?: 0.0) + weight).coerceAtLeast(floor[k] ?: 0.0)
        }
        v.moods.forEach { bump(moods, it) }
        v.genres.forEach { bump(genres, it) }
        bump(versions, v.version)
        if (weight > 0) {
            v.energy?.let { energySum += it * weight; energyWeight += weight }
            v.decade?.let { decadeSum += it * weight; decadeWeight += weight }
        }
    }

    val energy: Double? get() = if (energyWeight > 0) energySum / energyWeight else null
    val decade: Double? get() = if (decadeWeight > 0) decadeSum / decadeWeight else null

    /** Whether the session has taken to [version] (e.g. the seed itself was slowed). */
    fun accepts(version: SongVersion): Boolean = version == SongVersion.ORIGINAL || (versions[version] ?: 0.0) > 0.0

    fun topMoods(n: Int = 2): List<Mood> = moods.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(n).map { it.key }

    fun similarity(v: Vibe): Double {
        val mood = overlap(moods, v.moods)
        val genre = overlap(genres, v.genres)
        val e = energy
        val energySim = if (e != null && v.energy != null) 1.0 - abs(e - v.energy) else 0.5
        val d = decade
        val eraSim = if (d != null && v.decade != null) 1.0 - (abs(d - v.decade) / 30.0).coerceAtMost(1.0) else 0.5
        val versionSim = if (accepts(v.version)) 1.0 else 0.0
        return 0.32 * mood + 0.25 * genre + 0.23 * energySim + 0.1 * eraSim + 0.1 * versionSim
    }

    private fun <K> overlap(weights: Map<K, Double>, tags: Set<K>): Double {
        val total = weights.values.sum()
        if (total <= 0.0 || tags.isEmpty()) return 0.5
        val shared = tags.sumOf { weights[it] ?: 0.0 }
        // Sharing the session's main tag is a full match.
        val top = weights.values.max()
        return (shared / top).coerceAtMost(1.0) * 0.8 + (shared / total).coerceAtMost(1.0) * 0.2
    }
}

/** How alike two songs are for variety purposes: same artist or same feel. */
internal fun vibeLikeness(a: PlayableItem, av: Vibe, b: PlayableItem, bv: Vibe, sameArtistWeight: Double = 0.65): Double {
    val sameArtist = RadioRules.artistKey(a.artist).let { it.isNotEmpty() && it == RadioRules.artistKey(b.artist) }
    val tagsA = av.moods.map { it.name } + av.genres
    val tagsB = bv.moods.map { it.name } + bv.genres
    val jaccard = if (tagsA.isEmpty() || tagsB.isEmpty()) 0.0
    else tagsA.intersect(tagsB.toSet()).size.toDouble() / (tagsA.toSet() + tagsB).size
    return (if (sameArtist) sameArtistWeight else 0.0) + 0.35 * jaccard
}

// Compiled once: building a Regex per call made the radio freeze the UI.
private val RX_VB0 = Regex("[^\\p{L}\\p{M}\\p{N}]+")
private val RX_VB1 = Regex(" (19[5-9]\\d|20[0-3]\\d) ")
private val RX_VB2 = Regex(" ([5-9]0)s ")
private val RX_VB3 = Regex(" (old is gold|evergreen|purane|purana|retro|oldies|classic hits|golden era) ")
private val RX_VB4 = Regex(" (latest|new song|new songs|trending) ")
