// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.playback.provider

import com.whiplash.music.data.local.entity.ProviderStatus
import com.whiplash.music.domain.model.AudioQuality
import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSourceFallbackTest {

    private class Source(override val id: String, var fail: ProviderFailure? = null) : PlaybackProvider {
        var calls = 0
        override val displayName = id
        override fun supports(item: PlayableItem) = true
        override suspend fun getStream(songId: String, quality: AudioQuality, preferredItag: Int?): ResolvedStream {
            calls++
            fail?.let { throw it }
            return ResolvedStream("https://example.invalid/$id", "audio/webm", 160_000, null, id, null, itag = 251)
        }
        override suspend fun getPlayerInfo(songId: String) = throw UnsupportedOperationException()
        override suspend fun providerStatus() = ProviderStatus.HEALTHY
    }

    private val track = PlayableItem.YoutubeTrack(id = "abcdefghijk", title = "t", artist = "a", album = null, artworkUri = null, durationMs = 200_000L)

    private fun manager(pref: StreamSourcePreference, newPipe: Source, direct: Source) =
        PlaybackManager(listOf(newPipe, direct), chooseProviders = { pref.order(it) })

    @Test
    fun automaticFallsBackToDirectWhenNewPipeBreaks() = runTest {
        val newPipe = Source("newpipe", ProviderFailure.ProviderParserFailure("YouTube changed"))
        val direct = Source("youtube_direct")
        val m = manager(StreamSourcePreference.AUTO, newPipe, direct)
        val result = m.resolveStream(track)
        assertTrue(result is FallbackResult.Success)
        assertEquals("youtube_direct", (result as FallbackResult.Success).value.providerId)
        assertEquals("youtube_direct", m.lastStreamSource.value)
    }

    @Test
    fun automaticUsesNewPipeWhenItWorks() = runTest {
        val newPipe = Source("newpipe")
        val direct = Source("youtube_direct")
        val m = manager(StreamSourcePreference.AUTO, newPipe, direct)
        assertEquals("newpipe", (m.resolveStream(track) as FallbackResult.Success).value.providerId)
        assertEquals(0, direct.calls)
    }

    @Test
    fun aPickedSourceIsNotSecondGuessed() = runTest {
        val newPipe = Source("newpipe")
        val direct = Source("youtube_direct", ProviderFailure.UnknownPlaybackFailure("down"))
        val m = manager(StreamSourcePreference.YOUTUBE_DIRECT, newPipe, direct)
        assertTrue(m.resolveStream(track) is FallbackResult.Failure)
        assertEquals(0, newPipe.calls)
    }
}
