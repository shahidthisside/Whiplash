// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Colours pulled from a track's cover art for the Now Playing screen.
 *
 * [accent] is a single bright colour that reads well on a dark background
 * (play button, active toggles). It is null for greyscale covers, where
 * any "accent" would be invented, so the caller keeps the theme accent.
 *
 * [mesh] holds four darkened colours sampled from the cover's own
 * quadrants (top-left, top-right, bottom-left, bottom-right), so the
 * backdrop keeps the artwork's colour *arrangement* rather than flattening
 * it into one tint. They are dark enough that white text stays legible.
 *
 * All colours are packed ARGB ints so this file has no Android or Compose
 * dependency and can be unit-tested on the JVM.
 */
data class ArtworkPalette(
    val accent: Int?,
    val mesh: List<Int>,
)

private const val MIN_ACCENT_SATURATION = 0.18f
private const val ACCENT_MIN_LIGHTNESS = 0.62f
private const val ACCENT_MAX_LIGHTNESS = 0.80f
private const val MESH_MIN_LIGHTNESS = 0.10f
private const val MESH_MAX_LIGHTNESS = 0.34f

/**
 * Extracts an [ArtworkPalette] from raw ARGB [pixels] of a small, already
 * downscaled copy of the cover (the caller decodes ~48px, so this is at
 * most a few thousand pixels). Returns null if there is nothing usable,
 * e.g. an empty or fully transparent image.
 */
fun extractArtworkPalette(pixels: IntArray, width: Int, height: Int): ArtworkPalette? {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return null

    // Quadrant sums for the mesh, and a coarse 4-bit-per-channel histogram
    // for the accent. The histogram score favours saturated, mid-lightness
    // colours, so a large grey or black area can't win just by being big.
    val quadSums = Array(4) { LongArray(4) } // r, g, b, count
    val bucketScore = FloatArray(4096)
    val bucketSums = Array(4096) { LongArray(4) }
    val hsl = FloatArray(3)
    var opaquePixels = 0

    for (y in 0 until height) {
        for (x in 0 until width) {
            val c = pixels[y * width + x]
            if ((c ushr 24) < 128) continue
            opaquePixels++
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            val q = (if (y * 2 < height) 0 else 2) + (if (x * 2 < width) 0 else 1)
            quadSums[q][0] += r.toLong(); quadSums[q][1] += g.toLong(); quadSums[q][2] += b.toLong(); quadSums[q][3]++

            rgbToHsl(r, g, b, hsl)
            val s = hsl[1]
            val l = hsl[2]
            if (s < MIN_ACCENT_SATURATION || l < 0.12f || l > 0.92f) continue
            val bucket = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            // Mid lightness scores best: l = 0.5 -> 1.0, edges -> ~0.4.
            val lightnessWeight = 1f - abs(l - 0.5f) * 1.2f
            bucketScore[bucket] += s * lightnessWeight
            val sums = bucketSums[bucket]
            sums[0] += r.toLong(); sums[1] += g.toLong(); sums[2] += b.toLong(); sums[3]++
        }
    }
    if (opaquePixels == 0) return null

    val overallAverage = averageOf(quadSums.reduce { a, b -> LongArray(4) { i -> a[i] + b[i] } })
    val mesh = quadSums.map { sums ->
        val base = if (sums[3] > 0) averageOf(sums) else overallAverage
        toneForMesh(base)
    }

    // A single bucket is 1/4096 of colour space, so merge each candidate
    // with its immediate neighbours before choosing; otherwise a smooth
    // gradient splits its votes and a small flat patch wins instead.
    var bestBucket = -1
    var bestScore = 0f
    for (bucket in 0 until 4096) {
        if (bucketScore[bucket] <= 0f) continue
        val score = neighbourhoodScore(bucketScore, bucket)
        if (score > bestScore) {
            bestScore = score
            bestBucket = bucket
        }
    }
    // Require the accent to cover a meaningful share of the cover (≈2% of
    // pixels at full weight) so a few stray coloured pixels in a black and
    // white photo don't produce a loud accent.
    val accent = if (bestBucket >= 0 && bestScore >= opaquePixels * 0.02f) {
        toneForAccent(averageOf(bucketSums[bestBucket]))
    } else {
        null
    }
    return ArtworkPalette(accent = accent, mesh = mesh)
}

private fun neighbourhoodScore(scores: FloatArray, bucket: Int): Float {
    val r = bucket shr 8
    val g = (bucket shr 4) and 0xF
    val b = bucket and 0xF
    var total = 0f
    for (dr in -1..1) for (dg in -1..1) for (db in -1..1) {
        val nr = r + dr; val ng = g + dg; val nb = b + db
        if (nr !in 0..15 || ng !in 0..15 || nb !in 0..15) continue
        val weight = if (dr == 0 && dg == 0 && db == 0) 1f else 0.5f
        total += scores[(nr shl 8) or (ng shl 4) or nb] * weight
    }
    return total
}

private fun averageOf(sums: LongArray): Int {
    val n = sums[3].coerceAtLeast(1)
    return packRgb((sums[0] / n).toInt(), (sums[1] / n).toInt(), (sums[2] / n).toInt())
}

/** Keeps hue, caps saturation and lifts lightness so it reads on a dark UI. */
private fun toneForAccent(color: Int): Int {
    val hsl = FloatArray(3)
    rgbToHsl((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF, hsl)
    hsl[1] = min(hsl[1], 0.75f).coerceAtLeast(0.35f)
    hsl[2] = hsl[2].coerceIn(ACCENT_MIN_LIGHTNESS, ACCENT_MAX_LIGHTNESS)
    return hslToRgb(hsl)
}

/** Keeps hue, trims saturation and darkens so white text stays legible. */
private fun toneForMesh(color: Int): Int {
    val hsl = FloatArray(3)
    rgbToHsl((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF, hsl)
    hsl[1] = min(hsl[1], 0.65f)
    hsl[2] = hsl[2].coerceIn(MESH_MIN_LIGHTNESS, MESH_MAX_LIGHTNESS)
    return hslToRgb(hsl)
}

/** Relative luminance (0..1) of a packed colour, for choosing icon colour on top of it. */
fun relativeLuminance(color: Int): Float {
    fun channel(v: Int): Float {
        val c = v / 255f
        return if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
    return 0.2126f * channel((color shr 16) and 0xFF) +
        0.7152f * channel((color shr 8) and 0xFF) +
        0.0722f * channel(color and 0xFF)
}

internal fun packRgb(r: Int, g: Int, b: Int): Int =
    (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

internal fun rgbToHsl(r: Int, g: Int, b: Int, out: FloatArray) {
    val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
    val mx = max(rf, max(gf, bf)); val mn = min(rf, min(gf, bf))
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d == 0f) {
        out[0] = 0f; out[1] = 0f; out[2] = l
        return
    }
    val s = d / (1f - abs(2f * l - 1f))
    val h = when (mx) {
        rf -> ((gf - bf) / d).mod(6f)
        gf -> (bf - rf) / d + 2f
        else -> (rf - gf) / d + 4f
    } * 60f
    out[0] = h; out[1] = s.coerceIn(0f, 1f); out[2] = l
}

// SA-WHIPLASH-2026: proprietary, see LICENSE section 2.
internal fun hslToRgb(hsl: FloatArray): Int {
    val h = hsl[0]; val s = hsl[1]; val l = hsl[2]
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f).mod(2f) - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return packRgb(
        ((r1 + m) * 255f + 0.5f).toInt(),
        ((g1 + m) * 255f + 0.5f).toInt(),
        ((b1 + m) * 255f + 0.5f).toInt(),
    )
}
