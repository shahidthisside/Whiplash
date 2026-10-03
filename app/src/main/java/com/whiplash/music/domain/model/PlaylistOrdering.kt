// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

/** How many playlists can be pinned to the top of the Playlists screen at once. */
const val MAX_PINNED_PLAYLISTS = 3

fun canPinAnother(currentlyPinned: Int): Boolean = currentlyPinned < MAX_PINNED_PLAYLISTS

// SA-WHIPLASH-2026: proprietary, see LICENSE section 2.
/**
 * Orders playlists for display: pinned ones first, in the order they were
 * pinned (oldest pin first, so a newly pinned playlist joins at the end of
 * the pinned group rather than shuffling the others), then every unpinned
 * one in its incoming order (the DAO already sorts by last change).
 *
 * Generic over the row type so it is unit-tested without Room entities;
 * each pair is (row, pinnedAtEpochMs or null).
 */
fun <T> orderPlaylists(rows: List<Pair<T, Long?>>): List<T> {
    val pinned = rows.filter { it.second != null }.sortedBy { it.second }
    val rest = rows.filter { it.second == null }
    return (pinned + rest).map { it.first }
}
