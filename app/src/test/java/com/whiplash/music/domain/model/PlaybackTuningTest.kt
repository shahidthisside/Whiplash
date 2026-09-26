package com.whiplash.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTuningTest {

    @Test fun crossfadeClampsAndSnapsToWholeSeconds() {
        assertEquals(0, PlaybackTuning.normalizeCrossfadeMs(-500))
        assertEquals(12_000, PlaybackTuning.normalizeCrossfadeMs(99_000))
        assertEquals(4_000, PlaybackTuning.normalizeCrossfadeMs(3_600))
        assertEquals(3_000, PlaybackTuning.normalizeCrossfadeMs(3_400))
    }

    @Test fun previouslyStoredCrossfadeValuesAreUnchanged() {
        listOf(0, 3_000, 6_000, 10_000).forEach { assertEquals(it, PlaybackTuning.normalizeCrossfadeMs(it)) }
    }

    @Test fun speedClampsToRange() {
        assertEquals(0.5f, PlaybackTuning.normalizeSpeed(0.1f), 0f)
        assertEquals(2.0f, PlaybackTuning.normalizeSpeed(5f), 0f)
        assertEquals(1.0f, PlaybackTuning.normalizeSpeed(Float.NaN), 0f)
    }

    @Test fun speedSnapsSliderDriftToExactStep() {
        assertEquals(0.75f, PlaybackTuning.normalizeSpeed(0.7500001f), 0f)
        assertEquals(1.05f, PlaybackTuning.normalizeSpeed(1.049f), 0f)
    }

    @Test fun previouslyStoredSpeedsAndPresetsAreUnchanged() {
        (listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f) + PlaybackTuning.SPEED_PRESETS).forEach {
            assertEquals(it, PlaybackTuning.normalizeSpeed(it), 0f)
        }
    }

    @Test fun speedFormatting() {
        assertEquals("1x", PlaybackTuning.formatSpeed(1.0f))
        assertEquals("0.5x", PlaybackTuning.formatSpeed(0.5f))
        assertEquals("1.25x", PlaybackTuning.formatSpeed(1.25f))
        assertEquals("1.05x", PlaybackTuning.formatSpeed(1.05f))
        assertEquals("2x", PlaybackTuning.formatSpeed(2.0f))
    }
}
