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

class SpeedDialSnapshotTest {

    private val items = listOf(
        PlayableItem.YoutubeTrack("abcdefghijk", "Song", "Artist", null, "https://i.ytimg.com/vi/abcdefghijk/hq720.jpg", 200_000L),
        PlayableItem.LocalTrack("42", "Local", "Me", "Album", null, 100_000L, "content://media/external/audio/media/42"),
        PlayableItem.DownloadedTrack("zyxwvutsrqp", "Saved", "Them", null, null, 150_000L, "file:///data/x.webm"),
    )

    @Test
    fun keepsEveryKindOfSongInOrder() = runTest {
        val file = File.createTempFile("speed_dial", ".json").apply { delete() }
        val store = SpeedDialSnapshot(file)
        assertTrue(store.read().isEmpty())
        store.write(items)
        assertEquals(items, store.read())
        store.clear()
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun aDamagedFileReadsAsEmpty() = runTest {
        val file = File.createTempFile("speed_dial", ".json").apply { writeText("{not json") }
        assertTrue(SpeedDialSnapshot(file).read().isEmpty())
        file.delete()
    }
}
