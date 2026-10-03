// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.onboarding

/** A language choice: [name] is stored and used in searches, [native] is shown under it. */
data class TasteLanguage(val name: String, val native: String)

/** A genre tile: [query] is what Home searches for, [colors] its gradient (ARGB). */
data class TasteGenre(val name: String, val query: String, val colors: Pair<Long, Long>)

/**
 * Fixed choices for onboarding. Artist suggestions are hand-picked popular
 * names per language and genre, then looked up on YouTube for their photo;
 * anything else can be found with the artist search.
 */
object OnboardingCatalog {

    val languages = listOf(
        TasteLanguage("English", "English"),
        TasteLanguage("Hindi", "हिन्दी"),
        TasteLanguage("Punjabi", "ਪੰਜਾਬੀ"),
        TasteLanguage("Tamil", "தமிழ்"),
        TasteLanguage("Telugu", "తెలుగు"),
        TasteLanguage("Bengali", "বাংলা"),
        TasteLanguage("Marathi", "मराठी"),
        TasteLanguage("Urdu", "اردو"),
        TasteLanguage("Spanish", "Español"),
        TasteLanguage("Korean", "한국어"),
        TasteLanguage("Japanese", "日本語"),
        TasteLanguage("Arabic", "العربية"),
        TasteLanguage("French", "Français"),
        TasteLanguage("Portuguese", "Português"),
    )

    val genres = listOf(
        TasteGenre("Pop", "pop hits", 0xFFE0457B to 0xFF7B2FF7),
        TasteGenre("Hip-hop", "hip hop hits", 0xFFF7971E to 0xFFB2361B),
        TasteGenre("Bollywood", "bollywood hits", 0xFFFF6A00 to 0xFFEE0979),
        TasteGenre("Romantic", "romantic songs", 0xFFFF5E62 to 0xFF9B1B4A),
        TasteGenre("Rock", "rock classics", 0xFF434343 to 0xFFB71C1C),
        TasteGenre("EDM", "edm hits", 0xFF00C6FF to 0xFF3A0CA3),
        TasteGenre("Lo-fi", "lofi chill beats", 0xFF7F7FD5 to 0xFF3C4A7A),
        TasteGenre("R&B", "r&b hits", 0xFF8E2DE2 to 0xFF4A00E0),
        TasteGenre("Indie", "indie songs", 0xFF43C6AC to 0xFF1D5C57),
        TasteGenre("Punjabi pop", "punjabi hits", 0xFFFDC830 to 0xFFD35400),
        TasteGenre("K-pop", "kpop hits", 0xFFFF9A9E to 0xFFC2185B),
        TasteGenre("Sufi", "sufi songs", 0xFFB79891 to 0xFF5D4037),
        TasteGenre("Party", "party songs", 0xFFF953C6 to 0xFFB91D73),
        TasteGenre("Chill", "chill songs", 0xFF56CCF2 to 0xFF2F80ED),
        TasteGenre("Jazz", "jazz classics", 0xFFC79081 to 0xFF3E2723),
        TasteGenre("Classical", "classical music", 0xFFBDC3C7 to 0xFF2C3E50),
        TasteGenre("Metal", "metal songs", 0xFF232526 to 0xFF616161),
        TasteGenre("Devotional", "devotional songs", 0xFFFFB75E to 0xFFED8F03),
        TasteGenre("Workout", "workout songs", 0xFF11998E to 0xFF38EF7D),
        TasteGenre("Latin", "latin hits", 0xFFFF512F to 0xFFDD2476),
    )

    private val artistsByLanguage = mapOf(
        "English" to listOf("Taylor Swift", "The Weeknd", "Ed Sheeran", "Coldplay", "Billie Eilish", "Dua Lipa", "Drake", "Imagine Dragons", "Ariana Grande", "Bruno Mars", "Olivia Rodrigo", "Post Malone"),
        "Hindi" to listOf("Arijit Singh", "Shreya Ghoshal", "Pritam", "A. R. Rahman", "Atif Aslam", "Jubin Nautiyal", "Neha Kakkar", "Armaan Malik", "Sonu Nigam", "Badshah", "Vishal-Shekhar", "KK"),
        "Punjabi" to listOf("Diljit Dosanjh", "AP Dhillon", "Karan Aujla", "Sidhu Moose Wala", "Guru Randhawa", "Shubh"),
        "Tamil" to listOf("Anirudh Ravichander", "A. R. Rahman", "Sid Sriram", "Yuvan Shankar Raja", "Harris Jayaraj"),
        "Telugu" to listOf("Devi Sri Prasad", "S. Thaman", "Sid Sriram", "M. M. Keeravani"),
        "Bengali" to listOf("Anupam Roy", "Arijit Singh", "Shreya Ghoshal", "Nachiketa"),
        "Marathi" to listOf("Ajay-Atul", "Shankar Mahadevan", "Avadhoot Gupte"),
        "Urdu" to listOf("Atif Aslam", "Rahat Fateh Ali Khan", "Nusrat Fateh Ali Khan", "Ali Sethi", "Abida Parveen"),
        "Spanish" to listOf("Bad Bunny", "Shakira", "Karol G", "J Balvin", "Rosalía"),
        "Korean" to listOf("BTS", "BLACKPINK", "NewJeans", "Stray Kids", "TWICE"),
        "Japanese" to listOf("YOASOBI", "Kenshi Yonezu", "Ado", "LiSA"),
        "Arabic" to listOf("Amr Diab", "Nancy Ajram", "Elissa"),
        "French" to listOf("Stromae", "Aya Nakamura", "Indila"),
        "Portuguese" to listOf("Anitta", "Marília Mendonça", "Jorge & Mateus"),
    )

    private val artistsByGenre = mapOf(
        "Hip-hop" to listOf("Eminem", "Kendrick Lamar", "Travis Scott", "Divine"),
        "Rock" to listOf("Linkin Park", "Queen", "Arctic Monkeys", "Nirvana"),
        "EDM" to listOf("Alan Walker", "Martin Garrix", "Avicii", "Marshmello"),
        "Lo-fi" to listOf("Lofi Girl", "Prateek Kuhad"),
        "R&B" to listOf("SZA", "Frank Ocean", "The Weeknd"),
        "Indie" to listOf("Prateek Kuhad", "Arctic Monkeys", "Lana Del Rey", "The Local Train"),
        "K-pop" to listOf("BTS", "BLACKPINK", "NewJeans"),
        "Sufi" to listOf("Nusrat Fateh Ali Khan", "Rahat Fateh Ali Khan", "Abida Parveen"),
        "Jazz" to listOf("Norah Jones", "Frank Sinatra"),
        "Classical" to listOf("Ludovico Einaudi", "Hans Zimmer"),
        "Metal" to listOf("Metallica", "Bring Me The Horizon"),
        "Latin" to listOf("Bad Bunny", "Shakira", "Karol G"),
        "Punjabi pop" to listOf("Diljit Dosanjh", "AP Dhillon", "Karan Aujla"),
        "Bollywood" to listOf("Arijit Singh", "Pritam", "Shreya Ghoshal"),
        "Devotional" to listOf("Hariharan", "Anuradha Paudwal"),
    )

    /**
     * The best-known names across the app's audience, used to fill the
     * artist step when the picks alone give only a handful (Marathi, say).
     */
    private val topArtists = listOf(
        "Arijit Singh", "Taylor Swift", "Diljit Dosanjh", "The Weeknd", "Shreya Ghoshal", "AP Dhillon",
        "Ed Sheeran", "Pritam", "Billie Eilish", "A. R. Rahman", "Karan Aujla", "Dua Lipa",
        "Atif Aslam", "BTS", "Sidhu Moose Wala", "Coldplay", "Badshah", "Bad Bunny",
        "Anirudh Ravichander", "Drake", "Neha Kakkar", "Bruno Mars", "Sid Sriram", "Eminem",
        "Jubin Nautiyal", "BLACKPINK", "Imagine Dragons", "Shakira",
    )

    /**
     * Artist names to suggest for the picked [languages] and [genres], most
     * relevant first, without repeats, topped up with [topArtists] to at
     * least [min] so the step never looks empty. Nothing picked: a mix of
     * English and Hindi, the two biggest groups.
     */
    fun suggestedArtists(languages: List<String>, genres: List<String>, max: Int = 24, min: Int = 24): List<String> {
        val langs = languages.ifEmpty { listOf("English", "Hindi") }
        val lists = langs.mapNotNull { artistsByLanguage[it] } + genres.mapNotNull { artistsByGenre[it] }
        // Round-robin so every pick is represented near the top.
        val out = LinkedHashSet<String>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) for (list in lists) list.getOrNull(i)?.let { out += it }
        for (name in topArtists) {
            if (out.size >= min) break
            out += name
        }
        return out.take(max)
    }

    fun genre(name: String): TasteGenre? = genres.firstOrNull { it.name == name }
}
