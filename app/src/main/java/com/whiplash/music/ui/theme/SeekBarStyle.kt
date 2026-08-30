package com.whiplash.music.ui.theme

/**
 * Selectable full-player seek bar visual styles (section: Appearance
 * customization — "let the user pick the progress bar style they like").
 *
 * Every style shares the exact same underlying gesture/seek logic (see
 * [com.whiplash.music.ui.player.SeekBar]'s doc for why that logic is a
 * custom, from-scratch tap/drag surface rather than Material3's `Slider`
 * — a real, reported bug was traced to Slider's own internal drag-state
 * machine, not this app's logic). Only the *drawing* differs per style;
 * there is exactly one seek/drag code path shared by all four, so picking
 * a different style can never reintroduce a seeking bug on its own.
 *
 * - [CLASSIC]: thin rounded track + circular thumb (the original style).
 * - [WAVY]: an animated sine-wave squiggle on the played portion, flat on
 *   the remaining portion — modeled directly on Android 13+'s own system
 *   media player seek bar, a real, shipped Google design.
 * - [WAVEFORM]: vertical bar/equalizer-style segments, like SoundCloud's
 *   or Waveform players' seek bars.
 * - [MINIMAL]: an ultra-thin 2dp line with no visible thumb until touched
 *   — an intentionally understated, Apple Music-esque style.
 */
enum class SeekBarStyle(val displayName: String) {
    CLASSIC("Classic"),
    WAVY("Wavy"),
    WAVEFORM("Waveform"),
    MINIMAL("Minimal"),
}
