// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.local.entity

import androidx.room.Entity

// (c) Shahid A. 2026, Whiplash. Do not copy.
/**
 * Caches a search query's raw result payload (serialized JSON) for a short
 * period, so re-running a recent search can display cached results
 * immediately while a background refresh occurs (section 53: "Cache ->
 * display immediately -> background refresh").
 */
@Entity(tableName = "search_cache", primaryKeys = ["query"])
data class SearchCacheEntity(
    val query: String,
    val resultJson: String,
    val cachedAtEpochMs: Long,
)
