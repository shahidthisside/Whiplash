// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.home

import com.whiplash.music.data.local.dao.PlayEventDao
import com.whiplash.music.domain.model.MediaSource
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.recommend.PlayEventFeedback
import com.whiplash.music.recommend.RadioRules
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

// Original work of Shahid Ansari (-SA). Not licensed for reuse.
/**
 * Quick Picks' best source, the way YouTube Music builds its own: the song
 * radios of tracks the listener actually finishes. Seeds are the most
 * finished songs of the last month (one per artist, the top one always
 * and a rotating few of the rest so a refresh changes the mix), falling
 * back to recent plays before there's enough history of finishes.
 */
class QuickPicksRadio(
    private val dao: PlayEventDao?,
    private val fetchRadio: suspend (seedId: String) -> List<PlayableItem.YoutubeTrack>,
) {
    private val feedback = PlayEventFeedback(dao)

    /** One list per seed radio, best first; empty when nothing can seed it. */
    suspend fun load(recent: List<PlayableItem>): List<List<PlayableItem.YoutubeTrack>> {
        val seeds = seeds(recent)
        if (seeds.isEmpty()) return emptyList()
        val rejected = runCatching { feedback.rejectedTrackIds() }.getOrDefault(emptySet())
        val blocked = runCatching { feedback.blockedArtistKeys() }.getOrDefault(emptySet())
        return coroutineScope {
            seeds.map { id ->
                async {
                    // One stuck radio mustn't hold up the rest; it just sits
                    // this refresh out.
                    runCatching { withTimeoutOrNull(RADIO_TIMEOUT_MS) { fetchRadio(id) } }.getOrNull().orEmpty()
                        .filter { it.id != id && it.id !in rejected && RadioRules.artistKey(it.artist) !in blocked }
                }
            }.awaitAll()
        }.filter { it.isNotEmpty() }
    }

    /** How many songs were finished since [sinceMs]. */
    suspend fun finishedSince(sinceMs: Long): Int =
        runCatching { dao?.completedSince(sinceMs) }.getOrNull() ?: 0

    private suspend fun seeds(recent: List<PlayableItem>): List<String> {
        val top = runCatching { dao?.topCompleted(System.currentTimeMillis() - 30L * DAY, 16) }.getOrNull().orEmpty()
        val picked = mutableListOf<String>()
        val artists = HashSet<String>()
        // The favourite always seeds; others rotate between refreshes.
        val ordered = top.take(1) + top.drop(1).shuffled()
        for (t in ordered) {
            if (picked.size >= MAX_SEEDS) break
            if (artists.add(RadioRules.artistKey(t.artist))) picked += t.trackId
        }
        if (picked.size < MAX_SEEDS) {
            recent.filter { it.source == MediaSource.YOUTUBE || it.source == MediaSource.DOWNLOAD }
                .forEach { item ->
                    if (picked.size < MAX_SEEDS && item.id !in picked && artists.add(RadioRules.artistKey(item.artist))) picked += item.id
                }
        }
        return picked
    }

    private companion object {
        const val MAX_SEEDS = 3
        const val RADIO_TIMEOUT_MS = 8_000L
        const val DAY = 24 * 3_600_000L
    }
}
