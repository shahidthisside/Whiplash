package com.whiplash.music.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private fun playlist(name: String, created: Long, updated: Long, vararg ids: String) =
        SyncPlaylist(name = name, createdAtEpochMs = created, updatedAtEpochMs = updated, tracks = ids.map { SyncTrack(it, 1L) })

    private val song = SyncSong("t", "a")

    @Test
    fun `first sync combines both libraries and takes settings from the cloud`() {
        val local = SyncSnapshot(favorites = mapOf("a" to 1L), settings = mapOf("theme" to "DARK", "speed" to "1.0"))
        val remote = SyncSnapshot(favorites = mapOf("b" to 2L), settings = mapOf("theme" to "GLASS"))
        val merged = SyncMerge.merge(null, local, remote)
        assertEquals(setOf("a", "b"), merged.favorites.keys)
        assertEquals("GLASS", merged.settings["theme"])
        assertEquals("1.0", merged.settings["speed"])
    }

    @Test
    fun `deletion on either side syncs`() {
        val base = SyncSnapshot(favorites = mapOf("a" to 1L, "b" to 2L))
        val local = SyncSnapshot(favorites = mapOf("b" to 2L)) // removed a here
        val remote = SyncSnapshot(favorites = mapOf("a" to 1L)) // removed b elsewhere
        assertTrue(SyncMerge.merge(base, local, remote).favorites.isEmpty())
    }

    @Test
    fun `additions on both sides are kept`() {
        val base = SyncSnapshot(favorites = mapOf("a" to 1L))
        val local = SyncSnapshot(favorites = mapOf("a" to 1L, "b" to 2L))
        val remote = SyncSnapshot(favorites = mapOf("a" to 1L, "c" to 3L))
        assertEquals(setOf("a", "b", "c"), SyncMerge.merge(base, local, remote).favorites.keys)
    }

    @Test
    fun `setting changed on this phone wins over an unchanged cloud value`() {
        val base = SyncSnapshot(settings = mapOf("theme" to "DARK"))
        val local = SyncSnapshot(settings = mapOf("theme" to "GLASS"))
        assertEquals("GLASS", SyncMerge.merge(base, local, base).settings["theme"])
        assertEquals("GLASS", SyncMerge.merge(base, base, local).settings["theme"])
    }

    @Test
    fun `edited playlist beats deleting it`() {
        val p = playlist("Mix", 10, 10, "a")
        val base = SyncSnapshot(playlists = mapOf("c10" to p))
        val local = SyncSnapshot() // deleted here
        val remote = SyncSnapshot(playlists = mapOf("c10" to p.copy(tracks = p.tracks + SyncTrack("b", 2))))
        assertEquals(listOf("a", "b"), SyncMerge.merge(base, local, remote).playlists.getValue("c10").tracks.map { it.id })
    }

    @Test
    fun `playlist edited on both phones merges its tracks`() {
        val base = playlist("Mix", 10, 10, "a", "b", "c")
        val local = playlist("Mix", 10, 10, "c", "a", "b", "d") // reordered, added d
        val remote = playlist("Road trip", 10, 20, "a", "c", "e") // renamed, removed b, added e
        val merged = SyncMerge.mergePlaylist(base, local, remote)
        assertEquals(listOf("c", "a", "d", "e"), merged.tracks.map { it.id })
        assertEquals("Road trip", merged.name)
        assertEquals(20L, merged.updatedAtEpochMs)
    }

    @Test
    fun `history keeps the newest entries only`() {
        val local = SyncSnapshot(history = (1..150L).associate { SyncSnapshot.historyKey("x$it", it) to it })
        val remote = SyncSnapshot(history = (151..300L).associate { SyncSnapshot.historyKey("y$it", it) to it })
        val merged = SyncMerge.merge(null, local, remote)
        assertEquals(SyncMerge.MAX_HISTORY, merged.history.size)
        assertEquals(101L, merged.history.values.min())
    }

    @Test
    fun `songs follow references and this phone's metadata wins`() {
        val local = SyncSnapshot(songs = mapOf("a" to song.copy(title = "local"), "z" to song), favorites = mapOf("a" to 1L))
        val remote = SyncSnapshot(songs = mapOf("a" to song.copy(title = "remote"), "b" to song), pinned = mapOf("b" to 1L))
        val merged = SyncMerge.merge(null, local, remote)
        assertEquals(setOf("a", "b"), merged.songs.keys)
        assertEquals("local", merged.songs.getValue("a").title)
    }

    @Test
    fun `replay tallies changed on both sides keep the higher counts`() {
        val row = SyncReplay("t", "a", plays = 2, listenedMs = 100)
        val base = SyncSnapshot(replay = mapOf("2026-09|a" to row))
        val local = SyncSnapshot(replay = mapOf("2026-09|a" to row.copy(plays = 5, listenedMs = 400)))
        val remote = SyncSnapshot(replay = mapOf("2026-09|a" to row.copy(plays = 3, listenedMs = 900)))
        val merged = SyncMerge.merge(base, local, remote).replay.getValue("2026-09|a")
        assertEquals(5, merged.plays)
        assertEquals(900L, merged.listenedMs)
    }

    @Test
    fun `nothing changed means nothing to upload`() {
        val snap = SyncSnapshot(favorites = mapOf("a" to 1L), playlists = mapOf("c1" to playlist("P", 1, 1, "a")), songs = mapOf("a" to song))
        val merged = SyncMerge.merge(snap, snap, snap)
        assertTrue(merged.sameContentAs(snap))
        assertFalse(merged.sameContentAs(snap.copy(favorites = emptyMap())))
    }

    @Test
    fun `newest profile edit wins and a reset syncs too`() {
        val old = SyncProfile("Sam", byteArrayOf(1, 2), 10)
        val base = SyncSnapshot(profile = old)
        val renamed = SyncSnapshot(profile = SyncProfile("Samir", byteArrayOf(1, 2), 20))
        assertEquals("Samir", SyncMerge.merge(base, base, renamed).profile?.name)
        val reset = SyncSnapshot(profile = SyncProfile(null, null, 30))
        val merged = SyncMerge.merge(base, reset, renamed).profile
        assertEquals(null, merged?.name)
        assertEquals(30L, merged?.updatedAtEpochMs)
    }

    @Test
    fun `profile photos compare by content`() {
        assertEquals(SyncProfile("a", byteArrayOf(1, 2), 1), SyncProfile("a", byteArrayOf(1, 2), 1))
        assertFalse(SyncProfile("a", byteArrayOf(1, 2), 1) == SyncProfile("a", byteArrayOf(1, 3), 1))
        assertFalse(SyncProfile("a", null, 1) == SyncProfile("a", byteArrayOf(1), 1))
    }

    @Test
    fun `a switched-off category is left alone on both sides`() {
        val local = SyncSnapshot(favorites = mapOf("a" to 1L), history = mapOf("x@1" to 1L))
        val remote = SyncSnapshot(favorites = mapOf("b" to 2L), history = mapOf("y@2" to 2L))
        val merged = SyncMerge.merge(null, local, remote)
        val plan = SyncMerge.plan(merged, local, remote, setOf(SyncCategory.HISTORY))
        assertEquals(setOf("a", "b"), plan.toApply.favorites.keys)
        assertEquals(local.history, plan.toApply.history)
        assertEquals(remote.history, plan.toUpload.history)
        assertTrue(plan.newBase.history.isEmpty())
    }

    @Test
    fun `turning a category back on combines instead of deleting`() {
        // Base saved while History was off keeps it empty, so both sides' plays survive.
        val base = SyncSnapshot(history = emptyMap())
        val local = SyncSnapshot(history = mapOf("x@1" to 1L))
        val remote = SyncSnapshot(history = mapOf("y@2" to 2L))
        assertEquals(setOf("x@1", "y@2"), SyncMerge.merge(base, local, remote).history.keys)
    }
}
