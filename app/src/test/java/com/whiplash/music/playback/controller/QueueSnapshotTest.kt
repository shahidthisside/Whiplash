package com.whiplash.music.playback.controller

import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class QueueSnapshotTest {
    private val yt = PlayableItem.YoutubeTrack("v1", "Song", "Artist", "Album", "https://img/1.jpg", 200_000L)
    private val local = PlayableItem.LocalTrack("42", "Local", "Me", null, null, 180_000L, "content://media/external/audio/media/42")
    private val dl = PlayableItem.DownloadedTrack("v2", "Saved", "Artist 2", null, "file:///art.jpg", 150_000L, "file:///song.m4a")

    private val snapshot = QueueSnapshot(
        items = listOf(yt, local, dl),
        currentIndex = 1,
        positionMs = 61_500L,
        autoplayIds = setOf("v2"),
        shuffleEnabled = true,
        repeatMode = RepeatMode.ALL,
    )

    @Test fun roundTripsEveryKindOfSong() {
        assertEquals(snapshot, QueueSnapshot.decode(QueueSnapshot.encode(snapshot)))
    }

    @Test fun unreadableSongsAreDroppedAndTheIndexStaysInRange() {
        val json = QueueSnapshot.encode(snapshot.copy(currentIndex = 2))
            .replace("\"type\":\"local\"", "\"type\":\"unknown\"")
        val decoded = QueueSnapshot.decode(json)!!
        assertEquals(listOf(yt, dl), decoded.items)
        assertEquals(1, decoded.currentIndex) // was 2, clamped to the last song
    }

    @Test fun damagedOrForeignDataReadsAsNothing() {
        assertNull(QueueSnapshot.decode("{\"items\":["))
        assertNull(QueueSnapshot.decode("{\"v\":99,\"items\":[],\"index\":0}"))
        assertFalse(snapshot.copy(items = emptyList(), currentIndex = -1).isUsable)
    }

    @Test fun fileStoreSavesReadsAndClears() = runTest {
        val dir = Files.createTempDirectory("queue").toFile()
        try {
            val store = FileQueueStore(File(dir, "queue.json"))
            assertNull(store.read())
            store.write(snapshot)
            assertEquals(snapshot, store.read())
            // Emptying the queue removes the saved copy.
            store.write(null)
            assertNull(store.read())
            assertFalse(File(dir, "queue.json").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
