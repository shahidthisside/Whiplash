package com.whiplash.music.ui.common

/**
 * Formats large counts (subscribers, view counts, etc.) the way YouTube's
 * own UI does — abbreviated with K/M/B suffixes — rather than a raw
 * unformatted number like "339000000", which reads as a debug value
 * rather than a polished, standard music-app UI treatment.
 */
fun formatCompactCount(count: Long): String = when {
    count < 1_000 -> count.toString()
    count < 1_000_000 -> formatWithSuffix(count, 1_000.0, "K")
    count < 1_000_000_000 -> formatWithSuffix(count, 1_000_000.0, "M")
    else -> formatWithSuffix(count, 1_000_000_000.0, "B")
}

private fun formatWithSuffix(count: Long, divisor: Double, suffix: String): String {
    val value = count / divisor
    // One decimal place, but drop a trailing ".0" (e.g. "3.0M" -> "3M", matching YouTube's own convention).
    val rounded = "%.1f".format(value)
    val trimmed = if (rounded.endsWith(".0")) rounded.dropLast(2) else rounded
    return "$trimmed$suffix"
}
