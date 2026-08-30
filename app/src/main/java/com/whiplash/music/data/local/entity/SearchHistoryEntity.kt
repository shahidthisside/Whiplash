package com.whiplash.music.data.local.entity

import androidx.room.Entity

/**
 * A single remembered search query (YouTube Music-style "recent searches"),
 * shown when the search field is empty/idle so a user can re-run or resume
 * a past search without retyping it. Deliberately separate from
 * [SearchCacheEntity] — that table caches a query's *result payload* for a
 * short TTL to speed up re-searching, this table remembers the *query
 * strings themselves* indefinitely (until removed) purely for UI recall.
 */
@Entity(tableName = "search_history", primaryKeys = ["query"])
data class SearchHistoryEntity(
    val query: String,
    val searchedAtEpochMs: Long,
)
