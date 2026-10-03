// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.recommend

import java.util.Calendar
import java.util.TimeZone

/** Parts of the day listening habits differ by. */
enum class DayPart { NIGHT, MORNING, AFTERNOON, EVENING;
    companion object {
        fun of(epochMs: Long, zone: TimeZone = TimeZone.getDefault()): DayPart {
            val hour = Calendar.getInstance(zone).apply { timeInMillis = epochMs }.get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 0..4 -> NIGHT
                in 5..11 -> MORNING
                in 12..17 -> AFTERNOON
                else -> EVENING
            }
        }
    }
}

/**
 * The energy the listener usually plays through at each part of the day
 * (calm at night, upbeat in the morning, or whatever they actually do),
 * learned from finished songs.
 */
class EnergyByTime private constructor(private val preferred: Map<DayPart, Double>) {

    /** 0…1 fit of [energy] to what's usual at [now]; 0.5 when either is unknown. */
    fun fit(energy: Float?, now: Long, zone: TimeZone = TimeZone.getDefault()): Double {
        val p = preferred[DayPart.of(now, zone)] ?: return 0.5
        energy ?: return 0.5
        return (1 - kotlin.math.abs(energy - p) * 1.5).coerceIn(0.0, 1.0)
    }

    fun preferred(part: DayPart): Double? = preferred[part]

    companion object {
        private const val MIN_SAMPLES = 6
        val EMPTY = EnergyByTime(emptyMap())

        fun from(plays: List<PastPlay>, zone: TimeZone = TimeZone.getDefault()): EnergyByTime {
            val byPart = HashMap<DayPart, MutableList<Float>>()
            for (p in plays) {
                if (!p.completed) continue
                val e = VibeTagger.tag(p.title, p.artist).energy ?: continue
                byPart.getOrPut(DayPart.of(p.startedAt, zone)) { mutableListOf() } += e
            }
            return EnergyByTime(byPart.filterValues { it.size >= MIN_SAMPLES }.mapValues { (_, v) -> v.average() })
        }
    }
}
