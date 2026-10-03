// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.recommend

import org.json.JSONObject
import java.util.Random
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** Where a radio song came from. */
enum class RadioSource {
    /** YouTube Music's radio for the song the listener picked. */
    SEED,
    /** The radio of an autoplay song the listener played through. */
    KEPT,
    /** The radio of a song this radio served that wasn't skipped (not finished yet). */
    SAMPLED,
    /** YouTube Music's "You might also like" for the seed or a kept song. */
    RELATED,
}

/**
 * Picks where to continue once the seed's own radio is used up, by
 * Thompson sampling: each source has a Beta(kept, skipped) belief, one
 * draw per source, the highest draw wins. Sources that work for this
 * listener get picked more, but every source keeps a chance. Old evidence
 * slowly fades so it adapts when taste changes.
 */
class SourceBandit(private val random: Random = Random()) {
    private val arms = HashMap<RadioSource, DoubleArray>().apply {
        put(RadioSource.SEED, doubleArrayOf(2.0, 1.0))
        put(RadioSource.KEPT, doubleArrayOf(2.0, 1.0))
        put(RadioSource.SAMPLED, doubleArrayOf(1.5, 1.0))
        put(RadioSource.RELATED, doubleArrayOf(1.0, 1.2))
    }

    fun choose(options: Set<RadioSource>): RadioSource? =
        options.maxByOrNull { val (a, b) = arms.getValue(it); beta(a, b) }

    fun reward(source: RadioSource, kept: Boolean) {
        val arm = arms.getValue(source)
        // Fade old evidence toward the prior, then count this play.
        arm[0] = 1 + (arm[0] - 1) * DECAY
        arm[1] = 1 + (arm[1] - 1) * DECAY
        if (kept) arm[0] += 1 else arm[1] += 1
    }

    /** Mean kept rate the bandit believes for [source]. */
    fun mean(source: RadioSource): Double = arms.getValue(source).let { it[0] / (it[0] + it[1]) }

    fun toJson(): JSONObject = JSONObject().apply {
        arms.forEach { (s, v) -> put(s.name, org.json.JSONArray().put(v[0]).put(v[1])) }
    }

    fun load(json: JSONObject?) {
        json ?: return
        for (s in RadioSource.entries) {
            val a = json.optJSONArray(s.name) ?: continue
            val alpha = a.optDouble(0, Double.NaN)
            val beta = a.optDouble(1, Double.NaN)
            if (alpha.isFinite() && beta.isFinite() && alpha >= 1 && beta >= 1) arms[s] = doubleArrayOf(alpha, beta)
        }
    }

    private fun beta(a: Double, b: Double): Double {
        val x = gamma(a)
        val y = gamma(b)
        return x / (x + y)
    }

    /** Marsaglia–Tsang gamma sampler (shape < 1 boosted). */
    private fun gamma(shape: Double): Double {
        if (shape < 1) return gamma(shape + 1) * random.nextDouble().pow(1 / shape)
        val d = shape - 1.0 / 3
        val c = 1 / sqrt(9 * d)
        while (true) {
            var x: Double
            var v: Double
            do {
                x = random.nextGaussian()
                v = 1 + c * x
            } while (v <= 0)
            v = v * v * v
            val u = random.nextDouble()
            if (u < 1 - 0.0331 * x * x * x * x) return d * v
            if (ln(u) < 0.5 * x * x + d * (1 - v + ln(v))) return d * v
        }
    }

    private companion object {
        const val DECAY = 0.99
    }
}
