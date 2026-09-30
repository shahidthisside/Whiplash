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
