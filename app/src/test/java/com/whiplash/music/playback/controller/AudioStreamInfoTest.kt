// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.playback.controller

import com.whiplash.music.playback.controller.AudioStreamInfo.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioStreamInfoTest {

    @Test fun fullYoutubeStream() {
        val info = AudioStreamInfo("audio/opus", 48_000, 2, null, 138_400, Origin.STREAM)
        assertEquals("OPUS · 48 kHz · Stereo · 138 kbps · Stream", info.summary())
    }

    @Test fun losslessLocalFileShowsBitDepth() {
        val info = AudioStreamInfo("audio/flac", 44_100, 2, 24, null, Origin.LOCAL)
        assertEquals("FLAC · 44.1 kHz · 24-bit · Stereo · Local file", info.summary())
    }

    @Test fun unknownFieldsAreOmittedNotGuessed() {
        val info = AudioStreamInfo(null, null, null, null, -1, Origin.CACHED)
        assertEquals("Cached", info.summary())
    }

    @Test fun codecLabels() {
        assertEquals("AAC", AudioStreamInfo.codecLabel("audio/mp4a-latm"))
        assertEquals("MP3", AudioStreamInfo.codecLabel("audio/mpeg"))
        assertEquals("XYZ", AudioStreamInfo.codecLabel("audio/xyz"))
        assertNull(AudioStreamInfo.codecLabel(null))
        assertNull(AudioStreamInfo.codecLabel("audio"))
    }

    @Test fun sampleRatesAndChannels() {
        assertEquals("96 kHz", AudioStreamInfo.formatSampleRate(96_000))
        assertEquals("22.1 kHz", AudioStreamInfo.formatSampleRate(22_050))
        assertEquals("Mono", AudioStreamInfo.formatChannels(1))
        assertEquals("5.1", AudioStreamInfo.formatChannels(6))
        assertEquals("3 ch", AudioStreamInfo.formatChannels(3))
    }
}
