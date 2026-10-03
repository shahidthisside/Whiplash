package com.whiplash.music.ui.home

import java.time.Instant
import java.time.ZoneId

/**
 * Decides whether opening the app should rebuild Quick Picks or keep the
 * saved list. Rebuilding on every launch spent data and changed the list a
 * second after it appeared; recommendations don't go stale in minutes, so
 * the saved list stays until there's a reason to change it.
 */
object QuickPicksRefreshPolicy {

    enum class Network { OFFLINE, METERED, UNMETERED }

    enum class Decision {
        /** Build a fresh list now. */
        REFRESH,
        /** The saved list is still good. */
        KEEP,
        /** A refresh is due but there's no connection; do it when one comes back. */
        WAIT_FOR_NETWORK,
    }

    /** Saved list younger than this is kept on Wi-Fi. */
    const val MAX_AGE_MS = 3 * 3_600_000L

    /** On mobile data the saved list is kept longer. */
    const val MAX_AGE_METERED_MS = 6 * 3_600_000L

    /** Finishing this many songs since the list was built means taste may have moved. */
    const val NEW_FINISHES = 5

    /** How many of the current top artists are checked against the ones at build time. */
    const val TOP_ARTISTS_CHECKED = 3

    data class Inputs(
        val nowMs: Long,
        /** Null when there's no saved list or it predates build info. */
        val builtAtMs: Long?,
        val hasList: Boolean,
        val finishedSinceBuild: Int,
        val topArtistsAtBuild: List<String>,
        val topArtistsNow: List<String>,
        val network: Network,
        val zone: ZoneId = ZoneId.systemDefault(),
    )

    fun decide(inputs: Inputs, forced: Boolean = false): Decision {
        // Rule 6: pull-to-refresh and the refresh button always rebuild.
        if (forced) return Decision.REFRESH
        if (!isDue(inputs)) return Decision.KEEP
        // Rule 4: never try offline; the reconnect does it.
        return if (inputs.network == Network.OFFLINE) Decision.WAIT_FOR_NETWORK else Decision.REFRESH
    }

    private fun isDue(i: Inputs): Boolean {
        val builtAt = i.builtAtMs ?: return true
        if (!i.hasList) return true
        // A clock set back makes the age meaningless; rebuild.
        if (builtAt > i.nowMs) return true
        // Rule 3: first launch of a new day.
        val builtDay = Instant.ofEpochMilli(builtAt).atZone(i.zone).toLocalDate()
        val today = Instant.ofEpochMilli(i.nowMs).atZone(i.zone).toLocalDate()
        if (builtDay != today) return true
        // Rule 2: taste moved since the build (applies on mobile data too).
        if (i.finishedSinceBuild >= NEW_FINISHES) return true
        val before = i.topArtistsAtBuild.map { it.lowercase() }.toSet()
        if (i.topArtistsNow.take(TOP_ARTISTS_CHECKED).any { it.lowercase() !in before }) return true
        // Rules 1 and 4: age, with a longer limit on mobile data.
        val limit = if (i.network == Network.METERED) MAX_AGE_METERED_MS else MAX_AGE_MS
        return i.nowMs - builtAt >= limit
    }
}
