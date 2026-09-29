package com.whiplash.music.recommend

/**
 * A language guess for a song. [confident] guesses come from the script the
 * title is written in or an explicit label ("Punjabi Song", "Bollywood");
 * weak ones come from romanised word markers and should only nudge.
 */
data class LanguageGuess(val code: String, val confident: Boolean)

/**
 * Detects a song's language from its title and artist, entirely on device
 * and with no model: native scripts map straight to a language (Gurmukhi is
 * Punjabi, Tamil script is Tamil…), explicit labels in the title win next,
 * and romanised titles fall back to marker words that are distinctive for
 * Punjabi, Hindi and English. Returns null when there's no real evidence —
 * the radio treats unknown as "allowed", never as a mismatch.
 */
object LanguageDetector {

    fun detect(title: String, artist: String = ""): LanguageGuess? {
        val text = "$title $artist"
        script(text)?.let { return LanguageGuess(it, confident = true) }
        val lower = " " + text.lowercase().replace(Regex("[^a-z0-9]+"), " ") + " "
        label(lower)?.let { return LanguageGuess(it, confident = true) }
        channel(lower)?.let { return LanguageGuess(it, confident = false) }
        return romanised(lower)
    }

    /** Counts letters per script; the dominant non-Latin script decides. */
    internal fun script(text: String): String? {
        val counts = HashMap<String, Int>()
        var urduLetters = 0
        for (ch in text) {
            val code = when (ch.code) {
                in 0x0A00..0x0A7F -> "pa"   // Gurmukhi
                in 0x0900..0x097F -> "hi"   // Devanagari (Hindi, Marathi, Bhojpuri…)
                in 0x0B80..0x0BFF -> "ta"
                in 0x0C00..0x0C7F -> "te"
                in 0x0980..0x09FF -> "bn"
                in 0x0A80..0x0AFF -> "gu"
                in 0x0C80..0x0CFF -> "kn"
                in 0x0D00..0x0D7F -> "ml"
                in 0x0B00..0x0B7F -> "or"
                in 0x0600..0x06FF, in 0x0750..0x077F -> {
                    if (ch in URDU_ONLY) urduLetters++
                    "ar"
                }
                in 0xAC00..0xD7AF, in 0x1100..0x11FF, in 0x3130..0x318F -> "ko"
                in 0x3040..0x30FF -> "ja"   // Hiragana/Katakana
                in 0x4E00..0x9FFF -> "zh"   // Han (also used in Japanese; kana decides that)
                in 0x0400..0x04FF -> "ru"
                in 0x0E00..0x0E7F -> "th"
                else -> null
            } ?: continue
            counts[code] = (counts[code] ?: 0) + 1
        }
        if (counts.isEmpty()) return null
        if ((counts["ja"] ?: 0) > 0) return "ja"
        val top = counts.maxByOrNull { it.value }!!.key
        if (top == "ar" && urduLetters > 0) return "ur"
        if (top == "hi" && Regex("(?i)marathi").containsMatchIn(text)) return "mr"
        return top
    }

    private fun label(lower: String): String? = LABELS.firstOrNull { (re, _) -> re.containsMatchIn(lower) }?.second

    private fun channel(lower: String): String? = CHANNELS.entries.firstOrNull { lower.contains(" ${it.key} ") }?.value

    /** Marker words; needs a clear margin so a lone shared word ("dil") decides nothing. */
    private fun romanised(lower: String): LanguageGuess? {
        val words = lower.trim().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        fun score(set: Set<String>) = words.count { it in set }
        val pa = score(PUNJABI_WORDS)
        val hi = score(HINDI_WORDS)
        val en = score(ENGLISH_WORDS)
        return when {
            pa >= 1 && pa > hi -> LanguageGuess("pa", confident = false)
            hi >= 2 && hi > pa -> LanguageGuess("hi", confident = false)
            en >= 2 && pa == 0 && hi == 0 -> LanguageGuess("en", confident = false)
            else -> null
        }
    }

    // ٹ ڈ ڑ ں ے ہ ھ ک گ — letters Urdu uses and Arabic doesn't.
    private val URDU_ONLY = setOf('\u0679', '\u0688', '\u0691', '\u06BA', '\u06D2', '\u06C1', '\u06BE', '\u06A9', '\u06AF')

    private val LABELS: List<Pair<Regex, String>> = listOf(
        Regex(" (punjabi|panjabi) ") to "pa",
        Regex(" haryanvi ") to "hr",
        Regex(" bhojpuri ") to "bho",
        Regex(" (tamil|kollywood) ") to "ta",
        Regex(" (telugu|tollywood) ") to "te",
        Regex(" (malayalam|mollywood) ") to "ml",
        Regex(" (kannada|sandalwood) ") to "kn",
        Regex(" marathi ") to "mr",
        Regex(" (bengali|bangla) ") to "bn",
        Regex(" gujarati ") to "gu",
        Regex(" (k ?pop) ") to "ko",
        Regex(" (j ?pop|anime) ") to "ja",
        Regex(" (bollywood|hindi) ") to "hi",
        Regex(" (reggaeton|latino|en espanol) ") to "es",
    )

    /** Uploader channels that release almost only one language. */
    private val CHANNELS: Map<String, String> = mapOf(
        "speed records" to "pa", "white hill music" to "pa", "humble music" to "pa",
        "geet mp3" to "pa", "jass records" to "pa", "brown town music" to "pa",
        "sidhu moose wala" to "pa", "karan aujla" to "pa", "diljit dosanjh" to "pa",
        "ap dhillon" to "pa", "apdhillon" to "pa", "shubh" to "pa", "ammy virk" to "pa",
        "vyrl haryanvi" to "hr",
        "think music india" to "ta", "sony music south" to "ta", "anirudh ravichander" to "ta",
        "aditya music" to "te", "lahari music" to "kn",
        "zee music company" to "hi", "tips official" to "hi", "yrf" to "hi",
        "arijit singh" to "hi", "shreya ghoshal" to "hi",
    )

    private val PUNJABI_WORDS = setOf(
        "jatt", "jatti", "kudi", "kudiye", "munde", "mundeya", "gabru", "tenu", "menu", "sanu",
        "tuhanu", "vich", "nal", "naal", "pind", "sohni", "sohniye", "haaye", "vekh", "dasdi",
        "pagg", "yaara", "yaarian", "mitran", "mittran", "jawani", "shonk", "laavan", "ranjha",
    )

    private val HINDI_WORDS = setOf(
        "tujhe", "mujhe", "tujhse", "mujhse", "hai", "hain", "kya", "nahi", "nahin", "hum", "tum",
        "kyun", "kabhi", "mera", "meri", "mere", "tera", "teri", "tere", "sanam", "pyaar", "zindagi",
        "bina", "saath", "raha", "rahe", "kaise", "jaana", "dekha", "chal",
    )

    private val ENGLISH_WORDS = setOf(
        "the", "you", "me", "my", "i", "and", "of", "in", "to", "your", "love", "we", "it",
        "on", "all", "for", "with", "don", "t", "is", "be", "baby", "night", "feel", "like",
    )
}
