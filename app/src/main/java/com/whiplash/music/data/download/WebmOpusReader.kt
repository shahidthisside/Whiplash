// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.download

import java.io.File

/**
 * Reads the Opus track straight out of a WebM (Matroska) file: its OpusHead
 * (the track's CodecPrivate) and every audio packet, in order, exactly as
 * stored. Reading the container directly takes a few milliseconds per song;
 * going through MediaExtractor one packet at a time took ~5 s.
 *
 * Handles SimpleBlocks and BlockGroups, unknown-size Segment/Cluster
 * elements and Xiph/EBML/fixed lacing. Returns null for anything it doesn't
 * fully understand (the caller then falls back to MediaExtractor), so a file
 * is never half-read.
 */
internal object WebmOpusReader {

    class Result(val opusHead: ByteArray, val packets: List<ByteArray>)

    private const val ID_EBML = 0x1A45DFA3L
    private const val ID_SEGMENT = 0x18538067L
    private const val ID_TRACKS = 0x1654AE6BL
    private const val ID_TRACK_ENTRY = 0xAEL
    private const val ID_TRACK_NUMBER = 0xD7L
    private const val ID_CODEC_ID = 0x86L
    private const val ID_CODEC_PRIVATE = 0x63A2L
    private const val ID_CLUSTER = 0x1F43B675L
    private const val ID_SIMPLE_BLOCK = 0xA3L
    private const val ID_BLOCK_GROUP = 0xA0L
    private const val ID_BLOCK = 0xA1L

    /** Level-1 IDs that end an unknown-size Cluster. */
    private val LEVEL1 = setOf(ID_CLUSTER, 0x114D9B74L, 0x1549A966L, ID_TRACKS, 0x1C53BB6BL, 0x1941A469L, 0x1043A770L, 0x1254C367L)

    /** Files above this are left to the streaming MediaExtractor path, so memory stays bounded. */
    private const val MAX_DIRECT_BYTES = 96L * 1024 * 1024

    fun read(file: File): Result? {
        if (file.length() > MAX_DIRECT_BYTES) return null
        return runCatching { parse(file.readBytes()) }.getOrNull()
    }

    private class Reader(val d: ByteArray) {
        /** Element ID with its marker bits kept, and its byte length. */
        fun id(p: Int): Pair<Long, Int>? {
            if (p >= d.size) return null
            val first = d[p].toInt() and 0xFF
            val len = when {
                first and 0x80 != 0 -> 1
                first and 0x40 != 0 -> 2
                first and 0x20 != 0 -> 3
                first and 0x10 != 0 -> 4
                else -> return null
            }
            if (p + len > d.size) return null
            var v = 0L
            for (i in 0 until len) v = (v shl 8) or (d[p + i].toLong() and 0xFF)
            return v to len
        }

        /** Size vint: value (or -1 for "unknown size") and byte length. */
        fun size(p: Int): Pair<Long, Int>? {
            if (p >= d.size) return null
            val first = d[p].toInt() and 0xFF
            if (first == 0) return null
            var len = 1
            var mask = 0x80
            while (first and mask == 0) { mask = mask ushr 1; len++ }
            if (p + len > d.size) return null
            var v = (first and (mask - 1)).toLong()
            var allOnes = v == (mask - 1).toLong()
            for (i in 1 until len) {
                val b = d[p + i].toLong() and 0xFF
                if (b != 0xFFL) allOnes = false
                v = (v shl 8) or b
            }
            return (if (allOnes) -1L else v) to len
        }
    }

    private fun parse(d: ByteArray): Result? {
        val r = Reader(d)
        var p = 0
        // EBML header
        val (hid, hl) = r.id(p) ?: return null
        if (hid != ID_EBML) return null
        val (hs, hsl) = r.size(p + hl) ?: return null
        if (hs < 0) return null
        p += hl + hsl + hs.toInt()

        val (sid, sl) = r.id(p) ?: return null
        if (sid != ID_SEGMENT) return null
        val (ss, ssl) = r.size(p + sl) ?: return null
        val segStart = p + sl + ssl
        val segEnd = if (ss < 0) d.size else minOf(d.size.toLong(), segStart + ss).toInt()

        var opusTrack = -1L
        var head: ByteArray? = null
        val packets = ArrayList<ByteArray>(12_000)

        p = segStart
        while (p < segEnd) {
            val (id, il) = r.id(p) ?: return null
            val (sz, szl) = r.size(p + il) ?: return null
            val body = p + il + szl
            val end = if (sz < 0) segEnd else (body + sz).let { if (it > segEnd) return null else it.toInt() }
            when (id) {
                ID_TRACKS -> {
                    val (track, codecPrivate) = readOpusTrack(r, body, end) ?: return null
                    opusTrack = track
                    head = codecPrivate
                }
                ID_CLUSTER -> {
                    if (opusTrack < 0) return null
                    val clusterEnd = readCluster(r, body, end, sz < 0, opusTrack, packets) ?: return null
                    p = clusterEnd
                    continue
                }
            }
            if (sz < 0) return null // only clusters may have unknown size here
            p = end
        }
        val opusHead = head ?: return null
        if (opusHead.size < 19 || String(opusHead, 0, 8, Charsets.US_ASCII) != "OpusHead") return null
        if (packets.isEmpty()) return null
        return Result(opusHead, packets)
    }

    /** The Opus track's number and CodecPrivate (OpusHead). */
    private fun readOpusTrack(r: Reader, start: Int, end: Int): Pair<Long, ByteArray>? {
        var p = start
        while (p < end) {
            val (id, il) = r.id(p) ?: return null
            val (sz, szl) = r.size(p + il) ?: return null
            if (sz < 0) return null
            val body = p + il + szl
            val next = body + sz.toInt()
            if (next > end) return null
            if (id == ID_TRACK_ENTRY) {
                var number = -1L
                var codec: String? = null
                var priv: ByteArray? = null
                var q = body
                while (q < next) {
                    val (cid, cl) = r.id(q) ?: return null
                    val (cs, csl) = r.size(q + cl) ?: return null
                    if (cs < 0) return null
                    val cb = q + cl + csl
                    val cn = cb + cs.toInt()
                    if (cn > next) return null
                    when (cid) {
                        ID_TRACK_NUMBER -> { var v = 0L; for (i in cb until cn) v = (v shl 8) or (r.d[i].toLong() and 0xFF); number = v }
                        ID_CODEC_ID -> codec = String(r.d, cb, cn - cb, Charsets.US_ASCII).trimEnd('\u0000')
                        ID_CODEC_PRIVATE -> priv = r.d.copyOfRange(cb, cn)
                    }
                    q = cn
                }
                if (codec == "A_OPUS" && number > 0 && priv != null) return number to priv
            }
            p = next
        }
        return null
    }

    /** Appends the cluster's Opus packets; returns where parsing continues. */
    private fun readCluster(r: Reader, start: Int, end: Int, unknownSize: Boolean, track: Long, out: MutableList<ByteArray>): Int? {
        var p = start
        while (p < end) {
            val (id, il) = r.id(p) ?: return null
            if (unknownSize && id in LEVEL1) return p // next level-1 element starts
            val (sz, szl) = r.size(p + il) ?: return null
            if (sz < 0) return null
            val body = p + il + szl
            val next = body + sz.toInt()
            if (next > end) return null
            when (id) {
                ID_SIMPLE_BLOCK -> if (!readBlock(r, body, next, track, out)) return null
                ID_BLOCK_GROUP -> {
                    var q = body
                    while (q < next) {
                        val (bid, bl) = r.id(q) ?: return null
                        val (bs, bsl) = r.size(q + bl) ?: return null
                        if (bs < 0) return null
                        val bb = q + bl + bsl
                        val bn = bb + bs.toInt()
                        if (bn > next) return null
                        if (bid == ID_BLOCK && !readBlock(r, bb, bn, track, out)) return null
                        q = bn
                    }
                }
            }
            p = next
        }
        return end
    }

    /** One (Simple)Block: track vint, 16-bit timecode, flags, then the frame(s). */
    private fun readBlock(r: Reader, start: Int, end: Int, track: Long, out: MutableList<ByteArray>): Boolean {
        val (tn, tl) = r.size(start) ?: return false
        var p = start + tl + 3
        if (p > end) return false
        if (tn != track) return true // another track; skip
        val flags = r.d[p - 1].toInt() and 0xFF
        when ((flags ushr 1) and 3) {
            0 -> { if (p >= end) return false; out.add(r.d.copyOfRange(p, end)); return true }
            else -> {
                // Laced block: frame count, then sizes by lacing type.
                val lacing = (flags ushr 1) and 3
                val count = (r.d[p].toInt() and 0xFF) + 1
                p++
                val sizes = LongArray(count)
                when (lacing) {
                    1 -> for (i in 0 until count - 1) { // Xiph
                        var s = 0L
                        while (true) { if (p >= end) return false; val b = r.d[p++].toInt() and 0xFF; s += b; if (b < 255) break }
                        sizes[i] = s
                    }
                    3 -> { // EBML
                        val (first, fl) = r.size(p) ?: return false
                        if (first < 0) return false
                        sizes[0] = first; p += fl
                        for (i in 1 until count - 1) {
                            val (raw, rl) = r.size(p) ?: return false
                            if (raw < 0) return false
                            val bias = (1L shl (7 * rl - 1)) - 1
                            sizes[i] = sizes[i - 1] + (raw - bias); p += rl
                            if (sizes[i] < 0) return false
                        }
                    }
                    2 -> { // fixed
                        val total = end - p
                        if (total % count != 0) return false
                        for (i in 0 until count) sizes[i] = (total / count).toLong()
                    }
                }
                if (lacing != 2) sizes[count - 1] = (end - p) - sizes.take(count - 1).sum()
                for (s in sizes) {
                    if (s <= 0 || p + s > end) return false
                    out.add(r.d.copyOfRange(p, (p + s).toInt())); p += s.toInt()
                }
                return p == end
            }
        }
    }
}
