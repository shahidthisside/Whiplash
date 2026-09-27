package com.whiplash.music.domain.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * 4.7 Monthly Replay — the on-device recap of one calendar month's listening.
 * Pure logic, no Android types, so it's unit-tested directly.
 */

/** One song's tally for one month. */
data class ReplayTrackStat(
    val trackId: String,
    val source: MediaSource,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val plays: Int,
    val listenedMs: Long,
    val lastPlayedAtEpochMs: Long,
)

data class ReplayArtistStat(
    val name: String,
    val plays: Int,
    val listenedMs: Long,
    val songCount: Int,
    /** Artwork of the artist's most-played song — used as their picture. */
    val artworkUrl: String?,
)

data class ReplaySummary(
    val monthKey: String,
    val totalPlays: Int,
    val listenedMs: Long,
    val songCount: Int,
    val artistCount: Int,
    val topTracks: List<ReplayTrackStat>,
    val topArtists: List<ReplayArtistStat>,
) {
    val listenedMinutes: Long get() = listenedMs / 60_000L
    val isEmpty: Boolean get() = totalPlays == 0
}

/** How many songs/artists each Replay list shows. */
const val REPLAY_TOP_COUNT = 5

/** "yyyy-MM" for the month [epochMs] falls in, in [zone]. */
fun replayMonthKey(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    YearMonth.from(Instant.ofEpochMilli(epochMs).atZone(zone)).toString()

/** "September 2026" for "2026-09"; the raw key if it isn't a valid month. */
fun replayMonthLabel(monthKey: String, locale: Locale = Locale.getDefault()): String =
    runCatching {
        val ym = YearMonth.parse(monthKey)
        "${ym.month.getDisplayName(TextStyle.FULL, locale)} ${ym.year}"
    }.getOrDefault(monthKey)

/** Just the month's name, e.g. "September". */
fun replayMonthName(monthKey: String, locale: Locale = Locale.getDefault()): String =
    runCatching { YearMonth.parse(monthKey).month.getDisplayName(TextStyle.FULL, locale) }.getOrDefault(monthKey)

/**
 * The month Replay opens on: the current month once it has a few plays,
 * otherwise the latest month that has any (early in a new month, last
 * month's recap is the interesting one). Null when there's nothing at all.
 */
fun defaultReplayMonth(currentMonthKey: String, currentMonthPlays: Int, monthsWithPlays: List<String>): String? {
    if (currentMonthPlays >= MIN_PLAYS_FOR_CURRENT_MONTH) return currentMonthKey
    return monthsWithPlays.filter { it != currentMonthKey }.maxOrNull()
        ?: currentMonthKey.takeIf { currentMonthPlays > 0 }
}

/** Below this many plays the current month's recap is too thin to lead with. */
const val MIN_PLAYS_FOR_CURRENT_MONTH = 5

/**
 * Builds a month's recap. Songs rank by plays, then time listened, then most
 * recently played. Artists are grouped by primary name (case-insensitive, so
 * "Dua Lipa" and "Dua Lipa, DaBaby" count together) and rank the same way.
 */
fun buildReplaySummary(monthKey: String, stats: List<ReplayTrackStat>, top: Int = REPLAY_TOP_COUNT): ReplaySummary {
    val played = stats.filter { it.plays > 0 || it.listenedMs > 0 }
    val trackOrder = compareByDescending<ReplayTrackStat> { it.plays }
        .thenByDescending { it.listenedMs }
        .thenByDescending { it.lastPlayedAtEpochMs }
    val rankedTracks = played.sortedWith(trackOrder)

    val artists = rankedTracks
        .filter { primaryArtistName(it.artist).isNotBlank() }
        .groupBy { primaryArtistName(it.artist).lowercase(Locale.ROOT) }
        .values
        .map { songs ->
            ReplayArtistStat(
                // The spelling used by most plays, preferring a capitalised one
                // ("Dua Lipa" over an upload's "dua lipa").
                name = songs.groupBy { primaryArtistName(it.artist) }
                    .maxWith(compareBy({ it.key != it.key.lowercase(Locale.ROOT) }, { it.value.sumOf { s -> s.plays } }))
                    .key,
                plays = songs.sumOf { it.plays },
                listenedMs = songs.sumOf { it.listenedMs },
                songCount = songs.size,
                artworkUrl = songs.firstOrNull { it.artworkUrl != null }?.artworkUrl,
            ) to songs.maxOf { it.lastPlayedAtEpochMs }
        }
        .sortedWith(
            compareByDescending<Pair<ReplayArtistStat, Long>> { it.first.plays }
                .thenByDescending { it.first.listenedMs }
                .thenByDescending { it.second },
        )
        .map { it.first }

    return ReplaySummary(
        monthKey = monthKey,
        totalPlays = played.sumOf { it.plays },
        listenedMs = played.sumOf { it.listenedMs },
        songCount = played.count { it.plays > 0 },
        artistCount = artists.size,
        topTracks = rankedTracks.take(top),
        topArtists = artists.take(top),
    )
}
