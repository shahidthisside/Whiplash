package com.whiplash.music.domain.model

/**
 * Result of a lyrics lookup (CLAUDE.md section 20). Modeled as a sealed
 * type rather than a nullable string so the UI can render a distinct,
 * honest state for every real outcome — including [Unavailable], which is
 * shown whenever no real lyrics exist for a track rather than fabricating
 * placeholder text.
 */
sealed interface LyricsResult {

    /** Real, live-synced lyrics (LRC-format timestamps) from the lyrics provider. */
    data class Synced(val lines: List<LyricLine>) : LyricsResult

    /** Real lyrics text exists, but with no line-level timing data. */
    data class Plain(val text: String) : LyricsResult

    /** The lookup succeeded but no lyrics exist for this track — a real, honest negative result. */
    data object Unavailable : LyricsResult

    /** The lookup itself failed (network error, etc.), distinct from a genuine "no lyrics" result. */
    data class Error(val message: String) : LyricsResult
}

/** A single synced lyrics line with its start timestamp, in milliseconds. */
data class LyricLine(val timestampMs: Long, val text: String)
