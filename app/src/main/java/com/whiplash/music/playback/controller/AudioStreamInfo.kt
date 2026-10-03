// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.playback.controller

import kotlin.math.roundToInt

/**
 * Technical details of the audio actually being played, for the full
 * player's "Stats for nerds" line. Plain Kotlin so the UI never touches
 * Media3 types; every field is nullable because what a container reports
 * varies — a YouTube WebM/Opus stream carries no bitrate in its track
 * format, and only uncompressed/lossless PCM has a meaningful bit depth.
 */
data class AudioStreamInfo(
    /** Media3 sample MIME type, e.g. "audio/opus", "audio/mp4a-latm", "audio/flac". */
    val mimeType: String?,
    val sampleRateHz: Int?,
    val channelCount: Int?,
    /** Bits per sample, only for PCM-decoded formats (WAV, FLAC, ALAC). */
    val bitDepth: Int?,
    val bitrateBps: Int?,
    val origin: Origin,
) {
    enum class Origin { STREAM, CACHED, DOWNLOAD, LOCAL }

    /** e.g. "OPUS · 48 kHz · Stereo · 138 kbps · Stream". Unknown parts are left out, never guessed. */
    fun summary(): String = listOfNotNull(
        codecLabel(mimeType),
        sampleRateHz?.takeIf { it > 0 }?.let(::formatSampleRate),
        bitDepth?.takeIf { it > 0 }?.let { "$it-bit" },
        channelCount?.takeIf { it > 0 }?.let(::formatChannels),
        bitrateBps?.takeIf { it > 0 }?.let { "${(it / 1000f).roundToInt()} kbps" },
        when (origin) {
            Origin.STREAM -> "Stream"
            Origin.CACHED -> "Cached"
            Origin.DOWNLOAD -> "Offline"
            Origin.LOCAL -> "Local file"
        },
    ).joinToString(" · ")

    companion object {
        fun codecLabel(mimeType: String?): String? {
            val subtype = mimeType?.substringAfter('/', "")?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
            return when (subtype) {
                "mp4a-latm", "aac" -> "AAC"
                "mpeg", "mpeg-l3" -> "MP3"
                "opus" -> "OPUS"
                "vorbis" -> "VORBIS"
                "flac" -> "FLAC"
                "alac" -> "ALAC"
                "raw", "wav" -> "PCM"
                "ac3" -> "AC-3"
                "eac3", "eac3-joc" -> "E-AC-3"
                else -> subtype.uppercase()
            }
        }

        /** 44100 -> "44.1 kHz", 48000 -> "48 kHz". */
        fun formatSampleRate(hz: Int): String {
            val tenths = (hz / 100f).roundToInt()
            return if (tenths % 10 == 0) "${tenths / 10} kHz" else "${tenths / 10}.${tenths % 10} kHz"
        }

        fun formatChannels(count: Int): String = when (count) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "$count ch"
        }
    }
}
