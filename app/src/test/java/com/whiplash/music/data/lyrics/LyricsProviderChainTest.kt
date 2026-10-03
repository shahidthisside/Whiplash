// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.lyrics

import com.whiplash.music.domain.model.LyricLine
import com.whiplash.music.domain.model.LyricsResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsProviderChainTest {

    private class Fake(
        override val id: String,
        override val supportsSync: Boolean,
        var answer: LyricsResult,
    ) : LyricsProvider {
        override val displayName = id
        var calls = 0
        override suspend fun getLyrics(title: String, artist: String, durationMs: Long): LyricsResult {
            calls++
            return answer
        }
    }

    private val synced = LyricsResult.Synced(listOf(LyricLine(0, "a")))
    private val plain = LyricsResult.Plain("text")
    private var now = 0L

    private fun chain(vararg p: Fake) = LyricsProviderChain(p.toList(), clock = { now })

    @Test
    fun `auto falls through to the next provider`() = runTest {
        val a = Fake("LRCLIB", true, LyricsResult.Unavailable)
        val b = Fake("LYRICS_OVH", false, plain)
        val r = chain(a, b).lookup(LyricsSourcePreference.AUTO, "t", "a", 0)
        assertEquals(LyricsLookup(plain, "LYRICS_OVH"), r)
    }

    @Test
    fun `synced first result stops the chain`() = runTest {
        val a = Fake("LRCLIB", true, synced)
        val b = Fake("LYRICS_OVH", false, plain)
        assertEquals("LRCLIB", chain(a, b).lookup(LyricsSourcePreference.AUTO, "t", "a", 0).providerId)
        assertEquals(0, b.calls)
    }

    @Test
    fun `plain result skips later providers that cannot sync`() = runTest {
        val a = Fake("LRCLIB", true, plain)
        val b = Fake("LYRICS_OVH", false, LyricsResult.Plain("other"))
        assertEquals(LyricsLookup(plain, "LRCLIB"), chain(a, b).lookup(LyricsSourcePreference.AUTO, "t", "a", 0))
        assertEquals(0, b.calls)
    }

    @Test
    fun `error wins over unavailable so it is retried`() = runTest {
        val a = Fake("LRCLIB", true, LyricsResult.Error("offline"))
        val b = Fake("LYRICS_OVH", false, LyricsResult.Unavailable)
        val r = chain(a, b).lookup(LyricsSourcePreference.AUTO, "t", "a", 0)
        assertEquals(LyricsResult.Error("offline"), r.result)
        assertNull(r.providerId)
    }

    @Test
    fun `explicit choice asks only that provider`() = runTest {
        val a = Fake("LRCLIB", true, synced)
        val b = Fake("LYRICS_OVH", false, LyricsResult.Unavailable)
        val r = chain(a, b).lookup(LyricsSourcePreference.LYRICS_OVH, "t", "a", 0)
        assertEquals(LyricsResult.Unavailable, r.result)
        assertEquals(0, a.calls)
    }

    @Test
    fun `failing provider cools down then recovers`() = runTest {
        val a = Fake("LRCLIB", true, LyricsResult.Error("down"))
        val b = Fake("LYRICS_OVH", false, plain)
        val c = chain(a, b)
        repeat(LyricsProviderChain.FAILURE_THRESHOLD) { c.lookup(LyricsSourcePreference.AUTO, "t", "a", 0) }
        assertEquals(ProviderHealth.State.FAILING, c.health.value["LRCLIB"]?.state)
        val before = a.calls
        c.lookup(LyricsSourcePreference.AUTO, "t", "a", 0)
        assertEquals(before, a.calls) // skipped while cooling down

        now += LyricsProviderChain.COOLDOWN_MS + 1
        a.answer = synced
        assertEquals("LRCLIB", c.lookup(LyricsSourcePreference.AUTO, "t", "a", 0).providerId)
        assertEquals(ProviderHealth.State.WORKING, c.health.value["LRCLIB"]?.state)
    }

    @Test
    fun `thrown exception becomes an error`() = runTest {
        val boom = object : LyricsProvider {
            override val id = "LRCLIB"
            override val displayName = "x"
            override val supportsSync = true
            override suspend fun getLyrics(title: String, artist: String, durationMs: Long): LyricsResult =
                throw IllegalStateException("bad")
        }
        val r = LyricsProviderChain(listOf(boom)).lookup(LyricsSourcePreference.AUTO, "t", "a", 0)
        assertEquals(LyricsResult.Error("bad"), r.result)
    }

    @Test
    fun `lyrics ovh cleaning`() {
        assertEquals("Blinding Lights", LyricsOvhProvider.cleanTitle("Blinding Lights (Official Video)"))
        assertEquals("Song", LyricsOvhProvider.cleanTitle("Song feat. Someone"))
        assertEquals("Song", LyricsOvhProvider.cleanTitle("Song - Official Audio"))
        assertEquals("The Weeknd", LyricsOvhProvider.primaryArtist("The Weeknd, Daft Punk"))
        assertEquals("Adele", LyricsOvhProvider.primaryArtist("Adele - Topic"))
        assertEquals("line1\nline2\n\nline3", LyricsOvhProvider.normalizeLyrics("Paroles de la chanson X par Y\r\nline1\r\nline2\n\n\n\nline3\n"))
    }
}
