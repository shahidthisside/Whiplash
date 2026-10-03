// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.repository

import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class QuickPicksSnapshotTest {
    private fun track(id: String, album: String? = null, art: String? = null) =
        PlayableItem.YoutubeTrack(id = id, title = "Song $id", artist = "Artist $id", album = album, artworkUri = art, durationMs = 180_000L)

    @Test fun savesReadsAndClears() = runTest {
        val dir = Files.createTempDirectory("qp").toFile()
        try {
            val snapshot = QuickPicksSnapshot(File(dir, "quick_picks.json"))
            assertTrue(snapshot.read().isEmpty())
            val list = listOf(track("a", album = "Album", art = "https://img/a.jpg"), track("b"))
            snapshot.write(list)
            assertEquals(list, snapshot.read())
            // A newer list replaces the old one completely.
            snapshot.write(listOf(track("c")))
            assertEquals(listOf(track("c")), snapshot.read())
            snapshot.clear()
            assertTrue(snapshot.read().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun keepsBuildInfoAndReadsOldFiles() = runTest {
        val dir = Files.createTempDirectory("qp").toFile()
        try {
            val file = File(dir, "quick_picks.json")
            val snapshot = QuickPicksSnapshot(file)
            val meta = QuickPicksSnapshot.Meta(builtAtMs = 1_700_000_000_000L, topArtists = listOf("Pritam", "A.R. Rahman"))
            snapshot.write(listOf(track("a")), meta)
            assertEquals(QuickPicksSnapshot.Saved(listOf(track("a")), meta), snapshot.readSaved())
            // A list saved by an older version is still shown; it just has no build info.
            file.writeText(YoutubeTrackJson.encode(listOf(track("b"))))
            assertEquals(QuickPicksSnapshot.Saved(listOf(track("b")), null), snapshot.readSaved())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun damagedFileReadsAsEmpty() = runTest {
        val dir = Files.createTempDirectory("qp").toFile()
        try {
            val file = File(dir, "quick_picks.json").apply { writeText("[{\"id\":") }
            assertTrue(QuickPicksSnapshot(file).read().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
