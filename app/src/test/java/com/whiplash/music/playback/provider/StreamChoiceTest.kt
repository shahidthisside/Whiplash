// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.playback.provider

import com.whiplash.music.data.local.entity.ProviderStatus
import com.whiplash.music.domain.model.AudioQuality
import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamChoiceTest {

    private class MemoryPins : StreamChoiceStore.Persistence {
        val map = mutableMapOf<String, StreamChoiceStore.Pin>()
        override fun read(videoId: String) = map[videoId]
        override fun write(videoId: String, pin: StreamChoiceStore.Pin) { map[videoId] = pin }
        override fun count() = map.size
        override fun clear() = map.clear()
    }

    /** Offers [offered] itags; picks the highest unless a preferred one is offered. */
    private class FakeProvider(var offered: List<Int>, var expiresInMs: Long = 6 * 3_600_000L) : PlaybackProvider {
        var calls = 0
        val preferredSeen = mutableListOf<Int?>()
        override val id = "fake"
        override val displayName = "Fake"
        override fun supports(item: PlayableItem) = true
        override suspend fun getStream(songId: String, quality: AudioQuality, preferredItag: Int?): ResolvedStream {
            calls++
            preferredSeen += preferredItag
            val itag = preferredItag?.takeIf { it in offered } ?: offered.max()
            return ResolvedStream(
                streamUrl = "https://x.googlevideo.com/videoplayback?itag=$itag&n=$calls",
                mimeType = null, bitrateBps = null,
                expiresAtEpochMs = System.currentTimeMillis() + expiresInMs,
                providerId = id, resolvedArtworkUrl = null, itag = itag,
            )
        }
        override suspend fun getPlayerInfo(songId: String) = throw UnsupportedOperationException()
        override suspend fun providerStatus() = ProviderStatus.HEALTHY
    }

    private fun track(id: String = "abc", durationMs: Long = 200_000L) =
        PlayableItem.YoutubeTrack(id, "t", "a", null, null, durationMs)

    @Test
    fun parsesGooglevideoExpiry() {
        assertEquals(1_700_000_000_000L, StreamChoiceStore.parseExpiryMs("https://r.googlevideo.com/videoplayback?a=1&expire=1700000000&b=2"))
        assertNull(StreamChoiceStore.parseExpiryMs("https://example.com/audio.webm"))
    }

    @Test
    fun reopenReusesStillValidUrlWithoutResolving() = runTest {
        val provider = FakeProvider(listOf(140, 251))
        val manager = PlaybackManager(listOf(provider), StreamChoiceStore(MemoryPins()))
        val first = (manager.resolveStream(track(), AudioQuality.HIGH) as FallbackResult.Success).value
        val again = (manager.resolveStream(track(), AudioQuality.HIGH) as FallbackResult.Success).value
        assertEquals(1, provider.calls)
        assertEquals(first.streamUrl, again.streamUrl)
    }

    @Test
    fun urlCloseToExpiryIsNotReused() = runTest {
        val provider = FakeProvider(listOf(251), expiresInMs = 5 * 60_000L) // < song + 10 min margin
        val manager = PlaybackManager(listOf(provider), StreamChoiceStore(MemoryPins()))
        manager.resolveStream(track(), AudioQuality.HIGH)
        manager.resolveStream(track(), AudioQuality.HIGH)
        assertEquals(2, provider.calls)
    }

    @Test
    fun sameFormatIsKeptAcrossResolvesEvenIfOfferChanges() = runTest {
        val provider = FakeProvider(listOf(140, 251))
        val store = StreamChoiceStore(MemoryPins())
        val manager = PlaybackManager(listOf(provider), store)
        manager.resolveStream(track(), AudioQuality.HIGH)          // pins 251
        manager.invalidateStream("abc")                              // e.g. player 403
        provider.offered = listOf(140, 251, 774)                     // a "better" one shows up
        val s = (manager.resolveStream(track(), AudioQuality.HIGH) as FallbackResult.Success).value
        assertEquals(251, s.itag)
        assertEquals(251, provider.preferredSeen.last())
    }

    @Test
    fun formatChangeDropsCachedAudio() = runTest {
        val provider = FakeProvider(listOf(140, 251))
        val dropped = mutableListOf<String>()
        val manager = PlaybackManager(listOf(provider), StreamChoiceStore(MemoryPins())) { id, known -> if (known) dropped += id }
        manager.resolveStream(track(), AudioQuality.HIGH)            // pins 251
        provider.offered = listOf(140)                               // 251 no longer offered
        manager.resolveStream(track(), AudioQuality.LOW)             // new quality, no reuse
        assertEquals(listOf("abc"), dropped)
    }

    @Test
    fun keepAnyPinnedFormatIgnoresQualityChange() = runTest {
        val provider = FakeProvider(listOf(140, 251))
        val manager = PlaybackManager(listOf(provider), StreamChoiceStore(MemoryPins()))
        manager.resolveStream(track(), AudioQuality.HIGH)
        manager.resolveStream(track(), AudioQuality.LOW, keepAnyPinnedFormat = true)
        assertEquals(251, provider.preferredSeen.last())
        // Without the flag, a different quality setting doesn't force the old pin.
        manager.resolveStream(track(), AudioQuality.MEDIUM)
        assertNull(provider.preferredSeen.last())
    }

    @Test
    fun downloadsNeitherReuseNorChangeTheStreamingChoice() = runTest {
        val provider = FakeProvider(listOf(140, 251))
        val store = StreamChoiceStore(MemoryPins())
        val dropped = mutableListOf<String>()
        val manager = PlaybackManager(listOf(provider), store) { id, _ -> dropped += id }
        manager.resolveStream(track(), AudioQuality.HIGH)                           // stream pins 251
        dropped.clear()
        provider.offered = listOf(140)
        manager.resolveStream(track(), AudioQuality.LOW, useStreamChoices = false) // a download
        assertEquals(2, provider.calls)                                             // no reuse
        assertNull(provider.preferredSeen.last())                                   // no pin forced
        assertEquals(StreamChoiceStore.Pin(251, AudioQuality.HIGH), store.pin("abc")) // pin untouched
        assertTrue(dropped.isEmpty())                                               // cache untouched
    }

    @Test
    fun firstResolveWithoutPinReportsUnknownChange() = runTest {
        val provider = FakeProvider(listOf(251))
        val calls = mutableListOf<Boolean>()
        val manager = PlaybackManager(listOf(provider), StreamChoiceStore(MemoryPins())) { _, known -> calls += known }
        manager.resolveStream(track(), AudioQuality.HIGH)
        assertEquals(listOf(false), calls)
    }

    @Test
    fun withoutStoreEveryResolveHitsProvider() = runTest {
        val provider = FakeProvider(listOf(251))
        val manager = PlaybackManager(listOf(provider))
        manager.resolveStream(track(), AudioQuality.HIGH)
        manager.resolveStream(track(), AudioQuality.HIGH)
        assertEquals(2, provider.calls)
        assertTrue(provider.preferredSeen.all { it == null })
    }
}
