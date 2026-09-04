package com.whiplash.music.playback.cache

import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Instrumented (device-run, so real android.net.Uri is available for
 * [DataSpec]) regression tests for the real, reported caching bug: no songs were ever
 * cached, Settings always showed 0 bytes, and a fully-played track still
 * re-downloaded on replay.
 *
 * Root cause was [ChunkedDataSource.open] violating [DataSource.open]'s
 * documented return contract — "For unbounded requests (i.e. requests where
 * length equals LENGTH_UNSET) this value is the resolved length of the
 * request" — by blindly echoing back the caller's original (still-unresolved)
 * length instead. `CacheDataSource` sitting above it therefore never learned
 * the resource's total size and silently skipped caching every read.
 */
@RunWith(AndroidJUnit4::class)
class ChunkedDataSourceInstrumentedTest {

    /**
     * Fake upstream that answers bounded range requests the way a real HTTP
     * server (and googlevideo specifically, verified on-device) does: a 206
     * response whose `Content-Range: bytes start-end/total` reveals the
     * resource's true total length even though the response body itself is
     * only the bounded chunk.
     */
    private class FakeRangeUpstream(private val totalLength: Long) : DataSource {
        var openCount = 0
            private set
        private var position = 0L
        private var remainingInResponse = 0L

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            openCount++
            position = dataSpec.position
            val requested = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                totalLength - position
            } else {
                minOf(dataSpec.length, totalLength - position)
            }
            remainingInResponse = requested
            return requested
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remainingInResponse <= 0L) return C.RESULT_END_OF_INPUT
            val toRead = minOf(length.toLong(), remainingInResponse).toInt()
            remainingInResponse -= toRead
            position += toRead
            return toRead
        }

        override fun getUri() = null

        override fun getResponseHeaders(): Map<String, List<String>> {
            val end = position + remainingInResponse - 1
            return mapOf("Content-Range" to listOf("bytes $position-$end/$totalLength"))
        }

        override fun close() = Unit
    }

    @Test
    fun open_resolvesRealTotalLength_forUnboundedRequest() {
        val total = 3_404_380L
        val source = ChunkedDataSource(FakeRangeUpstream(total), chunkBytes = 2L * 1024 * 1024)

        val resolved = source.open(DataSpec.Builder().setUri("https://example.test/a").build())

        // The whole point of the fix: an unbounded request must resolve to the
        // resource's REAL total length, not C.LENGTH_UNSET, so CacheDataSource
        // above can actually write a cache entry.
        assertEquals(total, resolved)
    }

    @Test
    fun open_echoesBackRequestedLength_forBoundedRequest() {
        val total = 3_404_380L
        val requested = 500_000L
        val source = ChunkedDataSource(FakeRangeUpstream(total), chunkBytes = 2L * 1024 * 1024)

        val resolved = source.open(
            DataSpec.Builder().setUri("https://example.test/a").setLength(requested).build(),
        )

        // Per the same contract: "For all other requests, the value returned
        // will be equal to the request's length."
        assertEquals(requested, resolved)
    }

    @Test
    fun read_deliversWholeResource_acrossMultipleChunks() {
        val total = 5_000_000L
        val chunk = 2L * 1024 * 1024
        val upstream = FakeRangeUpstream(total)
        val source = ChunkedDataSource(upstream, chunkBytes = chunk)
        source.open(DataSpec.Builder().setUri("https://example.test/a").build())

        val buffer = ByteArray(64 * 1024)
        var totalRead = 0L
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            totalRead += read
        }

        // Every byte must be delivered. The bug this guards against stalled
        // playback with an EOFException at the first chunk boundary because
        // per-chunk and per-resource remaining-byte bookkeeping had been
        // conflated, cutting the stream short at exactly one chunk.
        assertEquals(total, totalRead)
        // 5MB over 2MB chunks = 3 bounded sub-requests, proving chunking
        // (the throttling-avoidance behavior) is still intact and wasn't
        // silently degraded into one big unbounded request by the fix.
        assertEquals(3, upstream.openCount)
    }

    @Test
    fun read_deliversWholeResource_smallerThanOneChunk() {
        val total = 100_000L
        val upstream = FakeRangeUpstream(total)
        val source = ChunkedDataSource(upstream, chunkBytes = 2L * 1024 * 1024)
        source.open(DataSpec.Builder().setUri("https://example.test/a").build())

        val buffer = ByteArray(64 * 1024)
        var totalRead = 0L
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            totalRead += read
        }

        assertEquals(total, totalRead)
        assertEquals(1, upstream.openCount)
    }
}
