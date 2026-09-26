package com.whiplash.music.domain.model

/**
 * Pure maths for the lyrics view's word highlight and line styling, kept out
 * of Compose so it can be unit-tested.
 */

/** A word held at least this long gets the "bloom" glow while it is sung. */
const val HELD_WORD_MS = 900L

/** How a single word of the active line should be drawn at a given moment. */
data class WordHighlight(
    /** 0 = not reached yet, 1 = fully sung. */
    val fill: Float,
    /** 0..1 glow strength; only non-zero for a held word while it is being sung. */
    val bloom: Float,
)

fun isHeldWord(word: LyricWord): Boolean {
    val end = word.endMs ?: return false
    return end - word.startMs >= HELD_WORD_MS
}

/**
 * Highlight for every word of [line] at [positionMs]. Words before the current
 * one are fully sung, later ones are empty, and the current one fills with
 * [wordProgress]. A held word blooms in, peaks mid-way and fades out as it ends,
 * so the glow never snaps off.
 */
fun wordHighlights(line: LyricLine, positionMs: Long): List<WordHighlight> {
    val active = activeWordIndex(line, positionMs)
    return line.words.mapIndexed { i, word ->
        when {
            i < active -> WordHighlight(1f, 0f)
            i > active -> WordHighlight(0f, 0f)
            else -> {
                val p = wordProgress(word, positionMs)
                val bloom = if (isHeldWord(word) && p < 1f) bloomCurve(p) else 0f
                WordHighlight(p, bloom)
            }
        }
    }
}

/** Rises over the first 30 %, holds, then eases out over the last 25 %. */
internal fun bloomCurve(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return when {
        p < 0.3f -> p / 0.3f
        p > 0.75f -> (1f - p) / 0.25f
        else -> 1f
    }
}

/**
 * Visual emphasis for a line [distance] lines away from the active one
 * (negative = already sung). Returns alpha, and blur in "steps" (the UI turns
 * a step into dp). Already-sung lines fade a bit more than upcoming ones, so
 * the eye is drawn forward. With no active line yet (intro), every line is
 * treated as upcoming.
 */
data class LineEmphasis(val alpha: Float, val blurSteps: Float)

fun lineEmphasis(distance: Int): LineEmphasis = when {
    distance == 0 -> LineEmphasis(1f, 0f)
    distance > 0 -> LineEmphasis(
        alpha = (0.62f - 0.08f * (distance - 1)).coerceAtLeast(0.38f),
        blurSteps = distance.coerceAtMost(3).toFloat(),
    )
    else -> LineEmphasis(
        alpha = (0.5f - 0.06f * (-distance - 1)).coerceAtLeast(0.3f),
        blurSteps = (-distance).coerceAtMost(3).toFloat(),
    )
}
