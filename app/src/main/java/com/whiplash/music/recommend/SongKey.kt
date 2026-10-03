// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.recommend

import com.whiplash.music.domain.model.PlayableItem
import java.text.Normalizer

/**
 * What kind of upload a song is. Everything except [ORIGINAL] is a variant
 * of some original, and the radio treats variants of the same song as
 * duplicates of it.
 */
enum class SongVersion { ORIGINAL, REMIX, LOFI, SLOWED, SPED_UP, EIGHT_D, LIVE, ACOUSTIC, COVER, INSTRUMENTAL, KARAOKE, UNPLUGGED }

/**
 * A song's identity with upload noise removed, so "Dawood Lyrical Video |
 * PBX 1 | Sidhu Moose Wala | Byg Byrd | T-Series", "Dawood (Official Audio)"
 * and "Dawood - Slowed + Reverb" are all recognised as the one song
 * `dawood`.
 *
 * [core] is the normalised song name (Unicode aware, so Hindi or Tamil
 * titles work too), [artists] are the normalised artist names found in the
 * uploader and title, and [version] is what kind of upload it is.
 */
data class SongKey(
    val core: String,
    val artists: Set<String>,
    val version: SongVersion,
    val combo: Boolean,
    /**
     * For "A - B" titles by a label, where it's unclear which side is the
     * artist: both cleaned sides. Matches another song only when one side
     * is its name AND the other side is its artist, so "Arijit Singh - Tum
     * Hi Ho" (T-Series) matches "Tum Hi Ho" by Arijit Singh but two
     * different "Arijit Singh - …" uploads never match each other.
     */
    val pair: Pair<String, String>? = null,
    /**
     * Uploaded by a label, so [artists] only has the credits the title
     * happened to list (often actors or the film) and may miss the singer.
     */
    val labelUpload: Boolean = false,
) {
    /** Every name this song could be indexed under. */
    val names: Set<String> get() = setOfNotNull(core, pair?.first, pair?.second)

    companion object {
        fun of(track: PlayableItem): SongKey = of(track.title, track.artist)

        fun of(title: String, artist: String): SongKey = keys.getOrPut(title + "\u0000" + artist) { compute(title, artist) }

        private val keys = Memo<String, SongKey>()

        private fun compute(title: String, artist: String): SongKey {
            val version = versionOf(title)
            val combo = COMBO.containsMatchIn(title)
            val uploader = RadioRules.artistKey(artist)
            val artists = HashSet<String>()
            val byLabel = uploader.isEmpty() || isLabel(uploader) || isLabelName(artist)
            if (!byLabel) {
                artists += uploader
                // "Pritam & Arijit Singh" (YouTube Music's credits): each artist counts.
                RadioRules.creditKeys(artist).filter { it.length >= 3 && !isLabel(it) }.forEach { artists += it }
            }
            // Credits hiding in the title ("ft. X", "| Karan Aujla |") count too.
            FEAT.findAll(title).forEach { m ->
                m.groupValues[1].split(RX_SK0).map { RadioRules.artistKey(it) }.filter { it.length >= 3 }.forEach { artists += it }
            }

            var t = Normalizer.normalize(title, Normalizer.Form.NFKC)
            t = FEAT.replace(t, " ")
            t = BRACKETS.replace(t, " ")
            // "Artist - Title | Label | …": pick the segment that is the song.
            val segments = t.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }
            val cleaned = segments.map { seg -> seg to clean(seg) }
            val candidates = cleaned.filter { (seg, c) ->
                val k = RadioRules.artistKey(seg)
                c.isNotEmpty() && k != uploader && !isLabel(k) && k !in artists
            }
            var core = candidates.firstOrNull()?.second ?: cleaned.firstOrNull()?.second.orEmpty()
            var pair: Pair<String, String>? = null
            val firstSep = SEPARATORS.find(t)?.value?.trim()
            val dashPair = firstSep in DASHES && candidates.size >= 2 &&
                candidates[0].first == segments[0] && candidates[1].first == segments[1]
            if (dashPair) {
                // "X - Y" where neither side is the uploader: usually "Artist -
                // Title", sometimes "Title - Artist". A known artist settles it;
                // otherwise both readings are kept (see [pair]).
                val (a, b) = candidates[0] to candidates[1]
                val ka = RadioRules.artistKey(a.first)
                val kb = RadioRules.artistKey(b.first)
                when {
                    LanguageDetector.isKnownArtist(ka) -> { artists += ka; core = b.second }
                    LanguageDetector.isKnownArtist(kb) -> { artists += kb; core = a.second }
                    else -> { pair = a.second to b.second; core = b.second }
                }
            }
            // Further name-like segments are credits ("| Sidhu Moose Wala | Byg Byrd |",
            // "| Arijit Singh & Harshdeep Kaur |").
            candidates.drop(if (dashPair) 2 else 1).map { it.first }
                .flatMap { it.split(RX_SK1) }
                .map { it.trim() }
                .filter { NAME_LIKE.matches(it) && it.split(RX_SK2).size in 2..4 }
                .forEach { artists += RadioRules.artistKey(it) }
            // The uploader's name inside the title ("WAVY KARAN AUJLA").
            val uploaderWords = clean(artist)
            if (uploaderWords.isNotEmpty() && core != uploaderWords) {
                val stripped = (" $core ").replace(" $uploaderWords ", " ").trim()
                if (stripped.isNotEmpty()) core = stripped
            }
            if (core.isEmpty()) core = title.lowercase().replace(RX_SK3, " ").trim()
            return SongKey(core, artists, version, combo, pair, labelUpload = byLabel)
        }

        /** Lowercase letters/digits only, with upload and version words removed. */
        private fun clean(s: String): String {
            var c = s.lowercase()
            c = VERSION_WORDS.replace(c, " ")
            c = UPLOAD_WORDS.replace(c, " ")
            c = c.replace(RX_SK3, " ").trim()
            c = SEGMENT_NOISE.replace(" $c ", " ").trim()
            return c.replace(RX_SK2, " ")
        }

        fun versionOf(title: String): SongVersion {
            val l = title.lowercase()
            return when {
                RX_SK4.containsMatchIn(l) -> SongVersion.EIGHT_D
                RX_SK5.containsMatchIn(l) -> SongVersion.SLOWED
                RX_SK6.containsMatchIn(l) -> SongVersion.SPED_UP
                RX_SK7.containsMatchIn(l) -> SongVersion.LOFI
                RX_SK8.containsMatchIn(l) -> SongVersion.KARAOKE
                RX_SK9.containsMatchIn(l) -> SongVersion.INSTRUMENTAL
                RX_SK10.containsMatchIn(l) -> SongVersion.UNPLUGGED
                RX_SK11.containsMatchIn(l) -> SongVersion.ACOUSTIC
                RX_SK12.containsMatchIn(l) -> SongVersion.COVER
                RX_SK13.containsMatchIn(l) -> SongVersion.LIVE
                RX_SK14.containsMatchIn(l) -> SongVersion.REMIX
                else -> SongVersion.ORIGINAL
            }
        }

        private val DASHES = setOf("-", "–", "—", ":")

        private fun isLabel(key: String) = key in LABELS || LABEL_SUFFIX.containsMatchIn(key)

        /** Checked on the raw name too: [RadioRules.artistKey] drops "music"/"official". */
        private fun isLabelName(name: String): Boolean {
            val squashed = name.lowercase().replace(RX_SK15, "")
            return squashed in LABELS || LABEL_SUFFIX.containsMatchIn(squashed)
        }

        private val FEAT = Regex("(?i)[\\(\\[]?\\b(?:feat\\.?|ft\\.?|featuring)\\s+([^\\)\\]|\\-–]+)[\\)\\]]?")
        private val BRACKETS = Regex("[\\(\\[【「][^\\)\\]】」]{0,60}[\\)\\]】」]")
        private val SEPARATORS = Regex("\\s+[|\\-–—:~]\\s+|\\s*\\|\\s*|\\s+//\\s+|:\\s+")
        private val COMBO = Regex("(?i)\\s+(x|vs\\.?)\\s+|\\b(mashup|medley)\\b")
        private val NAME_LIKE = Regex("[\\p{Lu}][\\p{L}.']+(\\s+[\\p{Lu}][\\p{L}.']+){1,3}")
        /** Whole-word filler that says nothing about which song it is. */
        private val SEGMENT_NOISE = Regex(
            "(?<= )(latest|new|superhit|super hit|hit|hits|trending|viral|songs?|punjabi|hindi|tamil|telugu|bollywood|haryanvi|bhojpuri|marathi|bengali|english|20\\d\\d|19\\d\\d|with|full|video|audio|album|ep|single|track)(?= )",
        )
        private val VERSION_WORDS = Regex(
            "(?i)\\b(slowed|reverb|sped up|nightcore|8d( audio)?|lo-?fi|remix|remastered|remaster|acoustic|unplugged|instrumental|karaoke|cover|live|version|edit|extended|radio edit|bass boosted)\\b",
        )
        private val UPLOAD_WORDS = Regex(
            "(?i)\\b(official( music)?( video| audio| lyric video)?|(with )?lyrics?|lyrical( video)?|full (video|song|audio)|music video|video song|audio song|visuali[sz]er|mv|hd|hq|4k|explicit|clean|new song|latest song)\\b",
        )
        private val LABEL_SUFFIX = Regex("(records|music|studios?|entertainment|films|official|tv|channel|label|media|productions?|company|musicindia|worldwide|digital|series)$")
        private val LABELS = setOf(
            "tseries", "zeemusiccompany", "sonymusicindia", "tipsofficial", "saregama", "saregamamusic", "yrf", "speedrecords",
            "whitehillmusic", "desimelodi", "jukedock", "timesmusic", "eroseenow", "venusmovies", "shemaroo", "adityamusic",
            "thinkmusicindia", "lahari", "sonymusicsouth", "junglee", "vevo", "topic", "freshmediarecords", "ishtarpunjabi",
            "beingpunjabi", "geetmp3", "humble", "brown town", "browntown", "vyrlharyanvi", "vyrloriginals", "trendingsongs",
        )
    }

    /**
     * Whether [other] is the same song (possibly another upload or version).
     * Same [core] and either a shared artist, or no artist information to
     * contradict it. Mashups only match mashups.
     */
    fun sameSongAs(other: SongKey): Boolean {
        if (combo != other.combo) return false
        if (core != other.core || core.length < 2) {
            if (pairMatch(other) || other.pairMatch(this)) return true
            // "Janam Janam - Dilwale | …" vs "Janam Janam": one side of the pair is the
            // other's song name, and they share an artist.
            val pairSide = (pair != null && other.core.length >= 4 && other.core in names) ||
                (other.pair != null && core.length >= 4 && core in other.names)
            return pairSide && artistsOverlap(other)
        }
        if (pair != null && other.pair != null) return pair.toList().toSet() == other.pair.toList().toSet() || artistsOverlap(other)
        if (artists.isEmpty() || other.artists.isEmpty()) return true
        // "Kesariya (Slowed + Reverb)" by some lofi channel is still Kesariya.
        if (version != other.version && core.length >= 4) return true
        if (artistsOverlap(other)) return true
        // A label's credits can't prove it's someone else's song; the caller
        // decides on length (see NearDuplicateFilter).
        return labelUpload || other.labelUpload
    }

    private fun artistsOverlap(other: SongKey) = artists.any { it in other.artists }

    private fun pairMatch(other: SongKey): Boolean {
        val (a, b) = pair ?: return false
        val ka = a.replace(" ", "")
        val kb = b.replace(" ", "")
        return (other.core == b && ka in other.artists) || (other.core == a && kb in other.artists)
    }
}

/**
 * Drops near-duplicates from a stream of tracks: another upload of a song
 * already accepted ("Official Video" vs "Lyrics" vs "Audio"), or a variant
 * of it (slowed, 8D, lofi, remix…). Songs with a very different length and
 * no artist in common are kept even when the names match, since two
 * artists' songs can share a title.
 *
 * With [strictVersions] (search results) different versions are kept and
 * only same-version re-uploads merge.
 */
class NearDuplicateFilter(private val strictVersions: Boolean = false) {
    private val byName = HashMap<String, MutableList<Pair<SongKey, Long>>>()

    /** Registers [track]; returns false if it duplicates one seen earlier. */
    fun accept(track: PlayableItem): Boolean = accept(SongKey.of(track), track.durationMs)

    fun accept(key: SongKey, durationMs: Long): Boolean = match(key, durationMs) == null

    /** The earlier song [key] duplicates, or null (and [key] is registered as new). */
    fun match(key: SongKey, durationMs: Long): SongKey? {
        val seen = HashSet<SongKey>()
        for (name in key.names) {
            for ((k, d) in byName[name].orEmpty()) {
                if (!seen.add(k)) continue
                if (strictVersions && k.version != key.version) continue
                if (!k.sameSongAs(key)) continue
                // Nothing but the name in common: two artists' songs can share
                // a title, so only merge when the lengths agree too.
                val nameOnly = (k.artists.isEmpty() || key.artists.isEmpty()) && k.version == key.version
                if (nameOnly && d > 0 && durationMs > 0 && kotlin.math.abs(d - durationMs) > NAME_ONLY_TOLERANCE_MS) continue
                // Only a label's credits disagree: same song only if the lengths
                // match closely (a video's intro/outro adds some seconds).
                val labelOnly = !k.artists.any { it in key.artists } && (k.labelUpload || key.labelUpload) && k.version == key.version
                if (labelOnly && (d <= 0 || durationMs <= 0 || kotlin.math.abs(d - durationMs) > LABEL_TOLERANCE_MS)) continue
                return k
            }
        }
        // "Case" vs "Case Ghost" (album name left in the title): a prefix
        // match only counts with a shared artist and near-equal length.
        if (key.artists.isNotEmpty() && key.core.length >= 4) {
            for ((name, list) in byName) {
                val (short, long) = if (name.length < key.core.length) name to key.core else key.core to name
                if (short.length < 4 || !long.startsWith("$short ")) continue
                if (long.removePrefix(short).trim().split(' ').size > 2) continue
                list.firstOrNull { (k, d) ->
                        k.combo == key.combo && k.artists.any { it in key.artists } &&
                            (!strictVersions || k.version == key.version) &&
                            (d <= 0 || durationMs <= 0 || kotlin.math.abs(d - durationMs) <= 25_000)
                }?.let { return it.first }
            }
        }
        add(key, durationMs)
        return null
    }

    private fun add(key: SongKey, durationMs: Long) {
        key.names.forEach { byName.getOrPut(it) { mutableListOf() } += key to durationMs }
    }

    /** Marks [track] as seen without asking (e.g. the queue already holds it). */
    fun add(track: PlayableItem) = add(SongKey.of(track), track.durationMs)
}

// Authored by S. Ansari for Whiplash; all rights reserved.
private const val NAME_ONLY_TOLERANCE_MS = 60_000L
private const val LABEL_TOLERANCE_MS = 40_000L

/**
 * [this] without near-duplicates. Each song keeps the position of its first
 * appearance, but shows its best upload: the original over a slowed/8D/lofi
 * version, official audio/"- Topic" over a music video over a lyric video.
 * A song that only exists as a lyric video (or only as a video) is kept as
 * that: nothing is dropped unless another upload of it stays.
 */
fun <T : PlayableItem> List<T>.withoutNearDuplicates(strictVersions: Boolean = false, preferBest: Boolean = true): List<T> {
    val f = NearDuplicateFilter(strictVersions)
    val groups = mutableListOf<MutableList<T>>()
    val groupOf = java.util.IdentityHashMap<SongKey, Int>()
    for (item in this) {
        val key = SongKey.of(item)
        val original = f.match(key, item.durationMs)
        if (original == null) {
            groupOf[key] = groups.size
            groups += mutableListOf(item)
        } else {
            groupOf[original]?.let { groups[it] += item }
        }
    }
    if (!preferBest) return groups.map { it.first() }
    // Ties keep the earlier (higher-ranked) upload.
    return groups.map { g -> g.withIndex().maxWith(compareBy<IndexedValue<T>> { uploadQuality(it.value) }.thenByDescending { it.index }).value }
}

/**
 * Search results without re-uploads of the same song, keeping YouTube's
 * order and first result. Versions (slowed, live, remix…) stay distinct,
 * and nothing is merged at all when the query asks for a kind of upload
 * ("kesariya lyrics", "… slowed", "… live") — then those are the point.
 */
fun <T : PlayableItem> List<T>.dedupeSearchResults(query: String): List<T> {
    if (SEARCH_UPLOAD_INTENT.containsMatchIn(query)) return this
    return withoutNearDuplicates(strictVersions = true, preferBest = false)
}

private val SEARCH_UPLOAD_INTENT = Regex(
    "(?i)\\b(lyric|lyrics|lyrical|video|audio|slowed|reverb|8d|lofi|lo-fi|remix|live|cover|acoustic|unplugged|karaoke|instrumental|version|sped|nightcore|status|reaction)\\b",
)

/** [newItems] minus near-duplicates of [existing] or of each other (search paging). */
fun <T : PlayableItem> appendWithoutNearDuplicates(existing: List<T>, newItems: List<T>, query: String): List<T> {
    val ids = existing.mapTo(HashSet()) { it.id }
    val fresh = newItems.filter { ids.add(it.id) }
    if (SEARCH_UPLOAD_INTENT.containsMatchIn(query)) return existing + fresh
    val f = NearDuplicateFilter(strictVersions = true)
    existing.forEach { f.add(it) }
    return existing + fresh.filter { f.accept(it) }
}

/** Higher is a better copy of the same song to show. */
internal fun uploadQuality(item: PlayableItem): Int {
    val t = item.title.lowercase()
    var q = when (SongKey.versionOf(item.title)) {
        SongVersion.ORIGINAL -> 100
        SongVersion.REMIX, SongVersion.LIVE, SongVersion.ACOUSTIC, SongVersion.UNPLUGGED -> 60
        SongVersion.COVER -> 40
        else -> 20 // slowed, 8D, sped up, lofi, karaoke, instrumental
    }
    val kind = UploadKinds.of(item.id)
    if (kind == UploadKind.AUDIO || item.artist.endsWith(" - Topic", ignoreCase = true) || "official audio" in t) q += 12
    else if (kind == UploadKind.OFFICIAL_VIDEO || RX_SK16.containsMatchIn(t)) q += 8
    else if (kind == UploadKind.USER_VIDEO) q -= 4
    else if (RX_SK17.containsMatchIn(t)) q += 4
    if (RX_SK18.containsMatchIn(t)) q -= 30
    return q
}

// Compiled once: building a Regex per call made the radio freeze the UI.
private val RX_SK0 = Regex("[,&]| x | and ")
private val RX_SK1 = Regex("\\s*[,&]\\s*|\\s+x\\s+|\\s+and\\s+")
private val RX_SK2 = Regex("\\s+")
private val RX_SK3 = Regex("[^\\p{L}\\p{M}\\p{N}]+")
private val RX_SK4 = Regex("\\b8d\\b")
private val RX_SK5 = Regex("\\bslowed\\b|\\breverb\\b")
private val RX_SK6 = Regex("sped\\s*up|nightcore|\\bfast(er)? version")
private val RX_SK7 = Regex("\\blo-?fi\\b")
private val RX_SK8 = Regex("\\bkaraoke\\b")
private val RX_SK9 = Regex("\\binstrumental\\b|\\bbgm\\b|\\bringtone\\b")
private val RX_SK10 = Regex("\\bunplugged\\b")
private val RX_SK11 = Regex("\\bacoustic\\b")
private val RX_SK12 = Regex("\\bcover\\b|\\bcovered by\\b|female version|male version")
private val RX_SK13 = Regex("\\blive\\b(?! (?:your|my|it|in the|forever|life))|\\bconcert\\b")
private val RX_SK14 = Regex("\\bremix\\b|\\bmix\\)|\\bflip\\b|\\bbootleg\\b|\\bvip mix\\b")
private val RX_SK15 = Regex("[^\\p{L}\\p{N}]+")
private val RX_SK16 = Regex("official (music )?video|\\bmv\\b")
private val RX_SK17 = Regex("lyric|lyrical")
private val RX_SK18 = Regex("\\b(teaser|trailer|promo|reaction|status|shorts|ringtone)\\b")
