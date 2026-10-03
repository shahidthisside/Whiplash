// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.recommend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LearnedStoreTest {
    @Test fun saveReplaceClear() {
        val dir = Files.createTempDirectory("learned").toFile()
        try {
            val store = FileLearnedStore(File(dir, "radio_learned.json"))
            assertNull(store.load())
            assertEquals(0, store.examples())
            store.save("""{"ranker":{"n":12}}""")
            assertEquals(12, store.examples())
            val gen = store.generation
            store.replace("""{"ranker":{"n":40}}""")
            assertEquals(40, store.examples())
            assertEquals(gen + 1, store.generation) // the radio reloads it
            assertEquals(0, store.examples("not json"))
            store.clear()
            assertNull(store.load())
            assertEquals(gen + 2, store.generation)
        } finally {
            dir.deleteRecursively()
        }
    }
}
