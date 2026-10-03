// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.recommend

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp

/** The signals each radio candidate is scored on, in [Ranker] order. */
object Features {
    const val RANK = 0          // position in YouTube Music's list, 1 = top
    const val VIBE = 1          // fit to the session's mood/genre/energy
    const val AFFINITY = 2      // how often the listener finishes this artist
    const val NOVELTY = 3       // artist not heard in the last 20 songs
    const val OFF_LANGUAGE = 4  // language mismatch with the session
    const val CO_LISTEN = 5     // artist heard alongside the session's artists
    const val TIME = 6          // energy fits what's usually played at this hour (−0.5…0.5)
    const val AUDIO = 7         // official audio upload
    const val COUNT = 8

    /** The hand-set weights; also the ranker's starting point. */
    val HAND = doubleArrayOf(1.0, 1.4, 0.6, 0.25, -1.2, 0.8, 0.3, 0.2)
}

/**
 * A tiny logistic model of "will the listener play this through", trained
 * on-device, one example per autoplay song kept or skipped. It starts at
 * the hand-set weights and moves only as evidence builds up; the radio
 * leans on it more as it sees more examples ([trust]).
 */
class Ranker {
    private val w = Features.HAND.copyOf()
    private var bias = INITIAL_BIAS
    var examples = 0
        private set

    fun predict(x: DoubleArray): Double = sigmoid(bias + dot(x))

    fun update(x: DoubleArray, kept: Boolean) {
        val y = if (kept) 1.0 else 0.0
        val err = y - predict(x)
        val lr = BASE_RATE / (1 + examples / 2_000.0)
        for (i in w.indices) w[i] += lr * (err * x[i] - L2 * (w[i] - Features.HAND[i]))
        bias += lr * err
        examples++
    }

    /** 0 … [MAX_TRUST]: how much to believe the model over the hand-set score. */
    fun trust(): Double = MAX_TRUST * minOf(1.0, examples / FULL_TRUST_EXAMPLES)

    fun weights(): DoubleArray = w.copyOf()

    fun toJson(): JSONObject = JSONObject()
        .put("w", JSONArray().apply { w.forEach { put(it) } })
        .put("b", bias)
        .put("n", examples)

    fun load(json: JSONObject?) {
        json ?: return
        val a = json.optJSONArray("w") ?: return
        if (a.length() != w.size) return // feature set changed: start over
        val loaded = DoubleArray(w.size) { a.optDouble(it, Double.NaN) }
        val b = json.optDouble("b", Double.NaN)
        if (loaded.any { !it.isFinite() } || !b.isFinite()) return
        loaded.copyInto(w)
        bias = b
        examples = json.optInt("n", 0).coerceAtLeast(0)
    }

    private fun dot(x: DoubleArray): Double { var s = 0.0; for (i in w.indices) s += w[i] * x[i]; return s }

    private fun sigmoid(z: Double) = 1 / (1 + exp(-z.coerceIn(-30.0, 30.0)))

    private companion object {
        const val INITIAL_BIAS = -1.6
        const val BASE_RATE = 0.05
        const val L2 = 0.002
        const val MAX_TRUST = 0.6
        const val FULL_TRUST_EXAMPLES = 500.0
    }
}
