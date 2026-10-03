// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.recommend

import com.whiplash.music.data.local.dao.PlayEventDao

/** [RadioFeedback] backed by the play-event log. */
class PlayEventFeedback(private val dao: PlayEventDao?) : RadioFeedback {

    private fun now() = System.currentTimeMillis()

    override suspend fun rejectedTrackIds(): Set<String> =
        dao?.rejectedTrackIds(now() - 60 * DAY, minSkips = 2)?.toSet().orEmpty()

    override suspend fun blockedArtistKeys(): Set<String> =
        dao?.artistFeedback(now() - 90 * DAY).orEmpty()
            .filter { it.plays >= 4 && it.skips >= it.plays * 0.75 && it.completes <= 1 }
            .mapTo(HashSet()) { it.artistKey }

    override suspend fun recentlyPlayedIds(): Set<String> =
        dao?.playedTrackIdsSince(now() - 3 * HOUR)?.toSet().orEmpty()

    override suspend fun artistLanguages(artistKeys: Collection<String>): Map<String, String> {
        val d = dao ?: return emptyMap()
        val keys = artistKeys.filter { it.isNotEmpty() }.distinct()
        if (keys.isEmpty()) return emptyMap()
        return keys.chunked(500).flatMap { d.artistLanguages(it) }
            .groupBy { it.artistKey }
            .mapValues { (_, rows) -> rows.maxBy { it.n }.language }
    }

    override suspend fun artistAffinity(): Map<String, Double> =
        dao?.artistFeedback(now() - 180 * DAY).orEmpty()
            .filter { it.plays >= 2 }
            .associate { it.artistKey to (it.completes + 1.0) / (it.plays + 2.0) }

    override suspend fun history(): List<PastPlay> =
        dao?.recent(HISTORY_LIMIT).orEmpty().filter { it.startedAtEpochMs >= now() - 120 * DAY }.map {
            PastPlay(it.trackId, it.title, it.artist, it.artistKey, it.language, it.origin, it.radioSeedId, it.startedAtEpochMs, it.completed, it.skipped)
        }

    private companion object {
        const val HISTORY_LIMIT = 3_000
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
