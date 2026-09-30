package com.whiplash.music.recommend

/**
 * How well autoplay is doing, from the play log: how often its songs are
 * skipped or finished (overall and per source), and how often it drifted
 * off the seed's language family.
 */
data class RadioMetrics(
    val plays: Int,
    val skipRate: Double,
    val completionRate: Double,
    val bySource: Map<String, Pair<Int, Double>>,
    val languageDrift: Double,
) {
    override fun toString(): String =
        "autoplay plays=$plays skip=${pct(skipRate)} complete=${pct(completionRate)} drift=${pct(languageDrift)} " +
            bySource.entries.joinToString(" ", "sources[", "]") { (s, v) -> "$s:${v.first}/${pct(v.second)}kept" }

    companion object {
        fun from(plays: List<PastPlay>): RadioMetrics {
            val auto = plays.filter { it.fromAutoplay }
            if (auto.isEmpty()) return RadioMetrics(0, 0.0, 0.0, emptyMap(), 0.0)
            val seedLanguage = plays.filter { !it.fromAutoplay && it.language != null }.associate { it.trackId to it.language!! }
            val judged = auto.mapNotNull { p ->
                val seed = p.radioSeedId?.let(seedLanguage::get) ?: return@mapNotNull null
                p.language?.let { LanguageDetector.family(it) != LanguageDetector.family(seed) }
            }
            return RadioMetrics(
                plays = auto.size,
                skipRate = auto.count { it.skipped }.toDouble() / auto.size,
                completionRate = auto.count { it.completed }.toDouble() / auto.size,
                bySource = auto.groupBy { it.source ?: "?" }.mapValues { (_, v) -> v.size to v.count { !it.skipped }.toDouble() / v.size },
                languageDrift = if (judged.isEmpty()) 0.0 else judged.count { it }.toDouble() / judged.size,
            )
        }

        private fun pct(x: Double) = "${(x * 100).toInt()}%"
    }
}
