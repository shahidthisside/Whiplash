// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

/**
 * The audio route shown in the full player's overflow menu.
 *
 * Android reports every attached output at once (the built-in speaker is
 * always listed, even with headphones plugged in), so the one media is
 * actually playing through is picked by priority: Bluetooth, then USB,
 * wired, HDMI, and finally the phone speaker.
 */
data class AudioOutput(val kind: Kind, val name: String) {
    enum class Kind { BLUETOOTH, WIRED, USB, HDMI, SPEAKER }
}

/** A device as the platform reports it; plain values so this stays testable without Android. */
data class OutputCandidate(val kind: AudioOutput.Kind, val productName: String?)

/**
 * Picks the likely active output and gives it a readable name. External
 * devices keep their product name when it's real; the speaker's "product
 * name" is only the handset model, so it is always "This phone".
 */
fun pickActiveOutput(candidates: List<OutputCandidate>): AudioOutput {
    val order = listOf(AudioOutput.Kind.BLUETOOTH, AudioOutput.Kind.USB, AudioOutput.Kind.WIRED, AudioOutput.Kind.HDMI)
    for (kind in order) {
        val match = candidates.firstOrNull { it.kind == kind } ?: continue
        val name = match.productName?.trim()?.takeIf { it.isNotEmpty() } ?: defaultName(kind)
        return AudioOutput(kind, name)
    }
    return AudioOutput(AudioOutput.Kind.SPEAKER, defaultName(AudioOutput.Kind.SPEAKER))
}

private fun defaultName(kind: AudioOutput.Kind): String = when (kind) {
    AudioOutput.Kind.BLUETOOTH -> "Bluetooth"
    AudioOutput.Kind.USB -> "USB audio"
    AudioOutput.Kind.WIRED -> "Headphones"
    AudioOutput.Kind.HDMI -> "HDMI"
    AudioOutput.Kind.SPEAKER -> "This phone"
}
