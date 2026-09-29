package com.whiplash.music.recommend

/**
 * A language guess for a song. [confident] guesses come from the script the
 * title is written in or an explicit label ("Punjabi Song", "Bollywood");
 * weak ones come from romanised word markers and should only nudge.
 */
data class LanguageGuess(val code: String, val strength: Int) {
    /** Script or an explicit label: reliable enough to exclude a song on. */
    val confident: Boolean get() = strength >= STRONG

    companion object {
        /** Native script, or the title says so ("Punjabi Song", "Bollywood"). */
        const val STRONG = 3
        /** What the artist or label almost always releases. */
        const val ARTIST = 2
        /** Romanised words — Hindi songs borrow Punjabi words and English titles, so only a hint. */
        const val WEAK = 1

        operator fun invoke(code: String, confident: Boolean) = LanguageGuess(code, if (confident) STRONG else WEAK)
    }
}

/**
 * Detects a song's language from its title and artist, entirely on device
 * and with no model: native scripts map straight to a language (Gurmukhi is
 * Punjabi, Tamil script is Tamil…), explicit labels in the title win next,
 * and romanised titles fall back to marker words that are distinctive for
 * Punjabi, Hindi and English. Returns null when there's no real evidence —
 * the radio treats unknown as "allowed", never as a mismatch.
 */
object LanguageDetector {

    /**
     * Evidence in order of reliability: the title's script, an explicit
     * language label in the title, the artist/label's usual language, and
     * only then romanised marker words. A Hindi film song called "Excuses"
     * or "Tenu Leke" is still Hindi if the artist says so; a bilingual
     * artist (Diljit, Badshah, Guru Randhawa…) gives no artist evidence at
     * all rather than a wrong one.
     */
    fun detect(title: String, artist: String = ""): LanguageGuess? =
        cache.getOrPut(title + "\u0000" + artist) { compute(title, artist) ?: NONE }.takeIf { it !== NONE }

    private val NONE = LanguageGuess("", 0)
    private val cache = Memo<String, LanguageGuess>()

    private fun compute(title: String, artist: String): LanguageGuess? {
        // Script of the title first: the uploader name can be in another script.
        script(title)?.let { return LanguageGuess(it, LanguageGuess.STRONG) }
        val lowerTitle = " " + title.lowercase().replace(RX_LD0, " ") + " "
        label(lowerTitle)?.let { return LanguageGuess(it, LanguageGuess.STRONG) }
        artistLanguage(artist, lowerTitle)?.let { return LanguageGuess(it, LanguageGuess.ARTIST) }
        script(artist)?.let { return LanguageGuess(it, LanguageGuess.ARTIST) }
        romanised(lowerTitle)?.let { return it }
        // Big labels mostly release one language, but not only (T-Series puts
        // out Punjabi and Bhojpuri too), so a label alone is a weak hint.
        return LABEL_LANGUAGE[RadioRules.artistKey(artist)]?.let { LanguageGuess(it, LanguageGuess.WEAK) }
    }

    /** Whether [artistKey] is an artist this app knows by name (for reading "Artist - Title"). */
    fun isKnownArtist(artistKey: String): Boolean =
        artistKey.length >= 3 && (artistKey in ARTIST_LANGUAGE || artistKey in BILINGUAL)

    /** Languages close enough that a listener often mixes them (and titles borrow words). */
    fun family(code: String): String = when (code) {
        "pa", "hi", "ur", "hr", "bho" -> "north-indian"
        else -> code
    }

    private fun artistLanguage(artist: String, lowerTitle: String): String? {
        val key = RadioRules.artistKey(artist)
        if (key in BILINGUAL) return null
        ARTIST_LANGUAGE[key]?.let { return it }
        // Credited artists in the title ("| Sidhu Moose Wala |", "ft. Arijit Singh").
        val squashed = lowerTitle.replace(" ", "")
        return ARTIST_LANGUAGE.entries.firstOrNull { (k, _) -> k.length >= 6 && k !in BILINGUAL && squashed.contains(k) }?.value
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
        if (top == "hi" && RX_LD1.containsMatchIn(text)) return "mr"
        return top
    }

    private fun label(lower: String): String? = LABELS.firstOrNull { (re, _) -> re.containsMatchIn(lower) }?.second

    /** Marker words; needs a clear margin so a lone shared word ("dil") decides nothing. */
    private fun romanised(lower: String): LanguageGuess? {
        val words = lower.trim().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        fun score(set: Set<String>) = words.count { it in set }
        val pa = score(PUNJABI_WORDS)
        val hi = score(HINDI_WORDS)
        val en = score(ENGLISH_WORDS)
        return when {
            pa >= 2 && pa > hi -> LanguageGuess("pa", LanguageGuess.WEAK)
            hi >= 2 && hi > pa -> LanguageGuess("hi", LanguageGuess.WEAK)
            en >= 3 && pa == 0 && hi == 0 -> LanguageGuess("en", LanguageGuess.WEAK)
            else -> null
        }
    }

    // ٹ ڈ ڑ ں ے ہ ھ ک گ — letters Urdu uses and Arabic doesn't.
    private val URDU_ONLY = setOf('\u0679', '\u0688', '\u0691', '\u06BA', '\u06D2', '\u06C1', '\u06BE', '\u06A9', '\u06AF')

    private val LABELS: List<Pair<Regex, String>> = listOf(
        RX_LD2 to "pa",
        RX_LD3 to "hr",
        RX_LD4 to "bho",
        RX_LD5 to "ta",
        RX_LD6 to "te",
        RX_LD7 to "ml",
        RX_LD8 to "kn",
        RX_LD9 to "mr",
        RX_LD10 to "bn",
        RX_LD11 to "gu",
        RX_LD12 to "ko",
        RX_LD13 to "ja",
        RX_LD14 to "hi",
        RX_LD15 to "es",
    )

    /**
     * Artists and labels that release (almost) only one language, keyed by
     * [RadioRules.artistKey]. Deliberately excludes anyone who regularly
     * sings in two ([BILINGUAL]).
     */
    private val ARTIST_LANGUAGE: Map<String, String> = HashMap<String, String>().apply {
        fun put(lang: String, vararg keys: String) { for (k in keys) this[k] = lang }
        put("pa", "sidhumoosewala", "karanaujla", "shubh", "apdhillon", "ammyvirk", "babbumaan", "satindersartaaj",
            "gurdasmaan", "sukha", "chaninattan", "garrysandhu", "jassiegill", "parmishverma", "gurlezakhtar", "rajranjodh",
            "amritmaan", "arjandhillon", "prembhullar", "gippygrewal", "kulwinderbilla", "ninja", "mankirtaulakh",
            "nimratkhaira", "jasmandeep", "tarsemjassar", "harrdysandhu", "rnait", "gulabsidhu", "sultaan", "jordansandhu",
            "kalikamboj", "prabhsingh", "imrankhanworld", "imrankhan")
        put("hi", "arijitsingh", "shreyaghoshal", "jubinnautiyal", "darshanraval", "vishalmishra", "sachetparampara",
            "kishorekumar", "latamangeshkar", "mohammedrafi", "ashabhosle", "kumarsanu", "alkayagnik", "uditnarayan",
            "sonunigam", "kk", "papon", "shaan", "mohitchauhan", "ankittiwari", "palakmuchhal", "amaalmallik", "tanishkbagchi",
            "anuvjain", "prateekkuhad", "javedali", "sunidhichauhan", "shankarmahadevan", "amitabhbhattacharya", "pritam",
            "vishalshekhar", "salimsulaiman", "mithoon", "raghavchaitanya", "stebinben", "armaanmalik")
        put("ur", "atifaslam", "aliazafar", "asimazhar", "rahatfatehalikhan", "nusratfatehalikhan", "abidaparveen",
            "cokestudio", "cokestudiopakistan", "hasanraheem", "talhahanjum", "talhaanjum", "youngstunners", "aimakhan")
        put("hr", "masoomsharma", "dhandanyoliwala", "sapnachoudhary", "khasaaala", "ammyvirkharyanvi")
        put("ta", "anirudhravichander", "sidsriram", "ilaiyaraaja", "yuvanshankarraja", "gvprakashkumar", "harrisjayaraj",
            "dhanush")
        put("te", "devisriprasad", "dsp", "thaman", "thamans", "mangli", "sidsriramtelugu")
        put("ml", "sushinshyam", "vineethsreenivasan")
        put("kn", "arjunjanya", "vijayprakash")
        put("bn", "anupamroy", "rupamislam", "shironamhin", "artcell")
        put("mr", "ajayatul")
        put("ko", "bts", "blackpink", "twice", "straykids", "newjeans", "seventeen", "iu", "exo", "aespa", "lesserafim", "ive", "bigbang")
        put("ja", "yoasobi", "kenshiyonezu", "officialhigedandism", "radwimps", "adoofficial", "ado", "kinggnu", "mrsgreenapple")
        put("es", "badbunny", "karolg", "jbalvin", "rosalia", "pesopluma", "feid", "daddyyankee", "shakira", "ozuna", "anuel", "raualejandro")
        put("en", "theweeknd", "taylorswift", "edsheeran", "drake", "eminem", "travisscott", "kendricklamar", "billieeilish",
            "lanadelrey", "arianagrande", "dualipa", "coldplay", "imaginedragons", "linkinpark", "arcticmonkeys", "sza",
            "brunomars", "justinbieber", "postmalone", "oliviarodrigo", "harrystyles", "sabrinacarpenter", "maroon5",
            "onedirection", "adele", "samsmith", "shawnmendes", "charlieputh", "selenagomez", "rihanna", "beyonce",
            "ladygaga", "katyperry", "chainsmokers", "thechainsmokers", "alanwalker", "martingarrix", "avicii", "davidguetta",
            "calvinharris", "kanyewest", "ye", "jcole", "21savage", "futurehendrix", "future", "metroboomin", 
            "dojacat", "nickiminaj", "cardib", "megantheestallion", "lilnasx", "lilbaby", "juicewrld", "xxxtentacion",
            "nfrealmusic", "nf", "queen", "eagles", "metallica", "acdc", "gunsnroses", "nirvana", "radiohead", "oasis",
            "thebeatles", "michaeljackson", "whitneyhouston", "eltonjohn", "passenger", "lewiscapaldi", "jamesarthur",
            "tatemcrae", "gracieabrams", "noahkahan", "hozier", "bensonboone")
    }

    /** Labels and the language they mostly release (a weak hint only). */
    private val LABEL_LANGUAGE: Map<String, String> = mapOf("speedrecords" to "pa", "whitehill" to "pa", "whitehillmusic" to "pa", "humble" to "pa", "geetmp3" to "pa", "jassrecords" to "pa", "browntown" to "pa", "browntownmusic" to "pa", "desimelodi" to "pa", "jukedock" to "pa", "ishtarpunjabi" to "pa", "beingpunjabi" to "pa", "5911records" to "pa", "tseries" to "hi", "zeemusiccompany" to "hi", "tipsofficial" to "hi", "tips" to "hi", "yrf" to "hi", "saregama" to "hi", "saregamamusic" to "hi", "sonymusicindia" to "hi", "eroseenow" to "hi", "thinkmusicindia" to "ta", "sonymusicsouth" to "ta", "saregamatamil" to "ta", "adityamusic" to "te", "muzik247" to "ml", "lahari" to "kn", "laharimusic" to "kn", "zeemarathi" to "mr", "vyrlharyanvi" to "hr")

    /** Artists who release in more than one language: no artist evidence at all. */
    private val BILINGUAL = setOf(
        "diljitdosanjh", "badshah", "yoyohoneysingh", "honeysingh", "gururandhawa", "nehakakkar", "harrdy", "bpraak",
        "jasleenroyal", "sukhe", "mikasingh", "dalermehndi", "sukhwindersingh", "richasharma", "arrahman", "shreyaghoshalsouth",
        "sonymusicsouthindia", "spbalasubrahmanyam", "kinguniverse", "divine", "krsna", "raftaar", "emiway", "emiwaybantai",
        "seedhemaut", "bohemia", "ikka", "yoyo", "tonykakkar", "dhvanibhanushali", "sachinjigar", "vishaldadlani",
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

// Compiled once: building a Regex per call made the radio freeze the UI.
private val RX_LD0 = Regex("[^a-z0-9]+")
private val RX_LD1 = Regex("(?i)marathi")
private val RX_LD2 = Regex(" (punjabi|panjabi) ")
private val RX_LD3 = Regex(" haryanvi ")
private val RX_LD4 = Regex(" bhojpuri ")
private val RX_LD5 = Regex(" (tamil|kollywood) ")
private val RX_LD6 = Regex(" (telugu|tollywood) ")
private val RX_LD7 = Regex(" (malayalam|mollywood) ")
private val RX_LD8 = Regex(" (kannada|sandalwood) ")
private val RX_LD9 = Regex(" marathi ")
private val RX_LD10 = Regex(" (bengali|bangla) ")
private val RX_LD11 = Regex(" gujarati ")
private val RX_LD12 = Regex(" (k ?pop) ")
private val RX_LD13 = Regex(" (j ?pop|anime) ")
private val RX_LD14 = Regex(" (bollywood|hindi) ")
private val RX_LD15 = Regex(" (reggaeton|latino|en espanol) ")
