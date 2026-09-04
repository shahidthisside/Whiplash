package com.whiplash.music.playback.cache
// Developed by Shahid Ansari — github.com/shahidthisside (-SA)

import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Wraps a network [DataSource] so every read reaches googlevideo as a run of
 * bounded byte-range requests instead of one open-ended request.
 *
 * Adapted from the same technique in BitChord's `ChunkedDataSource` (an
 * open-source YouTube Music client under GPLv3 — this is an independent,
 * from-scratch implementation of the general approach it describes, not a
 * copy of its code): a plain unbounded GET to a googlevideo URL is paced
 * down by the server to roughly playback speed, while the same bytes
 * requested via an explicit `Range` header arrive at full line rate. The
 * difference is large enough to be the actual reason a stall never
 * recovers into a comfortable buffer — there's no spare bandwidth to catch
 * up with on an unbounded request, since the server itself is throttling
 * to match real-time consumption.
 *
 * This sits *below* [androidx.media3.datasource.cache.CacheDataSource] in
 * the data source chain (see [AudioCacheManager.cachingDataSourceFactory]),
 * so it only ever wraps genuine network reads — a cache hit never reaches
 * this class at all, and every chunk fetched here is written through to
 * disk exactly as a single unbounded read would have been. Transparent to
 * everything above it: from the cache's perspective this is one continuous
 * stream of the length it asked for.
 */
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkBytes: Long = DEFAULT_CHUNK_BYTES,
) : DataSource {

    private var dataSpec: DataSpec? = null
    private var bytesRemainingInResource = 0L
    private var bytesRemainingInChunk = 0L
    private var nextChunkPosition = 0L

    /**
     * The real total length of the resource, learned from the first
     * sub-chunk's upstream response once the original request's own
     * [DataSpec.length] was unknown ([C.LENGTH_UNSET]). [open] must return
     * this (per [DataSource.open]'s own documented contract: "For unbounded
     * requests... this value is the resolved length of the request") rather
     * than blindly echoing back the original, still-unresolved length -
     * otherwise [androidx.media3.datasource.cache.CacheDataSource] never
     * learns the resource's total size and silently skips caching the read
     * entirely (confirmed via Media3's own CacheDataSource behavior: it
     * cannot write a cache entry it doesn't know the eventual length for).
     * This was the real, reported root cause of "no songs are getting
     * cached, and a fully-played song still re-downloads on replay" - the
     * disk cache itself was never being written to in the first place.
     */
    private var resolvedResourceLength = C.LENGTH_UNSET.toLong()

    /** True once a request already asked for a bounded amount no larger than one chunk — nothing to improve on, forward it untouched. */
    private var passthrough = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        val requestedLength = dataSpec.length
        if (requestedLength != C.LENGTH_UNSET.toLong() && requestedLength <= chunkBytes) {
            // Already a small, bounded request (e.g. read-ahead priming a
            // handful of bytes, or the upstream cache asking for exactly
            // what it's missing) — chunking it further would only add
            // request overhead for no throughput benefit.
            passthrough = true
            return upstream.open(dataSpec)
        }

        passthrough = false
        nextChunkPosition = dataSpec.position
        bytesRemainingInResource = requestedLength
        bytesRemainingInChunk = 0L
        resolvedResourceLength = requestedLength
        return openNextChunk()
    }

    /** Opens the next bounded sub-request on [upstream], returning the same total-length contract [open] must honor. */
    private fun openNextChunk(): Long {
        val spec = dataSpec ?: throw IllegalStateException("open() must be called before reading")
        val wasUnresolved = bytesRemainingInResource == C.LENGTH_UNSET.toLong()
        val thisChunkLength = if (wasUnresolved) {
            chunkBytes
        } else {
            minOf(chunkBytes, bytesRemainingInResource)
        }
        val chunkSpec = spec.buildUpon()
            .setPosition(nextChunkPosition)
            .setLength(thisChunkLength)
            .build()
        val opened = upstream.open(chunkSpec)
        // `opened` is this SUB-REQUEST's own resolved length (bounded to
        // thisChunkLength per DataSource.open()'s contract for a request
        // that already specified a length) - never the whole resource's
        // remaining size. Must not be assigned into bytesRemainingInResource
        // directly (that field means "bytes remaining across the WHOLE
        // resource", a completely different quantity) - doing so was a real
        // bug that surfaced as a stalled-playback EOFException exactly at
        // the first chunk boundary, since bytesRemainingInResource was then
        // immediately decremented down to ~0 by read()'s own per-byte
        // bookkeeping before the chunk had actually finished, causing the
        // very next openNextChunk() call to request a 0-byte chunk.
        bytesRemainingInChunk = if (opened != C.LENGTH_UNSET.toLong()) opened else thisChunkLength
        if (wasUnresolved) {
            // This was the resource's first-ever sub-open with an unknown
            // total length. DefaultHttpDataSource resolves the TRUE
            // remaining-from-position byte count via the real upstream
            // response (e.g. a googlevideo Content-Range header) even for
            // a bounded chunkSpec request - exposed via getResponseHeaders()
            // Content-Range, not via this open() call's own return value
            // (which is correctly just the bounded chunk length per
            // contract). Parse it directly so bytesRemainingInResource
            // reflects the resource's real total remaining size, distinct
            // from bytesRemainingInChunk (this chunk's own remaining size).
            val totalLength = parseContentRangeTotalLength(upstream.responseHeaders)
            if (totalLength != null) {
                bytesRemainingInResource = totalLength - nextChunkPosition
                resolvedResourceLength = totalLength
            }
            // If the server didn't send a parseable Content-Range (e.g. a
            // plain 200 response because the resource turned out to be
            // smaller than one chunk), leave bytesRemainingInResource as
            // C.LENGTH_UNSET - the existing unresolved-length code path
            // (identical to this function's behavior before this fix)
            // continues to work correctly, just without a cache-writable
            // resolved length for this particular read.
        }
        return resolvedResourceLength
    }

    /**
     * Extracts the resource's total byte length from a `Content-Range:
     * bytes start-end/total` response header, or null if absent/unparseable
     * (e.g. the server responded 200 instead of 206, meaning the resource
     * was smaller than the requested range and got returned in full).
     */
    private fun parseContentRangeTotalLength(headers: Map<String, List<String>>): Long? {
        val contentRange = headers.entries
            .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value?.firstOrNull()
            ?: return null
        val totalPart = contentRange.substringAfterLast('/', missingDelimiterValue = "")
        return totalPart.toLongOrNull()?.takeIf { it > 0 }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(buffer, offset, length)

        if (bytesRemainingInChunk <= 0L) {
            if (bytesRemainingInResource == 0L) return C.RESULT_END_OF_INPUT
            // Current chunk is exhausted but the overall resource isn't —
            // close this sub-request and open the next bounded range
            // immediately, transparently to the caller.
            upstream.close()
            openNextChunk()
            if (bytesRemainingInChunk <= 0L) return C.RESULT_END_OF_INPUT
        }

        val toRead = if (length.toLong() > bytesRemainingInChunk) bytesRemainingInChunk.toInt() else length
        val read = upstream.read(buffer, offset, toRead)
        if (read == C.RESULT_END_OF_INPUT) {
            // Server closed the sub-request early (shorter response than
            // asked for, e.g. reaching genuine end-of-resource inside what
            // was expected to be a full-size chunk) — treat the whole
            // chunk as exhausted rather than looping on a request that
            // will keep returning nothing.
            bytesRemainingInChunk = 0L
            return if (bytesRemainingInResource == 0L) C.RESULT_END_OF_INPUT else read
        }
        bytesRemainingInChunk -= read
        if (bytesRemainingInResource != C.LENGTH_UNSET.toLong()) {
            bytesRemainingInResource -= read
        }
        nextChunkPosition += read
        return read
    }

    override fun getUri() = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        dataSpec = null
        upstream.close()
    }

    companion object {
        /** 2MB — large enough to keep request overhead negligible, small enough that a stall recovers within a couple of chunks. */
        const val DEFAULT_CHUNK_BYTES: Long = 2L * 1024 * 1024
    }
}

/** [DataSource.Factory] that wraps whatever [upstreamFactory] produces in a [ChunkedDataSource]. */
class ChunkedDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val chunkBytes: Long = ChunkedDataSource.DEFAULT_CHUNK_BYTES,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = ChunkedDataSource(upstreamFactory.createDataSource(), chunkBytes)
}
