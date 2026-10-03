// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.lyrics

import com.whiplash.music.domain.model.LyricLine
import com.whiplash.music.domain.model.LyricWord
import com.whiplash.music.domain.model.LyricsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LyricsCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_000_000L

    private fun cache(dir: File = tmp.root, maxBytes: Long = 1_000_000L, mem: Int = 50) =
        LyricsCache(dir, maxDiskBytes = maxBytes, maxMemoryEntries = mem, foundTtlMs = 1_000, unavailableTtlMs = 100, clock = { now })

    private val synced = LyricsResult.Synced(
        listOf(
            LyricLine(1_000, "plain line"),
            LyricLine(
                5_250, "Hello world",
                listOf(LyricWord(5_250, 5_600, "Hello "), LyricWord(5_700, 6_000, "world")),
            ),
        ),
    )

    @Test
    fun `survives a restart via disk`() {
        cache().put("yt:1", LyricsLookup(synced, "LRCLIB"))
        cache().put("yt:2", LyricsLookup(LyricsResult.Plain("some\nplain\ntext"), "LRCLIB"))
        val fresh = cache()
        assertEquals(synced, fresh.get("yt:1")?.result)
        assertEquals(LyricsResult.Plain("some\nplain\ntext"), fresh.get("yt:2")?.result)
        assertTrue(tmp.root.listFiles()!!.all { it.name.endsWith(".gz") })
    }

    @Test
    fun `provider id round trips`() {
        cache().put("p", LyricsLookup(LyricsResult.Plain("x"), "LYRICS_OVH"))
        assertEquals("LYRICS_OVH", cache().get("p")?.providerId)
    }

    @Test
    fun `errors are never cached`() {
        val c = cache()
        c.put("k", LyricsLookup(LyricsResult.Error("offline"), "LRCLIB"))
        assertNull(c.get("k")?.result)
        assertEquals(0, tmp.root.listFiles()?.size ?: 0)
    }

    @Test
    fun `unavailable expires sooner than found lyrics`() {
        val c = cache()
        c.put("none", LyricsLookup(LyricsResult.Unavailable, "LRCLIB"))
        c.put("found", LyricsLookup(synced, "LRCLIB"))
        now += 500
        assertNull(c.get("none")?.result)
        assertEquals(synced, cache().get("found")?.result)
        now += 600
        assertNull(cache().get("found")?.result)
    }

    @Test
    fun `disk is trimmed to its size cap, oldest first`() {
        val big = LyricsResult.Plain((1..4000).joinToString(" ") { "word$it" })
        val probe = cache()
        probe.put("probe", LyricsLookup(big, "LRCLIB"))
        val oneFile = probe.diskSizeBytes()
        probe.clear()

        val c = cache(maxBytes = oneFile * 2 + oneFile / 2)
        c.put("a", LyricsLookup(big, "LRCLIB")); now += 10
        c.put("b", LyricsLookup(big, "LRCLIB")); now += 10
        c.put("c", LyricsLookup(big, "LRCLIB"))
        assertTrue(c.diskSizeBytes() <= oneFile * 2 + oneFile / 2)
        val restarted = cache(maxBytes = oneFile * 3)
        assertNull(restarted.get("a")?.result)
        assertEquals(big, restarted.get("c")?.result)
    }

    @Test
    fun `clear removes memory and disk`() {
        val c = cache()
        c.put("k", LyricsLookup(synced, "LRCLIB"))
        c.clear()
        assertNull(c.get("k")?.result)
        assertEquals(0L, c.diskSizeBytes())
    }

    @Test
    fun `corrupt file is ignored and deleted`() {
        val c = cache()
        c.put("k", LyricsLookup(synced, "LRCLIB"))
        tmp.root.listFiles()!!.single().writeText("not gzip")
        assertNull(cache().get("k")?.result)
        assertEquals(0, tmp.root.listFiles()!!.size)
    }

    @Test
    fun `lrc round trip keeps word gaps`() {
        val lrc = LyricsCache.toLrc(synced.lines)
        assertTrue(lrc, lrc.contains("<00:05.600><00:05.700>world<00:06.000>"))
    }
}
