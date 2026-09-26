package com.whiplash.music.domain.model

/**
 * Parser for LRC lyrics, including the "enhanced" word-timed variant.
 *
 * Supported input:
 * - Standard lines: `[mm:ss.xx] text` (also `[mm:ss]`, `[mm:ss:xx]`, 1–3 fraction digits).
 * - Several time tags on one line: `[00:10.00][01:20.00] chorus` produces one line per tag.
 * - Enhanced word tags: `[00:10.00] <00:10.00>Hello <00:10.40>world<00:11.00>`.
 *   Each `<time>` starts a word; a trailing tag with no text after it ends the last word.
 * - `[offset:+/-ms]` header (positive shifts lyrics earlier, per the LRC convention).
 * - Other headers (`[ar:]`, `[ti:]`, …) and malformed lines are ignored.
 *
 * Word timing is only kept when the whole line parses cleanly; otherwise the line
 * falls back to line-level sync (its [LyricLine.words] is empty), so a half-broken
 * enhanced file never shows a wrong highlight.
 *
 * Pure Kotlin, no Android types, so it is unit-tested directly.
 */
object LrcParser {

    fun parse(lrc: String): List<LyricLine> {
        var offsetMs = 0L
        val parsed = mutableListOf<LyricLine>()

        for (raw in lrc.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            val offsetMatch = OFFSET_REGEX.matchEntire(line)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                continue
            }

            // Collect every leading [time] tag.
            var rest = line
            val starts = mutableListOf<Long>()
            while (true) {
                val m = LEADING_TIME_REGEX.find(rest) ?: break
                val t = parseTime(m.groupValues[1]) ?: break
                starts += t
                rest = rest.substring(m.range.last + 1)
            }
            if (starts.isEmpty()) continue

            val (text, words) = parseBody(rest)
            for (start in starts) {
                parsed += LyricLine(timestampMs = start, text = text, words = words)
            }
        }

        // Header offset: positive = lyrics appear earlier.
        val shifted = if (offsetMs == 0L) parsed else parsed.map { it.shiftedBy(-offsetMs) }
        val sorted = shifted.sortedBy { it.timestampMs }
        return closeWordEnds(sorted)
    }

    /** Splits the part after the line tags into display text and (optional) word timings. */
    private fun parseBody(body: String): Pair<String, List<LyricWord>> {
        val tags = WORD_TIME_REGEX.findAll(body).toList()
        if (tags.isEmpty()) return body.trim() to emptyList()

        val words = mutableListOf<LyricWord>()
        var ok = true
        // Text before the first word tag belongs to no timed word; keep it in display text.
        val prefix = body.substring(0, tags.first().range.first)
        for ((i, tag) in tags.withIndex()) {
            val start = parseTime(tag.groupValues[1])
            if (start == null) { ok = false; break }
            val textEnd = if (i + 1 < tags.size) tags[i + 1].range.first else body.length
            val wordText = body.substring(tag.range.last + 1, textEnd)
            if (wordText.isEmpty()) {
                // A tag with no text closes the previous word.
                if (words.isNotEmpty() && words.last().endMs == null) {
                    words[words.lastIndex] = words.last().copy(endMs = start)
                }
                continue
            }
            if (words.isNotEmpty() && words.last().endMs == null) {
                words[words.lastIndex] = words.last().copy(endMs = start)
            }
            words += LyricWord(startMs = start, endMs = null, text = wordText)
        }

        val display = (prefix + words.joinToString("") { it.text })
            .replace(WHITESPACE_RUN, " ").trim()
        if (!ok || words.isEmpty() || prefix.isNotBlank() || !isNonDecreasing(words)) {
            // Strip the tags for display, drop word timing.
            val plain = body.replace(WORD_TIME_REGEX, "").replace(WHITESPACE_RUN, " ").trim()
            return plain to emptyList()
        }
        return display to words
    }

    private fun isNonDecreasing(words: List<LyricWord>): Boolean =
        words.zipWithNext().all { (a, b) -> b.startMs >= a.startMs }

    /** A word without an explicit end runs until the next line starts (or +1s for the final line). */
    private fun closeWordEnds(lines: List<LyricLine>): List<LyricLine> = lines.mapIndexed { i, line ->
        if (line.words.isEmpty() || line.words.last().endMs != null) return@mapIndexed line
        val nextStart = lines.getOrNull(i + 1)?.timestampMs
        val last = line.words.last()
        val end = (nextStart ?: (last.startMs + FINAL_WORD_MS)).coerceAtLeast(last.startMs)
        line.copy(words = line.words.dropLast(1) + last.copy(endMs = end))
    }

    private fun LyricLine.shiftedBy(deltaMs: Long): LyricLine = copy(
        timestampMs = (timestampMs + deltaMs).coerceAtLeast(0L),
        words = words.map {
            it.copy(
                startMs = (it.startMs + deltaMs).coerceAtLeast(0L),
                endMs = it.endMs?.let { e -> (e + deltaMs).coerceAtLeast(0L) },
            )
        },
    )

    /** `mm:ss`, `mm:ss.x`–`mm:ss.xxx`, or `mm:ss:xx`. Returns null when invalid. */
    internal fun parseTime(value: String): Long? {
        val m = TIME_REGEX.matchEntire(value.trim()) ?: return null
        val minutes = m.groupValues[1].toLongOrNull() ?: return null
        val seconds = m.groupValues[2].toLongOrNull() ?: return null
        if (seconds >= 60) return null
        val fraction = m.groupValues[3]
        val fracMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.take(3).toLong()
        }
        return minutes * 60_000L + seconds * 1000L + fracMs
    }

    private const val FINAL_WORD_MS = 1_000L
    private val TIME_REGEX = Regex("""(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?""")
    private val LEADING_TIME_REGEX = Regex("""^\s*\[([0-9:.]+)]""")
    private val WORD_TIME_REGEX = Regex("""<([0-9:.]+)>""")
    private val OFFSET_REGEX = Regex("""^\[offset:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)
    private val WHITESPACE_RUN = Regex("""\s+""")
}

/**
 * Index of the word being sung at [positionMs] in [line], or -1 before the first word.
 * Returns the last word index once every word has started (it stays lit until the line changes).
 */
fun activeWordIndex(line: LyricLine, positionMs: Long): Int {
    val words = line.words
    if (words.isEmpty() || positionMs < words.first().startMs) return -1
    var lo = 0
    var hi = words.lastIndex
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (words[mid].startMs <= positionMs) lo = mid else hi = mid - 1
    }
    return lo
}

/** 0..1 progress through [word] at [positionMs]. */
fun wordProgress(word: LyricWord, positionMs: Long): Float {
    val end = word.endMs ?: return if (positionMs >= word.startMs) 1f else 0f
    val span = end - word.startMs
    if (span <= 0) return if (positionMs >= word.startMs) 1f else 0f
    return ((positionMs - word.startMs).toFloat() / span).coerceIn(0f, 1f)
}
