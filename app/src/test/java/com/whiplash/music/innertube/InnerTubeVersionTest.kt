// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.innertube

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class InnerTubeVersionTest {
    private class MemoryStore(var value: StoredVersion? = null) : VersionStore {
        override fun read() = value
        override fun write(value: StoredVersion) { this.value = value }
    }

    private val pageFetches = AtomicInteger()

    private fun client(page: String? = """..."INNERTUBE_CLIENT_VERSION":"1.20260901.03.00"..."""): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            pageFetches.incrementAndGet()
            Thread.sleep(50) // long enough for the parallel callers to pile up
            if (page == null) throw IOException("offline")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(page.toResponseBody()).build()
        }).build()

    private val day = 24 * 3_600_000L

    @Test fun parallelFirstCallsReadThePageOnce() = runTest {
        val store = MemoryStore()
        val it = InnerTubeClient(client(), store, now = { 10 * day })
        val versions = (1..8).map { _ -> async { it.version() } }.awaitAll()
        assertEquals(setOf("1.20260901.03.00"), versions.toSet())
        assertEquals(1, pageFetches.get())
        assertEquals(StoredVersion("1.20260901.03.00", 10 * day), store.value)
    }

    @Test fun rememberedVersionNeedsNoPage() = runTest {
        val store = MemoryStore(StoredVersion("1.20260915.01.00", 10 * day - 3_600_000L))
        val it = InnerTubeClient(client(), store, now = { 10 * day })
        assertEquals("1.20260915.01.00", it.version())
        assertEquals(0, pageFetches.get())
    }

    @Test fun tooOldVersionIsReadAgain() = runTest {
        val store = MemoryStore(StoredVersion("1.20250101.01.00", 0L))
        val it = InnerTubeClient(client(), store, now = { 40 * day })
        assertEquals("1.20260901.03.00", it.version())
        assertEquals(1, pageFetches.get())
    }

    @Test fun unreadablePageFallsBackToTheLastKnownVersion() = runTest {
        val stale = MemoryStore(StoredVersion("1.20250101.01.00", 0L))
        assertEquals("1.20250101.01.00", InnerTubeClient(client(page = null), stale, now = { 40 * day }).version())
        assertEquals("1.20250122.01.00", InnerTubeClient(client(page = null), MemoryStore(), now = { 40 * day }).version())
    }
}
