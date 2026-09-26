package com.whiplash.music.domain.model

import com.whiplash.music.domain.model.AudioOutput.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioOutputTest {

    @Test fun speakerOnlyIsThisPhone() {
        assertEquals(AudioOutput(Kind.SPEAKER, "This phone"), pickActiveOutput(listOf(OutputCandidate(Kind.SPEAKER, "Pixel 5"))))
    }

    @Test fun emptyListFallsBackToSpeaker() {
        assertEquals(Kind.SPEAKER, pickActiveOutput(emptyList()).kind)
    }

    @Test fun bluetoothBeatsWiredAndSpeaker() {
        val out = pickActiveOutput(
            listOf(
                OutputCandidate(Kind.SPEAKER, "Pixel 5"),
                OutputCandidate(Kind.WIRED, null),
                OutputCandidate(Kind.BLUETOOTH, "WH-1000XM4"),
            ),
        )
        assertEquals(AudioOutput(Kind.BLUETOOTH, "WH-1000XM4"), out)
    }

    @Test fun blankProductNameUsesGenericLabel() {
        assertEquals(AudioOutput(Kind.WIRED, "Headphones"), pickActiveOutput(listOf(OutputCandidate(Kind.WIRED, "  "))))
        assertEquals(AudioOutput(Kind.USB, "USB audio"), pickActiveOutput(listOf(OutputCandidate(Kind.USB, null))))
    }
}
