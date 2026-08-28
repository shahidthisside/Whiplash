package com.whiplash.music.data.local.entity

import androidx.room.Entity

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
