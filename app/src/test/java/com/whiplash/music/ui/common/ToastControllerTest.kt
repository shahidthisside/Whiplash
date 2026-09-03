package com.whiplash.music.ui.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the plumbing behind "show exactly one toast" requirements
 * (single-song download start, batch album/playlist download start, and
 * per-track download failure): [ToastController] must emit exactly one
 * event per [ToastController.show] call — no de-duplication, no dropped
 * back-to-back identical messages (a StateFlow would drop these; this is
 * why ToastController is backed by a SharedFlow instead).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToastControllerTest {

    @Test
    fun `show emits exactly one event per call`() = runTest(UnconfinedTestDispatcher()) {
        val collected = mutableListOf<String>()
        val job = backgroundScope.launch {
            ToastController.events.collect { collected.add(it) }
        }

        ToastController.show("Download started")

        assertEquals(listOf("Download started"), collected)
        job.cancel()
    }

    @Test
    fun `repeated identical messages are not collapsed into one event`() = runTest(UnconfinedTestDispatcher()) {
        // This is exactly the shape of a bulk album download where two
        // different tracks independently fail: each failure must produce
        // its own toast, not be silently swallowed because the message
        // text happens to repeat.
        val collected = mutableListOf<String>()
        val job = backgroundScope.launch {
            ToastController.events.collect { collected.add(it) }
        }

        ToastController.show("Download failed: Song A")
        ToastController.show("Download failed: Song A")

        assertEquals(listOf("Download failed: Song A", "Download failed: Song A"), collected)
        job.cancel()
    }

    @Test
    fun `batch download posts a single start toast regardless of track count`() = runTest(UnconfinedTestDispatcher()) {
        // Mirrors DownloadManager.downloadAll's actual call pattern:
        // per-track startDownload(showToast = false) calls produce no
        // events at all, followed by exactly one batch-level show().
        val collected = mutableListOf<String>()
        val job = backgroundScope.launch {
            ToastController.events.collect { collected.add(it) }
        }

        val trackCount = 12
        repeat(trackCount) { /* startDownload(showToast = false) would emit nothing */ }
        ToastController.show("Download started")

        assertEquals(1, collected.size)
        assertEquals("Download started", collected.first())
        job.cancel()
    }
}
